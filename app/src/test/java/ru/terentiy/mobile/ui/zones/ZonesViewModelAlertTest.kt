package ru.terentiy.mobile.ui.zones

import java.time.LocalTime
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
import org.junit.Assert.assertNotNull
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
import ru.terentiy.mobile.domain.ZoneBindingSession
import ru.terentiy.mobile.domain.ZoneDraft
import ru.terentiy.mobile.domain.ZonePatch
import ru.terentiy.mobile.domain.ZonePoint
import ru.terentiy.mobile.domain.ZoneRule
import ru.terentiy.mobile.domain.ZoneSchedule
import ru.terentiy.mobile.domain.ZoneState
import ru.terentiy.mobile.domain.ZoneTrigger

/** Переключатель, условие тревоги и частичная правка зоны в ViewModel. Сервер подменён. */
@OptIn(ExperimentalCoroutinesApi::class)
class ZonesViewModelAlertTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val triangle = listOf(ZonePoint(0.2f, 0.5f), ZonePoint(0.6f, 0.5f), ZonePoint(0.6f, 0.9f))

    private val night = ZoneSchedule(LocalTime.of(22, 0), LocalTime.of(7, 0))

    private val zone = Zone(
        id = "z1", agentId = "agent-1", cameraId = "camera-1", name = "Дверь", polygon = triangle,
        referenceFrameId = "frame-1", referenceMediaId = "media-ref", state = ZoneState.BOUND, version = 3,
        revision = 5, rules = listOf(ZoneRule(id = "r1")),
    )

    private class FakeZones(var list: List<Zone>) : ZonesRepository {
        val patches = mutableListOf<Pair<Int, ZonePatch>>()
        val created = mutableListOf<ZoneDraft>()
        var frameRequests = 0
        var failWith: AppError? = null

        override suspend fun currentFrame(agentId: String, cameraId: String): CurrentFrame {
            frameRequests++
            return CurrentFrame("frame-2", "media-2", null, 640, 480)
        }

        override suspend fun zones(agentId: String, cameraId: String) = list

        override suspend fun create(agentId: String, cameraId: String, draft: ZoneDraft): Zone {
            created += draft
            return list.first()
        }

        override suspend fun patch(agentId: String, cameraId: String, zoneId: String, revision: Int, patch: ZonePatch): Zone {
            failWith?.let { throw it }
            patches += revision to patch
            val zone = list.single { it.id == zoneId }
            return zone.copy(
                name = patch.name ?: zone.name,
                enabled = patch.enabled ?: zone.enabled,
                rules = patch.rules ?: zone.rules,
                revision = zone.revision + 1,
            ).also { updated -> list = list.map { if (it.id == zoneId) updated else it } }
        }

        override suspend fun delete(agentId: String, cameraId: String, zoneId: String) = Unit
        override suspend fun startBinding(agentId: String, cameraId: String, zoneId: String, version: Int): ZoneBindingSession =
            error("not used")
        override suspend fun binding(agentId: String, cameraId: String, zoneId: String, sessionId: String): ZoneBindingSession =
            error("not used")
        override suspend fun cancelBinding(agentId: String, cameraId: String, zoneId: String, sessionId: String) = Unit
    }

    private val agents = object : AgentsRepository {
        override suspend fun agents() = listOf(Agent("agent-1", "Гостиная", "camera-1", online = true))
    }

    private val events = object : EventsRepository {
        override suspend fun events(agentId: String?): List<FallEvent> = emptyList()
        override suspend fun media(mediaId: String, onProgress: (Long, Long?) -> Unit) = MediaPayload.Image(ByteArray(0))
    }

    private val zones = FakeZones(listOf(zone))

    private fun viewModel() = ZonesViewModel(agents, zones, events)

    @Test
    fun `переключатель отправляет только enabled с номером правки`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setZoneEnabled("z1", false)
        assertFalse("переключатель меняется сразу", vm.state.value.zones.single().enabled)
        assertTrue(vm.state.value.switchingZoneIds.contains("z1"))
        advanceUntilIdle()

        assertEquals(listOf(5 to ZonePatch(enabled = false)), zones.patches)
        assertTrue(vm.state.value.switchingZoneIds.isEmpty())
        assertEquals(6, vm.state.value.zones.single().revision)
    }

    @Test
    fun `если сервер не сохранил переключатель, он возвращается назад`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        zones.failWith = AppError.ZoneConflict

        vm.setZoneEnabled("z1", false)
        advanceUntilIdle()

        assertTrue(vm.state.value.zones.single().enabled)
        assertEquals(R.string.error_zone_conflict, vm.state.value.errorRes)
    }

    @Test
    fun `тревога из списка меняет первое правило и сохраняет его идентификатор`() = runTest(dispatcher) {
        zones.list = listOf(zone.copy(rules = listOf(ZoneRule(id = "r1"), ZoneRule(id = "r2", trigger = ZoneTrigger.EXIT))))
        val vm = viewModel()
        advanceUntilIdle()

        vm.openAlert("z1")
        assertFalse("по умолчанию — всегда", vm.state.value.alertEditor!!.scheduled)
        vm.setAlertTrigger(ZoneTrigger.EXIT)
        vm.setAlertScheduled(true)
        vm.setAlertFrom(LocalTime.of(22, 0))
        vm.setAlertTo(LocalTime.of(7, 0))
        vm.saveAlert()
        advanceUntilIdle()

        val expected = listOf(ZoneRule("r1", ZoneTrigger.EXIT, night), ZoneRule(id = "r2", trigger = ZoneTrigger.EXIT))
        assertEquals(listOf(5 to ZonePatch(rules = expected)), zones.patches)
        assertNull(vm.state.value.alertEditor)
        assertEquals(expected, vm.state.value.zones.single().rules)
    }

    @Test
    fun `пустой промежуток не сохраняется, «всегда» уходит без промежутка`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.openAlert("z1")
        vm.setAlertScheduled(true)
        vm.setAlertFrom(LocalTime.of(9, 0))
        vm.setAlertTo(LocalTime.of(9, 0))
        vm.saveAlert()
        advanceUntilIdle()
        assertNotNull(vm.state.value.alertEditor?.problemRes)
        assertTrue(zones.patches.isEmpty())

        vm.setAlertScheduled(false)
        vm.saveAlert()
        advanceUntilIdle()
        assertNull(zones.patches.single().second.rules!!.single().schedule)
    }

    @Test
    fun `новая зона уходит вместе с условием тревоги`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.fetchFrame()
        advanceUntilIdle()

        vm.startNew()
        triangle.forEach(vm::addPoint)
        vm.setName("Окно")
        vm.openDraftAlert()
        vm.setAlertTrigger(ZoneTrigger.EXIT)
        vm.setAlertScheduled(true)
        vm.saveAlert()
        assertEquals("до сохранения зоны на сервер ничего не ушло", 0, zones.created.size + zones.patches.size)
        vm.save()
        advanceUntilIdle()

        val draft = zones.created.single()
        assertEquals(listOf(ZoneRule(trigger = ZoneTrigger.EXIT, schedule = ZoneSchedule.DEFAULT_NIGHT)), draft.rules)
        assertEquals("frame-2", draft.referenceFrameId)
    }

    @Test
    fun `правка имени без нового контура не требует кадра и не сбрасывает проверку`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.edit("z1")
        advanceUntilIdle()
        vm.setName("Входная дверь")
        vm.save()
        advanceUntilIdle()

        assertEquals(0, zones.frameRequests)
        assertEquals(listOf(5 to ZonePatch(name = "Входная дверь")), zones.patches)
        assertNull(vm.state.value.edited)
    }

    @Test
    fun `новый контур уходит со свежим кадром`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.edit("z1")
        advanceUntilIdle()
        vm.movePoint(0, ZonePoint(0.1f, 0.4f))
        vm.save()
        advanceUntilIdle()

        val patch = zones.patches.single().second
        assertEquals("frame-2", patch.redraw?.referenceFrameId)
        assertNull(patch.name)
        assertNull(patch.rules)
    }

    @Test
    fun `без изменений сохранение ничего не отправляет`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.edit("z1")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        assertTrue(zones.patches.isEmpty())
        assertNull(vm.state.value.edited)
    }
}
