package ru.terentiy.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.terentiy.mobile.data.remote.CurrentFrameDto
import ru.terentiy.mobile.data.remote.DetectedPersonDto
import ru.terentiy.mobile.data.remote.toDomain

/** Контур зоны и проверка «человек в зоне» по рамкам детектора. */
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
    fun `доля прямоугольника внутри зоны`() {
        assertEquals(1f, ZoneGeometry.shareInside(square, PersonBox(0.3f, 0.3f, 0.5f, 0.5f)), 1e-5f)
        assertEquals(0f, ZoneGeometry.shareInside(square, PersonBox(0.7f, 0.7f, 0.8f, 0.8f)), 1e-5f)
        // Наполовину внутри по горизонтали.
        assertEquals(0.5f, ZoneGeometry.shareInside(square, PersonBox(0.5f, 0.3f, 0.7f, 0.4f)), 1e-5f)
        // Невыпуклая «буква Г»: прямоугольник над выемкой — снаружи.
        val ell = listOf(
            ZonePoint(0.1f, 0.1f), ZonePoint(0.3f, 0.1f), ZonePoint(0.3f, 0.5f),
            ZonePoint(0.7f, 0.5f), ZonePoint(0.7f, 0.7f), ZonePoint(0.1f, 0.7f),
        )
        assertEquals(0f, ZoneGeometry.shareInside(ell, PersonBox(0.4f, 0.2f, 0.6f, 0.4f)), 1e-5f)
        assertEquals(1f, ZoneGeometry.shareInside(ell, PersonBox(0.4f, 0.55f, 0.6f, 0.65f)), 1e-5f)
    }

    @Test
    fun `стоящий в зоне — если в зоне ноги, а не туловище`() {
        // Человек стоит ниже зоны, но рамка заходит в неё туловищем.
        val standingBelow = PersonBox(left = 0.3f, top = 0.3f, right = 0.5f, bottom = 0.8f)
        assertEquals(ZonePresence.OUTSIDE, ZonePresence.of(square, listOf(standingBelow)))
        val standingInside = PersonBox(left = 0.3f, top = 0.1f, right = 0.5f, bottom = 0.5f)
        assertEquals(ZonePresence.INSIDE, ZonePresence.of(square, listOf(standingBelow, standingInside)))
        assertEquals(ZonePresence.NOBODY, ZonePresence.of(square, emptyList()))
    }

    @Test
    fun `лежащий в зоне, если в зоне ноги`() {
        // Случай с живого стенда 13 октября: человек лежит на полу, зона —
        // вокруг ног. Середина нижней стороны рамки — снаружи, у угла зоны.
        val lying = PersonBox(left = 120f / 640, top = 315f / 480, right = 450f / 640, bottom = 478f / 480)
        val aroundLegs = listOf(
            ZonePoint(0.49f, 0.99f), ZonePoint(0.54f, 0.68f), ZonePoint(0.73f, 0.72f), ZonePoint(0.74f, 0.99f),
        )
        assertEquals(ZonePresence.INSIDE, ZonePresence.of(aroundLegs, listOf(lying)))
        // Зона у двери, выше и правее человека, — снаружи.
        val door = listOf(ZonePoint(0.75f, 0.55f), ZonePoint(0.85f, 0.55f), ZonePoint(0.85f, 0.65f), ZonePoint(0.75f, 0.65f))
        assertEquals(ZonePresence.OUTSIDE, ZonePresence.of(door, listOf(lying)))
    }

    @Test
    fun `касание краем — не в зоне`() {
        // Опора наполовину в квадрате — в зоне; на десятую часть — нет.
        val half = PersonBox(left = 0.5f, top = 0.2f, right = 0.7f, bottom = 0.6f)
        val edge = PersonBox(left = 0.58f, top = 0.2f, right = 0.78f, bottom = 0.6f)
        assertEquals(ZonePresence.INSIDE, ZonePresence.of(square, listOf(half)))
        assertEquals(ZonePresence.OUTSIDE, ZonePresence.of(square, listOf(edge)))
    }

    @Test
    fun `рамки из пикселей переводятся в доли кадра`() {
        val frame = CurrentFrameDto(
            frameId = "f",
            mediaId = "m",
            capturedAt = "2026-10-13T10:00:00Z",
            width = 640,
            height = 480,
            people = listOf(
                DetectedPersonDto(listOf(64.0, 48.0, 320.0, 480.0), 0.9),
                DetectedPersonDto(listOf(1.0, 2.0)), // неполная
                DetectedPersonDto(listOf(300.0, 10.0, 100.0, 20.0)), // перевёрнутая
            ),
        ).toDomain()
        assertEquals(listOf(PersonBox(0.1f, 0.1f, 0.5f, 1f)), frame.people)
        val support = frame.people.single().support
        assertEquals(0.775f, support.top, 1e-5f)
        assertEquals(1f, support.bottom, 1e-5f)
        assertEquals(java.time.Instant.parse("2026-10-13T10:00:00Z"), frame.capturedAt)
    }

    @Test
    fun `без размеров кадра рамок нет`() {
        val frame = CurrentFrameDto(
            frameId = "f",
            mediaId = "m",
            people = listOf(DetectedPersonDto(listOf(64.0, 48.0, 320.0, 480.0))),
        ).toDomain()
        assertTrue(frame.people.isEmpty())
    }
}
