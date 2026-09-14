package ru.terentiy.mobile.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Матрица состояний наблюдения.
 *
 * Главное, что проверяется: `UNKNOWN` не равно `NO`. Агент, который ни разу
 * не присылал heartbeat, не должен выглядеть как агент с выключенной камерой.
 */
class WatchStatusTest {

    private fun agent(
        online: Boolean = true,
        camera: Tri = Tri.YES,
        stream: Tri = Tri.YES,
        ml: Tri = Tri.YES,
        reportedAt: Instant? = Instant.parse("2026-09-13T10:00:00Z"),
    ) = Agent(
        id = "agent-1",
        name = "Ноутбук",
        cameraId = "camera-room-1",
        softwareVersion = "0.1.0",
        modelVersion = "yolo26m.pt",
        online = online,
        agentOnline = Tri.of(online),
        cameraConnected = camera,
        streamActive = stream,
        mlReady = ml,
        queueSize = 0,
        reportedAt = reportedAt,
        lastFrameAt = null,
    )

    @Test
    fun `всё работает — наблюдение активно`() {
        assertEquals(WatchState.WATCHING, WatchStatus.of(agent()))
    }

    @Test
    fun `агент не на связи важнее любых других флагов`() {
        val offline = agent(online = false, camera = Tri.NO, ml = Tri.NO)
        assertEquals(WatchState.AGENT_OFFLINE, WatchStatus.of(offline))
    }

    @Test
    fun `камера отключена — камера недоступна`() {
        assertEquals(WatchState.CAMERA_UNAVAILABLE, WatchStatus.of(agent(camera = Tri.NO)))
    }

    @Test
    fun `поток не идёт — камера недоступна`() {
        assertEquals(WatchState.CAMERA_UNAVAILABLE, WatchStatus.of(agent(stream = Tri.NO)))
    }

    @Test
    fun `состояние камеры неизвестно — это не норма, а камера недоступна`() {
        assertEquals(WatchState.CAMERA_UNAVAILABLE, WatchStatus.of(agent(camera = Tri.UNKNOWN)))
    }

    @Test
    fun `модель не готова — анализ не выполняется`() {
        assertEquals(WatchState.ML_NOT_READY, WatchStatus.of(agent(ml = Tri.NO)))
    }

    @Test
    fun `готовность модели неизвестна — анализ не выполняется`() {
        assertEquals(WatchState.ML_NOT_READY, WatchStatus.of(agent(ml = Tri.UNKNOWN)))
    }

    @Test
    fun `пустой список агентов — это отдельное состояние, а не норма`() {
        assertEquals(WatchState.NO_AGENT, WatchStatus.worstOf(emptyList()))
    }

    @Test
    fun `один агент — сводное состояние совпадает с его собственным`() {
        assertEquals(WatchState.WATCHING, WatchStatus.worstOf(listOf(agent())))
    }

    @Test
    fun `несколько агентов — берётся худшее состояние`() {
        val agents = listOf(agent(), agent(camera = Tri.NO), agent(ml = Tri.NO))
        assertEquals(WatchState.CAMERA_UNAVAILABLE, WatchStatus.worstOf(agents))
    }

    @Test
    fun `несколько агентов — отключённый агент перевешивает исправные`() {
        val agents = listOf(agent(), agent(online = false))
        assertEquals(WatchState.AGENT_OFFLINE, WatchStatus.worstOf(agents))
    }

    @Test
    fun `порядок констант задаёт серьёзность и не должен меняться`() {
        assertEquals(
            listOf(
                WatchState.NO_AGENT,
                WatchState.AGENT_OFFLINE,
                WatchState.CAMERA_UNAVAILABLE,
                WatchState.ML_NOT_READY,
                WatchState.WATCHING,
            ),
            WatchState.entries.toList(),
        )
    }
}
