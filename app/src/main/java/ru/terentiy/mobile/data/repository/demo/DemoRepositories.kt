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
import ru.terentiy.mobile.data.repository.ZonesRepository
import ru.terentiy.mobile.data.repository.EventsRepository
import ru.terentiy.mobile.domain.Agent
import ru.terentiy.mobile.domain.CurrentFrame
import java.util.UUID
import ru.terentiy.mobile.domain.Zone
import ru.terentiy.mobile.domain.ZoneBindingSession
import ru.terentiy.mobile.domain.ZoneDraft
import ru.terentiy.mobile.domain.ZoneGeometry
import ru.terentiy.mobile.domain.ZoneState
import ru.terentiy.mobile.domain.EventStatus
import ru.terentiy.mobile.domain.FallAnalysis
import ru.terentiy.mobile.domain.FallEvent
import ru.terentiy.mobile.domain.MediaPayload
import ru.terentiy.mobile.domain.Session
import ru.terentiy.mobile.domain.SessionState

/**
 * Сценарий демонстрации.
 *
 * Состояние агента продвигается по времени от первого обращения, а не по таймеру:
 * приложение может пролежать свёрнутым, и сценарий должен соответствовать
 * настоящим часам, а не количеству чтений.
 *
 * За минуту сценарий показывает все состояния наблюдения, которые может
 * показать демо («Сервер недоступен» изображать нечем — сервера в демо нет):
 *
 * | Время | Что видно |
 * |---|---|
 * | 0–35 с | наблюдение работает |
 * | 23 с | появляется событие падения с кадром |
 * | 35–49 с | камера недоступна |
 * | 49–55 с | устройство наблюдения не отвечает |
 * | далее | цикл с начала |
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
        when (elapsedSeconds() % CYCLE) {
            in 0 until CAMERA_LOST_AT -> template
            in CAMERA_LOST_AT until AGENT_LOST_AT -> template.copy(hasFreshFrame = false)
            else -> template.copy(online = false, hasFreshFrame = false)
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
        name = AGENT_NAME,
        cameraId = "demo-camera-1",
        online = true,
        hasFreshFrame = true,
    )

    companion object {
        const val AGENT_ID = "demo-agent-0001"
        const val AGENT_NAME = "Гостиная"
        const val HOUSEHOLD_ID = "demo-household"
        const val DEMO_FRAME_MEDIA_ID = "demo-frame"
        const val DEMO_CLIP_MEDIA_ID = "demo-clip"
        const val DEMO_EVENT_ID = "demo-event-fresh"

        private const val EVENT_AT = 23L
        private const val CAMERA_LOST_AT = 35L
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

/**
 * Зоны в показе без сервера: живут в памяти до конца процесса и отвечают по
 * тем же правилам, что сервер по контракту (версии, лимит, перерисовка
 * сбрасывает состояние в «не проверена»). Вид с камеры — демо-кадр события.
 */
@Singleton
class DemoZonesRepository @Inject constructor() : ZonesRepository {

    private val mutex = Mutex()
    private val zones = mutableListOf<Zone>()

    override suspend fun currentFrame(agentId: String, cameraId: String): CurrentFrame {
        delay(DemoScenario.NETWORK_FEEL_MS)
        return CurrentFrame(
            frameId = DEMO_FRAME_ID,
            mediaId = DemoScenario.DEMO_FRAME_MEDIA_ID,
            capturedAt = Instant.now(),
            width = 640,
            height = 480,
        )
    }

    override suspend fun zones(agentId: String, cameraId: String): List<Zone> {
        delay(DemoScenario.NETWORK_FEEL_MS)
        return mutex.withLock { zones.filter { it.agentId == agentId && it.cameraId == cameraId } }
    }

    override suspend fun create(agentId: String, cameraId: String, draft: ZoneDraft): Zone {
        delay(DemoScenario.NETWORK_FEEL_MS)
        return mutex.withLock {
            if (zones.count { it.agentId == agentId && it.cameraId == cameraId } >= ZoneGeometry.MAX_ZONES) {
                throw AppError.ZoneLimit
            }
            Zone(
                id = UUID.randomUUID().toString(),
                agentId = agentId,
                cameraId = cameraId,
                name = draft.name.trim(),
                polygon = draft.polygon,
                referenceFrameId = draft.referenceFrameId,
                referenceMediaId = DemoScenario.DEMO_FRAME_MEDIA_ID,
                state = ZoneState.DRAFT,
                version = 1,
            ).also { zones += it }
        }
    }

    override suspend fun update(agentId: String, cameraId: String, zoneId: String, version: Int, draft: ZoneDraft): Zone {
        delay(DemoScenario.NETWORK_FEEL_MS)
        return mutex.withLock {
            val index = zones.indexOfFirst { it.id == zoneId && it.agentId == agentId && it.cameraId == cameraId }
            if (index < 0) throw AppError.ZoneNotFound
            val zone = zones[index]
            if (zone.version != version) throw AppError.ZoneConflict
            val redrawn = zone.polygon != draft.polygon || zone.referenceFrameId != draft.referenceFrameId
            zone.copy(
                name = draft.name.trim(),
                polygon = draft.polygon,
                referenceFrameId = draft.referenceFrameId,
                state = if (redrawn) ZoneState.DRAFT else zone.state,
                version = zone.version + 1,
            ).also { zones[index] = it }
        }
    }

    override suspend fun delete(agentId: String, cameraId: String, zoneId: String) {
        delay(DemoScenario.NETWORK_FEEL_MS)
        mutex.withLock { zones.removeAll { it.id == zoneId } }
    }

    // Проверка зоны — это распознавание человека на живых кадрах камеры; её не
    // имитируем: проверять экран нужно с сервером в демо-режиме (видеофайл вместо камеры).
    override suspend fun startBinding(agentId: String, cameraId: String, zoneId: String, version: Int): ZoneBindingSession =
        throw AppError.ZoneCheckNeedsServer

    override suspend fun binding(agentId: String, cameraId: String, zoneId: String, sessionId: String): ZoneBindingSession =
        throw AppError.ZoneCheckNeedsServer

    override suspend fun cancelBinding(agentId: String, cameraId: String, zoneId: String, sessionId: String) = Unit

    private companion object {
        const val DEMO_FRAME_ID = "demo-frame-for-zones"
    }
}
