package ru.terentiy.mobile.domain

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Матрица состояния контура наблюдения.
 *
 * Состояние вычисляется на клиенте: в реализованном контракте поля «наблюдение
 * включено» нет вовсе. Поэтому матрица покрывается целиком — это единственное
 * место, где решается, что показать пользователю.
 */
class WatchStatusTest {

    private val now: Instant = Instant.parse("2026-09-30T12:00:00Z")

    private fun agent(
        online: Boolean = true,
        agentOnline: Tri = Tri.YES,
        camera: Tri = Tri.YES,
        stream: Tri = Tri.YES,
        queueSize: Int? = 0,
        reportedAt: Instant? = now,
        lastFrameAt: Instant? = now.minusSeconds(2),
    ) = Agent(
        id = "agent-1",
        householdId = "house-1",
        name = "Гостиная",
        cameraId = "camera-1",
        softwareVersion = "1.0.0",
        createdAt = now.minus(Duration.ofDays(3)),
        online = online,
        agentOnline = agentOnline,
        cameraConnected = camera,
        streamActive = stream,
        queueSize = queueSize,
        reportedAt = reportedAt,
        lastFrameAt = lastFrameAt,
    )

    @Test
    fun `здоровый контур — наблюдение работает`() {
        assertEquals(WatchState.WATCHING, WatchStatus.of(agent()))
    }

    @Test
    fun `пустой список агентов — это отдельное состояние, а не всё хорошо`() {
        assertEquals(WatchState.NO_AGENT, WatchStatus.worstOf(emptyList()))
    }

    @Test
    fun `агент ни разу не отчитался — состояние неизвестно, а не поломка`() {
        val silent = agent(reportedAt = null, lastFrameAt = null, online = false)
        assertEquals(WatchState.UNKNOWN, WatchStatus.of(silent))
    }

    @Test
    fun `сервер считает агента недоступным, и это важнее его собственных флагов`() {
        // agent_online остаётся true навсегда: это последнее, что агент успел
        // сказать о себе. Вердикт строится на серверном online.
        val dead = agent(online = false, agentOnline = Tri.YES, camera = Tri.YES, stream = Tri.YES)
        assertEquals(WatchState.AGENT_OFFLINE, WatchStatus.of(dead))
    }

    @Test
    fun `камера отключена`() {
        assertEquals(WatchState.CAMERA_UNAVAILABLE, WatchStatus.of(agent(camera = Tri.NO)))
    }

    @Test
    fun `поток прервался при подключённой камере`() {
        assertEquals(WatchState.STREAM_STALLED, WatchStatus.of(agent(stream = Tri.NO)))
    }

    @Test
    fun `неизвестные флаги при живом агенте не считаются исправностью`() {
        assertEquals(WatchState.UNKNOWN, WatchStatus.of(agent(camera = Tri.UNKNOWN)))
        assertEquals(WatchState.UNKNOWN, WatchStatus.of(agent(stream = Tri.UNKNOWN)))
    }

    @Test
    fun `отставание кадров считается по серверным временам, а не по часам телефона`() {
        // Оба времени приходят с сервера. Если бы отставание считалось от
        // Instant.now(), сбитые часы устройства давали бы ложную тревогу.
        val lagging = agent(reportedAt = now, lastFrameAt = now.minusSeconds(120))
        assertEquals(WatchState.FRAMES_DELAYED, WatchStatus.of(lagging))

        val fresh = agent(reportedAt = now.minus(Duration.ofHours(5)), lastFrameAt = now.minus(Duration.ofHours(5)).minusSeconds(3))
        // Данные старые, но отставания между heartbeat и кадром нет: контур был
        // исправен на момент последнего отчёта.
        assertEquals(WatchState.WATCHING, WatchStatus.of(fresh))
    }

    @Test
    fun `кадров нет вовсе, хотя агент отчитался`() {
        assertEquals(WatchState.FRAMES_DELAYED, WatchStatus.of(agent(lastFrameAt = null)))
    }

    @Test
    fun `растущая очередь на агенте означает, что падение не будет замечено`() {
        assertEquals(WatchState.FRAMES_DELAYED, WatchStatus.of(agent(queueSize = 24)))
        assertEquals(WatchState.WATCHING, WatchStatus.of(agent(queueSize = 3)))
    }

    @Test
    fun `пороги задаются политикой, а не зашиты в матрицу`() {
        val lagging = agent(reportedAt = now, lastFrameAt = now.minusSeconds(45))
        assertEquals(WatchState.WATCHING, WatchStatus.of(lagging))
        assertEquals(
            WatchState.FRAMES_DELAYED,
            WatchStatus.of(lagging, WatchPolicy(frameLagLimit = Duration.ofSeconds(30))),
        )
    }

    @Test
    fun `при нескольких агентах берётся худшее состояние`() {
        val healthy = agent()
        val broken = agent(camera = Tri.NO)
        assertEquals(WatchState.CAMERA_UNAVAILABLE, WatchStatus.worstOf(listOf(healthy, broken)))
        assertEquals(WatchState.CAMERA_UNAVAILABLE, WatchStatus.worstOf(listOf(broken, healthy)))
    }

    @Test
    fun `молчащий агент считается худшим, чем заведомо выключенный`() {
        // Про выключенный известно, что он выключен. Про молчащий неизвестно
        // даже то, работал ли контур когда-либо.
        val silent = agent(reportedAt = null, lastFrameAt = null)
        val offline = agent(online = false)
        assertEquals(WatchState.UNKNOWN, WatchStatus.worstOf(listOf(offline, silent)))
    }

    @Test
    fun `отставание не может быть отрицательным`() {
        val ahead = agent(reportedAt = now, lastFrameAt = now.plusSeconds(30))
        assertEquals(Duration.ZERO, WatchStatus.frameLag(ahead))
    }
}
