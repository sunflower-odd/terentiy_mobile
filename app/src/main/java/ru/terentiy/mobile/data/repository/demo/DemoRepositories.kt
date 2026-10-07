package ru.terentiy.mobile.data.repository.demo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.terentiy.mobile.R
import ru.terentiy.mobile.core.error.AppError
import ru.terentiy.mobile.data.local.AppSettings
import ru.terentiy.mobile.data.local.SessionHolder
import ru.terentiy.mobile.data.repository.AgentsRepository
import ru.terentiy.mobile.data.repository.AuthRepository
import ru.terentiy.mobile.data.repository.EventsRepository
import ru.terentiy.mobile.domain.Agent
import ru.terentiy.mobile.domain.EventStatus
import ru.terentiy.mobile.domain.FallAnalysis
import ru.terentiy.mobile.domain.FallEvent
import ru.terentiy.mobile.domain.MediaPayload
import ru.terentiy.mobile.domain.Session
import ru.terentiy.mobile.domain.SessionState
import ru.terentiy.mobile.domain.Tri

/**
 * Сценарий демонстрации.
 *
 * Состояние агента продвигается по времени от первого обращения, а не по таймеру:
 * приложение может пролежать свёрнутым, и сценарий должен соответствовать
 * настоящим часам, а не количеству чтений.
 *
 * Шкала подобрана так, чтобы за минуту показать **всю** матрицу состояний
 * контура — прежний демо-сценарий этого не умел, у него была одна пара
 * «камера подключена / агент на связи»:
 *
 * | Время | Что видно |
 * |---|---|
 * | 0–3 с | агент ни разу не отчитался: «состояние неизвестно» |
 * | 3–23 с | наблюдение работает |
 * | 23 с | появляется событие падения с кадром |
 * | 35–45 с | очередь кадров растёт, кадры отстают |
 * | 45–52 с | камера недоступна |
 * | 52–58 с | агент не отвечает |
 * | далее | цикл с отметки 3 с |
 *
 * Промежуточные состояния «включаем» и «выключаем» не изображаются: они были
 * состояниями асинхронной доставки команды, а команд в реализованном контракте
 * нет. Демо показывает только то, что существует на самом деле.
 */
@Singleton
class DemoScenario @Inject constructor() {

    private val mutex = Mutex()

    private var startedAt: Instant? = null
    private var pin: String? = null
    private var generatedEventId: String? = null

    /** PIN, заданный при демо-активации: без него нельзя показать ошибку входа. */
    suspend fun rememberPin(value: String) = mutex.withLock {
        pin = value
        startedAt = startedAt ?: Instant.now()
    }

    suspend fun checkPin(value: String): Boolean = mutex.withLock { pin == null || pin == value }

    suspend fun reset() = mutex.withLock {
        startedAt = null
        pin = null
        generatedEventId = null
    }

    suspend fun agent(): Agent = mutex.withLock {
        val elapsed = elapsedSeconds()
        val now = Instant.now()

        if (elapsed < UNKNOWN_UNTIL) {
            return@withLock template.copy(
                online = false,
                agentOnline = Tri.UNKNOWN,
                cameraConnected = Tri.UNKNOWN,
                streamActive = Tri.UNKNOWN,
                queueSize = null,
                reportedAt = null,
                lastFrameAt = null,
            )
        }

        when (val phase = (elapsed - UNKNOWN_UNTIL) % CYCLE) {
            in 0 until FRAMES_DELAYED_AT -> healthy(now)

            in FRAMES_DELAYED_AT until CAMERA_LOST_AT -> healthy(now).copy(
                queueSize = 24,
                lastFrameAt = now.minusSeconds(90),
            )

            in CAMERA_LOST_AT until AGENT_LOST_AT -> healthy(now).copy(
                cameraConnected = Tri.NO,
                streamActive = Tri.NO,
                lastFrameAt = now.minusSeconds(phase - CAMERA_LOST_AT + 5),
            )

            else -> healthy(now).copy(
                online = false,
                reportedAt = now.minusSeconds(60),
                lastFrameAt = now.minusSeconds(62),
            )
        }
    }

    suspend fun events(): List<FallEvent> = mutex.withLock {
        val started = startedAt ?: return@withLock historical()
        val generated = if (elapsedSeconds() >= EVENT_AT) {
            val id = generatedEventId ?: DEMO_EVENT_ID.also { generatedEventId = it }
            listOf(
                event(
                    id = id,
                    occurredAt = started.plusSeconds(EVENT_AT),
                    confirmationVotes = 2,
                )
            )
        } else {
            emptyList()
        }
        generated + historical()
    }

    private fun healthy(now: Instant): Agent = template.copy(
        online = true,
        agentOnline = Tri.YES,
        cameraConnected = Tri.YES,
        streamActive = Tri.YES,
        queueSize = 0,
        reportedAt = now,
        lastFrameAt = now.minusSeconds(2),
    )

    private fun historical(): List<FallEvent> = listOf(
        event(
            id = "demo-event-yesterday",
            occurredAt = Instant.now().minus(Duration.ofHours(19)),
            confirmationVotes = 2,
        ),
        event(
            id = "demo-event-earlier",
            occurredAt = Instant.now().minus(Duration.ofDays(2)).plus(Duration.ofHours(3)),
            confirmationVotes = 1,
        ),
    )

    private fun event(id: String, occurredAt: Instant, confirmationVotes: Int) = FallEvent(
        id = id,
        agentId = AGENT_ID,
        frameId = "$id-frame",
        frameMediaId = DEMO_FRAME_MEDIA_ID,
        clipMediaId = DEMO_CLIP_MEDIA_ID,
        capturedAt = occurredAt,
        createdAt = occurredAt.plusSeconds(18),
        status = EventStatus.CONFIRMED,
        analysis = FallAnalysis(
            detected = true,
            trackId = 3,
            candidateRules = listOf("aspect_ratio=1.41", "body_angle=72deg"),
            confirmationRules = listOf("angular_speed=118deg/s", "torso_drop=0.42"),
            candidateVotes = 2,
            candidateVotesRequired = 2,
            confirmationVotes = confirmationVotes,
            confirmationVotesRequired = 1,
            processingTimeMs = 84.219,
        ),
    )

    private fun elapsedSeconds(): Long {
        val started = startedAt ?: Instant.now().also { startedAt = it }
        return Duration.between(started, Instant.now()).seconds.coerceAtLeast(0)
    }

    private val template = Agent(
        id = AGENT_ID,
        householdId = HOUSEHOLD_ID,
        name = AGENT_NAME,
        cameraId = "demo-camera-1",
        softwareVersion = "demo",
        createdAt = Instant.now().minus(Duration.ofDays(11)),
        online = false,
        agentOnline = Tri.UNKNOWN,
        cameraConnected = Tri.UNKNOWN,
        streamActive = Tri.UNKNOWN,
        queueSize = null,
        reportedAt = null,
        lastFrameAt = null,
    )

    companion object {
        const val AGENT_ID = "demo-agent-0001"
        const val AGENT_NAME = "Гостиная"
        const val HOUSEHOLD_ID = "demo-household"
        const val DEMO_FRAME_MEDIA_ID = "demo-frame"
        const val DEMO_CLIP_MEDIA_ID = "demo-clip"
        const val DEMO_EVENT_ID = "demo-event-fresh"

        private const val UNKNOWN_UNTIL = 3L
        private const val EVENT_AT = 23L
        private const val FRAMES_DELAYED_AT = 32L
        private const val CAMERA_LOST_AT = 42L
        private const val AGENT_LOST_AT = 49L
        private const val CYCLE = 55L

        /** Задержка, изображающая сеть: без неё индикаторы загрузки не увидеть. */
        const val NETWORK_FEEL_MS = 420L
    }
}

@Singleton
class DemoAuthRepository @Inject constructor(
    private val settings: AppSettings,
    private val sessions: SessionHolder,
    private val scenario: DemoScenario,
) : AuthRepository {

    override val sessionState: Flow<SessionState> = combine(
        settings.isActivated,
        settings.userName,
        settings.householdId,
        sessions.session,
    ) { activated, userName, householdId, session ->
        when {
            !activated -> SessionState.NotActivated
            session == null -> SessionState.NeedsPin
            else -> SessionState.Active(userName, householdId)
        }
    }

    override val serverUrl: Flow<String> = settings.serverUrl

    override suspend fun restoreServerUrl() = Unit

    /** В демо адрес не проверяется: сервера нет, а отказывать в подключении незачем. */
    override suspend fun checkServer(url: String) {
        delay(DemoScenario.NETWORK_FEEL_MS)
    }

    override suspend fun activate(
        serverUrl: String,
        installationCode: String,
        userName: String,
        pin: String,
    ) {
        delay(DemoScenario.NETWORK_FEEL_MS)
        settings.saveServerUrl(serverUrl.trim())
        scenario.rememberPin(pin)
        sessions.save(Session(accessToken = DEMO_TOKEN, householdId = DemoScenario.HOUSEHOLD_ID))
        settings.saveActivation(userName = userName.trim(), householdId = DemoScenario.HOUSEHOLD_ID)
    }

    override suspend fun login(pin: String) {
        delay(DemoScenario.NETWORK_FEEL_MS)
        if (!scenario.checkPin(pin)) throw AppError.InvalidPin
        sessions.save(Session(accessToken = DEMO_TOKEN, householdId = DemoScenario.HOUSEHOLD_ID))
    }

    override suspend fun logout() {
        sessions.clear()
    }

    override suspend fun forgetActivation() {
        settings.clearActivation()
        sessions.clear()
        scenario.reset()
    }

    private companion object {
        const val DEMO_TOKEN = "demo-token"
    }
}

@Singleton
class DemoAgentsRepository @Inject constructor(
    private val scenario: DemoScenario,
) : AgentsRepository {

    override suspend fun agents(): List<Agent> {
        delay(DemoScenario.NETWORK_FEEL_MS)
        return listOf(scenario.agent())
    }
}

@Singleton
class DemoEventsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val scenario: DemoScenario,
) : EventsRepository {

    override suspend fun events(agentId: String?): List<FallEvent> {
        delay(DemoScenario.NETWORK_FEEL_MS)
        return scenario.events()
    }

    /**
     * Кадр и запись взяты из UR Fall Detection Dataset, ролик `fall-03`,
     * камера 0: человек входит в комнату, падает и остаётся лежать. Кадр —
     * момент, когда он уже на полу; запись — те же семь секунд целиком, 4:3
     * вписаны в 16:9 чёрными полями, чтобы проигрыватель не растягивал людей.
     *
     * Лицензия датасета — CC BY-NC-SA 4.0: только некоммерческое использование
     * с указанием авторства (B. Kwolek, M. Kepski, Жешувский университет).
     * Если проект станет коммерческим, оба файла придётся заменить своими.
     *
     * До 7 октября здесь лежала постановочная фотография, развёрнутая в видео
     * неподвижным кадром. Проигрыватель она проверяла целиком — загрузка,
     * прогресс, воспроизведение, внешний плеер, — но «запись падения», в
     * которой ничего не происходит, на показе выглядела неубедительно.
     */
    override suspend fun media(mediaId: String, onProgress: (Long, Long?) -> Unit): MediaPayload {
        delay(DemoScenario.NETWORK_FEEL_MS)
        return when (mediaId) {
            DemoScenario.DEMO_FRAME_MEDIA_ID -> MediaPayload.Image(
                context.resources.openRawResource(R.raw.demo_fall_frame).use { it.readBytes() }
            )

            DemoScenario.DEMO_CLIP_MEDIA_ID -> MediaPayload.Video(demoClip(onProgress))

            else -> throw AppError.MediaNotFound
        }
    }

    /**
     * Клип из ресурсов копируется в тот же каталог кеша, что и настоящие записи:
     * так его отдаёт внешнему плееру тот же FileProvider, и путь в приложении
     * один на оба режима.
     */
    private suspend fun demoClip(onProgress: (Long, Long?) -> Unit): File {
        val file = File(context.cacheDir, "media/demo-clip.mp4")
        if (!file.isFile || file.length() == 0L) {
            file.parentFile?.mkdirs()
            context.resources.openRawResource(R.raw.demo_fall_clip).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
        // Прогресс изображается шагами: иначе индикатор загрузки не увидеть,
        // а именно его и нужно показать на демонстрации.
        val total = file.length()
        for (step in 1..4) {
            delay(DemoScenario.NETWORK_FEEL_MS / 3)
            onProgress(total * step / 4, total)
        }
        return file
    }
}
