package ru.terentiy.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.terentiy.mobile.data.remote.ZoneDto
import ru.terentiy.mobile.data.remote.toDomain
import ru.terentiy.mobile.data.remote.toRequest

/** Проверка контура и разбор зон по контракту mobile-server 2.0.0-draft. */
class ZonesTest {

    private val square = listOf(ZonePoint(0.2f, 0.2f), ZonePoint(0.6f, 0.2f), ZonePoint(0.6f, 0.6f), ZonePoint(0.2f, 0.6f))

    @Test
    fun `правильный контур проходит проверку`() {
        assertNull(ZoneGeometry.problem(square))
    }

    @Test
    fun `проблемы контура распознаются`() {
        assertEquals(ZoneGeometry.Problem.TOO_FEW_POINTS, ZoneGeometry.problem(square.take(2)))
        val bowTie = listOf(ZonePoint(0.2f, 0.2f), ZonePoint(0.6f, 0.6f), ZonePoint(0.6f, 0.2f), ZonePoint(0.2f, 0.6f))
        assertEquals(ZoneGeometry.Problem.SELF_INTERSECTING, ZoneGeometry.problem(bowTie))
        val tiny = listOf(ZonePoint(0.1f, 0.1f), ZonePoint(0.15f, 0.1f), ZonePoint(0.15f, 0.15f))
        assertEquals(ZoneGeometry.Problem.TOO_SMALL, ZoneGeometry.problem(tiny))
    }

    @Test
    fun `зона с сервера разбирается`() {
        val zone = ZoneDto(
            zoneId = "z1",
            agentId = "a1",
            cameraId = "camera-1",
            name = "Дверь",
            polygon = listOf(listOf(0.1, 0.2), listOf(0.5, 0.2), listOf(0.5), listOf(0.5, 0.6)),
            referenceFrameId = "f1",
            referenceMediaId = "m1",
            state = "bound",
            version = 3,
        ).toDomain()
        assertEquals(ZoneState.BOUND, zone.state)
        assertEquals(3, zone.version)
        // Вершина не из двух чисел пропущена.
        assertEquals(listOf(ZonePoint(0.1f, 0.2f), ZonePoint(0.5f, 0.2f), ZonePoint(0.5f, 0.6f)), zone.polygon)
        assertEquals(ZoneState.DRAFT, ZoneDto(zoneId = "z2", state = "something_new").toDomain().state)
    }

    @Test
    fun `тело запроса по контракту`() {
        val draft = ZoneDraft(name = "  Дверь ", polygon = listOf(ZonePoint(0.1234567f, 0.5f)), referenceFrameId = "f1")
        val create = draft.toRequest()
        assertEquals("Дверь", create.name)
        assertEquals(0.123457, create.polygon.single()[0], 1e-9)
        assertEquals("f1", create.referenceFrameId)
        assertEquals(true, create.enabled)
        assertEquals("enter", create.rules.single().trigger)
        assertEquals("Дверь", ZonePatch(name = " Дверь ").toRequest(revision = 4).name)
        assertEquals(4, ZonePatch(enabled = false).toRequest(revision = 4).revision)
    }
}
