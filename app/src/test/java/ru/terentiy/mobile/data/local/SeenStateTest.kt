package ru.terentiy.mobile.data.local

import java.time.Instant
import ru.terentiy.mobile.domain.EventStatus
import ru.terentiy.mobile.domain.FallEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Отметки просмотра.
 *
 * Признака `reviewed` сервер не хранит, поэтому «новое» — понятие устройства.
 * Водяной знак решает главную проблему такой схемы: после переустановки
 * приходит вся история домохозяйства, и без знака приложение при первом запуске
 * закричало бы о падении трёхдневной давности.
 */
class SeenStateTest {

    private fun event(id: String, at: Instant?) = FallEvent(
        id = id,
        agentId = "agent-1",
        frameId = "$id-frame",
        frameMediaId = "$id-media",
        clipMediaId = "$id-clip",
        capturedAt = at,
        createdAt = at,
        status = EventStatus.CONFIRMED,
        analysis = null,
    )

    private val noon: Instant = Instant.parse("2026-09-30T12:00:00Z")

    @Test
    fun `без отметок и без знака всё считается новым`() {
        assertFalse(SeenState.Empty.isSeen(event("e1", noon)))
    }

    @Test
    fun `отметка по идентификатору`() {
        val state = SeenState(ids = setOf("e1"))
        assertTrue(state.isSeen(event("e1", noon)))
        assertFalse(state.isSeen(event("e2", noon)))
    }

    @Test
    fun `всё не новее знака считается просмотренным`() {
        val state = SeenState(watermark = noon)
        assertTrue(state.isSeen(event("old", noon.minusSeconds(60))))
        assertTrue(state.isSeen(event("same", noon)))
        assertFalse(state.isSeen(event("new", noon.plusSeconds(1))))
    }

    @Test
    fun `событие без времени знаком не закрывается`() {
        // Иначе непонятное событие молча исчезло бы из «новых».
        val state = SeenState(watermark = noon)
        assertFalse(state.isSeen(event("no-time", null)))
    }
}
