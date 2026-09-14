package ru.terentiy.mobile.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {

    @Test
    fun `корректный PIN — ровно шесть цифр`() {
        assertTrue(Validation.isPinValid("123456"))
    }

    @Test
    fun `PIN короче шести цифр отклоняется на клиенте`() {
        assertFalse(Validation.isPinValid("12345"))
    }

    @Test
    fun `PIN длиннее шести цифр отклоняется`() {
        assertFalse(Validation.isPinValid("1234567"))
    }

    @Test
    fun `PIN с буквами отклоняется`() {
        assertFalse(Validation.isPinValid("12a456"))
    }

    @Test
    fun `ввод PIN очищается от нецифр и обрезается по длине`() {
        assertEquals("123456", "1a2b3c4d5e6f7g".asPinInput())
        assertEquals("12", "  1 2 ".asPinInput())
    }

    @Test
    fun `адрес сервера требует схему`() {
        assertTrue(Validation.isServerUrlValid("http://10.0.2.2:8080"))
        assertTrue(Validation.isServerUrlValid("https://dev.example:8443"))
        assertFalse(Validation.isServerUrlValid("10.0.2.2:8080"))
        assertFalse(Validation.isServerUrlValid("http://"))
        assertFalse(Validation.isServerUrlValid(""))
    }
}
