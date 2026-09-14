package ru.terentiy.mobile.ui.common

import androidx.annotation.StringRes
import ru.terentiy.mobile.R

/**
 * Проверки, выполняемые до обращения к сети.
 *
 * PIN проверяется здесь не ради удобства: backend валидирует его через pydantic,
 * и отказ возвращается в формате `{"detail": [...]}`, отличном от прикладных ошибок.
 * Отсечь заведомо неверный ввод на клиенте дешевле, чем разбирать этот ответ.
 */
object Validation {

    const val PIN_LENGTH = 6

    fun isPinValid(pin: String): Boolean =
        pin.length == PIN_LENGTH && pin.all(Char::isDigit)

    fun isServerUrlValid(url: String): Boolean {
        val trimmed = url.trim()
        return (trimmed.startsWith("http://") || trimmed.startsWith("https://")) &&
            trimmed.length > "https://".length
    }

    @StringRes
    fun pinError(pin: String): Int? =
        if (isPinValid(pin)) null else R.string.validation_pin_six_digits
}

/** Оставляет только цифры и обрезает до длины PIN — поле нельзя заполнить мусором. */
fun String.asPinInput(): String = filter(Char::isDigit).take(Validation.PIN_LENGTH)
