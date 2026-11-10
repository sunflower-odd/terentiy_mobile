package ru.terentiy.mobile.ui.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.terentiy.mobile.core.error.AppError
import ru.terentiy.mobile.core.error.messageRes
import ru.terentiy.mobile.data.local.AppSettings
import ru.terentiy.mobile.data.repository.AgentsFeed
import ru.terentiy.mobile.data.repository.EventFeed
import ru.terentiy.mobile.domain.Agent
import ru.terentiy.mobile.domain.FallEvent
import ru.terentiy.mobile.domain.WatchState
import ru.terentiy.mobile.domain.WatchStatus

/** Ошибка, после которой о состоянии наблюдения ничего не известно: нет связи или сервер сломан. */
private fun AppError?.isServerUnreachable(): Boolean =
    this == AppError.Network || (this is AppError.Server && status >= 500)

data class HomeUiState(
    val userName: String = "",
    val agents: List<Agent> = emptyList(),
    val watchState: WatchState = WatchState.NO_AGENT,
    val unseenCount: Int = 0,
    val alert: FallEvent? = null,
    val firstLoad: Boolean = true,
    @param:StringRes val errorRes: Int? = null,
)

/**
 * Главный экран.
 *
 * Данные берутся из двух общих лент, а не запрашиваются здесь: «Главная» и
 * «История» показывают одни и те же события, и два независимых опроса означали бы
 * двойной трафик на ровном месте.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val agentsFeed: AgentsFeed,
    private val eventFeed: EventFeed,
    settings: AppSettings,
) : ViewModel() {

    /** Закрытая крестиком тревога. Живёт до конца процесса — постоянную память несут отметки просмотра. */
    private val dismissed = MutableStateFlow<String?>(null)

    val state: StateFlow<HomeUiState> = combine(
        agentsFeed.state,
        eventFeed.state,
        settings.userName,
        dismissed,
    ) { agents, events, userName, dismissedId ->
        // Сервер недоступен — это состояние наблюдения, а не отдельная ошибка:
        // его показывает сам статус, баннер его бы только повторял.
        val serverReachable = !agents.error.isServerUnreachable()
        HomeUiState(
            userName = userName,
            agents = agents.agents,
            watchState = WatchStatus.of(agents.agents, serverReachable),
            unseenCount = events.unseenCount,
            alert = events.latestUnseen?.takeIf { it.id != dismissedId },
            firstLoad = agents.firstLoad,
            errorRes = agents.error
                ?.takeIf { serverReachable && agents.agents.isEmpty() }
                ?.messageRes(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Опрос идёт, только пока экран на переднем плане. */
    suspend fun keepFresh() {
        coroutineScope {
            launch { agentsFeed.keepFresh() }
            launch { eventFeed.keepFresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            launch { agentsFeed.refresh() }
            launch { eventFeed.refresh() }
        }
    }

    fun dismissAlert() {
        dismissed.value = state.value.alert?.id
    }
}
