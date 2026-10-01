package ru.terentiy.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriTest {

    @Test
    fun `null превращается в UNKNOWN, а не в NO`() {
        assertEquals(Tri.UNKNOWN, Tri.of(null))
    }

    @Test
    fun `булевы значения отображаются напрямую`() {
        assertEquals(Tri.YES, Tri.of(true))
        assertEquals(Tri.NO, Tri.of(false))
    }

    @Test
    fun `isYes истинно только для YES`() {
        assertTrue(Tri.YES.isYes)
        assertFalse(Tri.NO.isYes)
        assertFalse(Tri.UNKNOWN.isYes)
    }

    @Test
    fun `isKnown отделяет отсутствие данных от отрицательного ответа`() {
        // На этом различении держится вся матрица состояний контура: «нет
        // данных» и «камера не работает» — разные сообщения пользователю.
        assertTrue(Tri.YES.isKnown)
        assertTrue(Tri.NO.isKnown)
        assertFalse(Tri.UNKNOWN.isKnown)
    }
}
