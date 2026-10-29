package ru.terentiy.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Матрица состояния наблюдения на главном экране. */
class WatchStatusTest {

    private fun agent(online: Boolean = true, hasFreshFrame: Boolean = true) = Agent(
        id = "agent-1",
        name = "Гостиная",
        cameraId = "camera-1",
        online = online,
        hasFreshFrame = hasFreshFrame,
    )

    @Test
    fun `агент на связи и свежий кадр есть — наблюдение работает`() {
        assertEquals(WatchState.WATCHING, WatchStatus.of(agent()))
    }

    @Test
    fun `агент на связи, но свежего кадра нет — камера недоступна`() {
        assertEquals(WatchState.CAMERA_UNAVAILABLE, WatchStatus.of(agent(hasFreshFrame = false)))
    }

    @Test
    fun `агент не на связи — устройство не отвечает, даже если кадр был`() {
        assertEquals(WatchState.AGENT_OFFLINE, WatchStatus.of(agent(online = false, hasFreshFrame = true)))
    }

    @Test
    fun `сервер недоступен важнее данных об агентах`() {
        assertEquals(WatchState.SERVER_UNAVAILABLE, WatchStatus.of(listOf(agent()), serverReachable = false))
        assertEquals(WatchState.SERVER_UNAVAILABLE, WatchStatus.of(emptyList(), serverReachable = false))
    }

    @Test
    fun `пустой список агентов — устройство не подключено`() {
        assertEquals(WatchState.NO_AGENT, WatchStatus.of(emptyList(), serverReachable = true))
    }

    @Test
    fun `при нескольких агентах берётся худшее состояние`() {
        val agents = listOf(agent(), agent(hasFreshFrame = false), agent(online = false))
        assertEquals(WatchState.AGENT_OFFLINE, WatchStatus.of(agents, serverReachable = true))
    }
}
