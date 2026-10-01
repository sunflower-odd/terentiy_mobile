package ru.terentiy.mobile.data.repository

import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.terentiy.mobile.core.error.AppError
import ru.terentiy.mobile.core.poll.Backoff
import ru.terentiy.mobile.core.poll.PollIntervals
import ru.terentiy.mobile.data.local.SeenEventsStore
import ru.terentiy.mobile.data.local.SeenState
import ru.terentiy.mobile.domain.Agent
import ru.terentiy.mobile.domain.FallEvent
import timber.log.Timber

/**
 * Общая часть двух лент.
 *
 * Опрос устроен через [shareIn] с [SharingStarted.WhileSubscribed]: сколько бы
 * экранов ни подписалось, наверху работает **один** цикл, и он останавливается
 * через пару секунд после того, как последний экран ушёл с переднего плана.
 * Раньше таймер жил в каждой ViewModel, и «Главная» с «Историей» опрашивали
 * сервер независимо, дублируя запросы.
 */
abstract class PollingFeed(interval: Duration) {

    protected val scope = CoroutineScope(SupervisorJob())
    private val backoff = Backoff(interval)
    private val refreshLock = Mutex()

    private val ticker: SharedFlow<Unit> = flow {
        while (true) {
            runCatching { refresh() }
            emit(Unit)
            delay(backoff.current.toMillis())
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 2_000), replay = 0)

    /**
     * Экран вызывает, пока он на переднем плане.
     *
     * Сбор из общего потока не завершается сам: он живёт, пока жива подписка.
     * Тело блочное, а не выражением, именно поэтому — у выражения вывелся бы
     * тип `Nothing`.
     */
    suspend fun keepFresh() {
        ticker.collect { }
    }

    /** Однократное обновление: pull-to-refresh, возврат на экран, кнопка «Повторить». */
    suspend fun refresh() {
        // Одновременные обновления не имеют смысла: второе всё равно получит то
        // же состояние, а лишний запрос уйдёт в сеть.
        if (refreshLock.isLocked) return
        refreshLock.withLock {
            try {
                load()
                backoff.success()
            } catch (error: Throwable) {
                // Репозитории отдают только AppError, но полагаться на это в
                // цикле опроса нельзя: неожиданное исключение не должно
                // останавливать обновление навсегда.
                backoff.failure()
                onError(if (error is AppError) error else AppError.Unexpected(error))
            }
        }
    }

    protected abstract suspend fun load()

    protected abstract fun onError(error: AppError)
}

/** Состояние ленты событий. */
data class EventFeedState(
    val events: List<FallEvent> = emptyList(),
    val seen: SeenState = SeenState.Empty,
    val firstLoad: Boolean = true,
    val error: AppError? = null,
    val loadedAt: Instant? = null,
) {
    val unseen: List<FallEvent> get() = events.filterNot(seen::isSeen)

    val unseenCount: Int get() = unseen.size

    val latestUnseen: FallEvent? get() = unseen.firstOrNull()

    /** Связь потеряна, но показывать есть что: экран остаётся с последними данными. */
    val offline: Boolean get() = error == AppError.Network && events.isNotEmpty()
}

/**
 * Лента событий — единственный источник событий в приложении.
 *
 * Её читают баннер тревоги на главной, список в истории и карточка события.
 * Отдельного маршрута `GET /events/{id}` не существует, поэтому карточка берёт
 * событие из этой же ленты, а не ходит за ним в сеть.
 */
@Singleton
class EventFeed @Inject constructor(
    private val events: EventsRepository,
    private val seenStore: SeenEventsStore,
) : PollingFeed(PollIntervals.Events) {

    private val _state = MutableStateFlow(EventFeedState())
    val state: StateFlow<EventFeedState> = _state.asStateFlow()

    override suspend fun load() {
        val fresh = events.events()
        // Знак ставится по самому свежему событию и только при первом чтении:
        // история, существовавшая до активации, не должна выглядеть непросмотренной.
        seenStore.initialiseWatermark(fresh.firstNotNullOfOrNull { it.capturedAt })
        seenStore.prune(fresh.map { it.id }.toSet())
        // Отметки читаются здесь, а не собираются фоновым сбором: состояние
        // получается предсказуемым, а мгновенное обновление после открытия
        // события обеспечивает markSeen.
        val seen = seenStore.state.first()
        _state.update {
            it.copy(events = fresh, seen = seen, firstLoad = false, error = null, loadedAt = Instant.now())
        }
    }

    override fun onError(error: AppError) {
        Timber.w("Не удалось обновить события | error=%s", error::class.simpleName)
        _state.update { it.copy(firstLoad = false, error = error) }
    }

    suspend fun markSeen(id: String) {
        seenStore.markSeen(id)
        // Список обновляется сразу: ждать следующего опроса, чтобы точка у
        // открытого события погасла, было бы странно.
        _state.update { it.copy(seen = it.seen.copy(ids = it.seen.ids + id)) }
    }

    /** Событие из уже загруженной ленты. `null`, если лента ещё не читалась. */
    fun cached(id: String): FallEvent? = _state.value.events.firstOrNull { it.id == id }
}

/** Состояние ленты агентов. */
data class AgentsFeedState(
    val agents: List<Agent> = emptyList(),
    val firstLoad: Boolean = true,
    val error: AppError? = null,
    val loadedAt: Instant? = null,
) {
    val offline: Boolean get() = error == AppError.Network && agents.isNotEmpty()
}

/** Лента агентов: состояние контура наблюдения на главном экране. */
@Singleton
class AgentsFeed @Inject constructor(
    private val agents: AgentsRepository,
) : PollingFeed(PollIntervals.Agents) {

    private val _state = MutableStateFlow(AgentsFeedState())
    val state: StateFlow<AgentsFeedState> = _state.asStateFlow()

    override suspend fun load() {
        val fresh = agents.agents()
        _state.update {
            it.copy(agents = fresh, firstLoad = false, error = null, loadedAt = Instant.now())
        }
    }

    override fun onError(error: AppError) {
        Timber.w("Не удалось обновить агентов | error=%s", error::class.simpleName)
        // Последние известные данные не стираются: пустой экран хуже, чем
        // честный баннер «нет связи» поверх того, что было.
        _state.update { it.copy(firstLoad = false, error = error) }
    }
}
