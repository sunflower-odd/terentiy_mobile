package ru.terentiy.mobile.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.terentiy.mobile.R

/** Правила взяты из схемы контракта: `pin` соответствует `^\d{6}$`. */
class ValidationTest {

    @Test
    fun `PIN — ровно шесть цифр`() {
        assertTrue(Validation.isPinValid("123456"))
        assertTrue(Validation.isPinValid("000000"))
        assertFalse(Validation.isPinValid("12345"))
        assertFalse(Validation.isPinValid("1234567"))
        assertFalse(Validation.isPinValid("12345a"))
        assertFalse(Validation.isPinValid(""))
    }

    @Test
    fun `поле PIN не заполняется мусором`() {
        assertEquals("123456", "12-34-56".asPinInput())
        assertEquals("123456", "1234567890".asPinInput())
        assertEquals("", "абвгде".asPinInput())
    }

    @Test
    fun `ведущий ноль сохраняется`() {
        // PIN — строка на всём пути до сервера. Превращение в число молча
        // потеряло бы ноль и дало бы неверный вход.
        assertEquals("012345", "012345".asPinInput())
        assertTrue(Validation.isPinValid("012345"))
    }

    @Test
    fun `адрес сервера требует схемы и непустого хоста`() {
        assertTrue(Validation.isServerUrlValid("http://10.0.2.2:8080"))
        assertTrue(Validation.isServerUrlValid("https://terentiy.example"))
        assertFalse(Validation.isServerUrlValid("10.0.2.2:8080"))
        assertFalse(Validation.isServerUrlValid("ftp://10.0.2.2"))
        assertFalse(Validation.isServerUrlValid("https://"))
        assertFalse(Validation.isServerUrlValid(""))
    }

    @Test
    fun `код установки не может быть пустым`() {
        assertFalse(Validation.isInstallationCodeValid(""))
        assertFalse(Validation.isInstallationCodeValid("  "))
        assertTrue(Validation.isInstallationCodeValid("HOME-SETUP-CODE"))
    }

    @Test
    fun `ошибка формы указывает на первое неверное поле сверху вниз`() {
        // Порядок важен: подсказка должна указывать на то поле, до которого
        // пользователь дошёл, а не на последнее в форме.
        assertEquals(
            R.string.validation_server_url,
            Validation.activationError("нет схемы", "", "1", "2"),
        )
        assertEquals(
            R.string.validation_installation_code,
            Validation.activationError("http://host:8080", "", "1", "2"),
        )
        assertEquals(
            R.string.validation_pin_six_digits,
            Validation.activationError("http://host:8080", "CODE-1", "123", "123"),
        )
        assertEquals(
            R.string.validation_pin_mismatch,
            Validation.activationError("http://host:8080", "CODE-1", "123456", "123457"),
        )
    }

    @Test
    fun `корректная форма не даёт ошибки`() {
        assertNull(Validation.activationError("http://10.0.2.2:8080", "CODE-1", "123456", "123456"))
    }
}
