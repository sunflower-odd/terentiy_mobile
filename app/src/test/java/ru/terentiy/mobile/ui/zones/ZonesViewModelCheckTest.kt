package ru.terentiy.mobile.ui.zones

import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.terentiy.mobile.R
import ru.terentiy.mobile.core.error.AppError
import ru.terentiy.mobile.data.repository.AgentsRepository
import ru.terentiy.mobile.data.repository.EventsRepository
import ru.terentiy.mobile.data.repository.ZonesRepository
import ru.terentiy.mobile.domain.Agent
import ru.terentiy.mobile.domain.CurrentFrame
import ru.terentiy.mobile.domain.FallEvent
import ru.terentiy.mobile.domain.MediaPayload
import ru.terentiy.mobile.domain.Zone
import ru.terentiy.mobile.domain.ZonePatch
import ru.terentiy.mobile.domain.ZoneBindingFailure
import ru.terentiy.mobile.domain.ZoneBindingSession
import ru.terentiy.mobile.domain.ZoneBindingState
import ru.terentiy.mobile.domain.ZoneDraft
import ru.terentiy.mobile.domain.ZoneObservation
import ru.terentiy.mobile.domain.ZonePoint
import ru.terentiy.mobile.domain.ZoneState

/**
 * Проверка зоны в ViewModel: запуск, опрос, отмена, сбои сети и предел опроса.
 * Сервер подменён: ответы задаются очередью, время — виртуальное.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZonesViewModelCheckTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val square = listOf(ZonePoint(0.2f, 0.5f), ZonePoint(0.6f, 0.5f), ZonePoint(0.6f, 0.9f), ZonePoint(0.2f, 0.9f))

    private fun zone(state: ZoneState = ZoneState.DRAFT) = Zone(
        id = "z1", agentId = "agent-1", cameraId = "camera-1", name = "Дверь", polygon = square,
        referenceFrameId = "frame-1", referenceMediaId = "media-ref", state = state, version = 3,
    )

    private val started = Instant.parse("2026-11-02T10:00:00Z")

    private fun session(
        state: ZoneBindingState,
        observation: ZoneObservation? = null,
        failure: ZoneBindingFailure? = null,
        id: String = "s1",
        lifetimeSeconds: Long = 60,
    ) = ZoneBindingSession(id, "z1", 3, state, started, started.plusSeconds(lifetimeSeconds), observation, failure)

    private class FakeZones(var list: List<Zone>) : ZonesRepository {
        val starts = ArrayDeque<suspend () -> ZoneBindingSession>()
        val polls = ArrayDeque<() -> ZoneBindingSession>()
        var pollForever: (() -> ZoneBindingSession)? = null
        val startedVersions = mutableListOf<Int>()
        val cancelled = mutableListOf<String>()

        override suspend fun currentFrame(agentId: String, cameraId: String) = CurrentFrame("frame-1", "media-ref", null, 640, 480)
        override suspend fun zones(agentId: String, cameraId: String) = list
        override suspend fun create(agentId: String, cameraId: String, draft: ZoneDraft) = error("not used")
        override suspend fun patch(agentId: String, cameraId: String, zoneId: String, revision: Int, patch: ZonePatch) = error("not used")
        override suspend fun delete(agentId: String, cameraId: String, zoneId: String) = Unit

        override suspend fun startBinding(agentId: String, cameraId: String, zoneId: String, version: Int): ZoneBindingSession {
            startedVersions += version
            return starts.removeFirst()()
        }

        override suspend fun binding(agentId: String, cameraId: String, zoneId: String, sessionId: String): ZoneBindingSession =
            polls.removeFirstOrNull()?.invoke() ?: pollForever?.invoke() ?: error("unexpected poll")

        override suspend fun cancelBinding(agentId: String, cameraId: String, zoneId: String, sessionId: String) {
            cancelled += sessionId
        }
    }

    private class FakeEvents : EventsRepository {
        val requested = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun events(agentId: String?): List<FallEvent> = emptyList()
        override suspend fun media(mediaId: String, onProgress: (Long, Long?) -> Unit): MediaPayload {
            requested += mediaId
            gate?.await()
            return MediaPayload.Image(mediaId.toByteArray())
        }
    }

    private val agents = object : AgentsRepository {
        override suspend fun agents() = listOf(Agent("agent-1", "Гостиная", "camera-1", online = true))
    }

    private val zones = FakeZones(listOf(zone()))
    private val events = FakeEvents()

    private fun viewModel() = ZonesViewModel(agents, zones, events)

    @Test
    fun `успешная проверка доходит до «проверена» и обновляет список`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        zones.starts += { session(ZoneBindingState.WAITING_FOR_FRAME) }
        zones.polls += { session(ZoneBindingState.WAITING_FOR_PERSON) }
        zones.polls += { session(ZoneBindingState.COLLECTING, ZoneObservation("m1", ZonePoint(0.4f, 0.7f), inZone = true)) }
        zones.polls += {
            zones.list = listOf(zone(ZoneState.BOUND))
            session(ZoneBindingState.BOUND, ZoneObservation("m2", ZonePoint(0.4f, 0.7f), inZone = true))
        }

        vm.startCheck("z1")
        advanceUntilIdle()

        val check = vm.state.value.check!!
        assertTrue(check.bound)
        assertFalse(check.running)
        assertEquals(listOf(3), zones.startedVersions)
        assertEquals(ZoneState.BOUND, vm.state.value.zones.single().state)
        assertTrue("кадр наблюдения загружен", check.frameMediaId in listOf("m1", "m2"))
    }

    @Test
    fun `кратковременная потеря сети не обрывает проверку`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        zones.starts += { session(ZoneBindingState.WAITING_FOR_FRAME) }
        zones.polls += { throw AppError.Network }
        zones.polls += { throw AppError.Network }
        zones.polls += { session(ZoneBindingState.BOUND) }

        vm.startCheck("z1")
        advanceUntilIdle()

        assertTrue(vm.state.value.check!!.bound)
    }

    @Test
    fun `неудача приходит с причиной от сервера`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        zones.starts += { session(ZoneBindingState.WAITING_FOR_FRAME) }
        zones.polls += { session(ZoneBindingState.EXPIRED, failure = ZoneBindingFailure.NO_PERSON) }

        vm.startCheck("z1")
        advanceUntilIdle()

        val check = vm.state.value.check!!
        assertFalse(check.running)
        assertFalse(check.bound)
        assertEquals(ZoneBindingFailure.NO_PERSON, check.failure)
        assertNull(check.errorRes)
    }

    @Test
    fun `отмена во время запуска отменяет сессию и на сервере`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val start = CompletableDeferred<ZoneBindingSession>()
        zones.starts += { start.await() }

        vm.startCheck("z1")
        advanceUntilIdle()
        vm.cancelCheck()
        start.complete(session(ZoneBindingState.WAITING_FOR_FRAME, id = "late"))
        advanceUntilIdle()

        assertNull(vm.state.value.check)
        assertEquals(listOf("late"), zones.cancelled)
    }

    @Test
    fun `ошибка отменённой попытки не попадает в новую`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val first = CompletableDeferred<ZoneBindingSession>()
        zones.starts += { first.await() }
        zones.starts += { session(ZoneBindingState.WAITING_FOR_FRAME, id = "s2") }
        zones.pollForever = { session(ZoneBindingState.WAITING_FOR_PERSON, id = "s2") }

        vm.startCheck("z1")
        advanceUntilIdle()
        vm.cancelCheck()
        vm.startCheck("z1")
        first.completeExceptionally(AppError.ZoneBusy)
        dispatcher.scheduler.advanceTimeBy(3_000)

        val check = vm.state.value.check!!
        assertNull(check.errorRes)
        assertTrue(check.running)
        assertEquals("s2", check.sessionId)
        vm.cancelCheck()
        advanceUntilIdle()
    }

    @Test
    fun `опрос ограничен сроком сессии`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        zones.starts += { session(ZoneBindingState.WAITING_FOR_FRAME, lifetimeSeconds = 5) }
        zones.pollForever = { session(ZoneBindingState.COLLECTING, lifetimeSeconds = 5) }

        vm.startCheck("z1")
        advanceUntilIdle()

        val check = vm.state.value.check!!
        assertFalse(check.running)
        assertEquals(R.string.zone_check_failure_unknown, check.errorRes)
        assertEquals(listOf("s1"), zones.cancelled)
    }

    @Test
    fun `медленная загрузка кадра не задерживает опрос`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        events.gate = CompletableDeferred()
        zones.starts += { session(ZoneBindingState.WAITING_FOR_FRAME) }
        zones.polls += { session(ZoneBindingState.COLLECTING, ZoneObservation("m1", ZonePoint(0.4f, 0.7f), inZone = true)) }
        zones.polls += { session(ZoneBindingState.BOUND) }

        vm.startCheck("z1")
        advanceUntilIdle()

        assertTrue(vm.state.value.check!!.bound)
        events.gate?.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `в показе без сервера объясняется, что нужен сервер`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        zones.starts += { throw AppError.ZoneCheckNeedsServer }

        vm.startCheck("z1")
        advanceUntilIdle()

        assertEquals(R.string.error_zone_check_needs_server, vm.state.value.check!!.errorRes)
    }
}
