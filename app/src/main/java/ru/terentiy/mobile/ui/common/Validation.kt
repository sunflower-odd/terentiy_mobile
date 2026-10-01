package ru.terentiy.mobile.ui.common

import androidx.annotation.StringRes
import ru.terentiy.mobile.R

/**
 * Проверки, выполняемые до обращения к сети.
 *
 * PIN проверяется здесь не ради удобства: сервер валидирует его через pydantic по
 * схеме `^\d{6}$`, и отказ возвращается в формате `{"detail": [...]}`, отличном
 * от прикладных ошибок. Отсечь заведомо неверный ввод на клиенте дешевле, чем
 * разбирать этот ответ.
 */
object Validation {

    const val PIN_LENGTH = 6
    const val CODE_MIN_LENGTH = 4

    fun isPinValid(pin: String): Boolean =
        pin.length == PIN_LENGTH && pin.all(Char::isDigit)

    fun isInstallationCodeValid(code: String): Boolean = code.trim().length >= CODE_MIN_LENGTH

    fun isServerUrlValid(url: String): Boolean {
        val trimmed = url.trim()
        return (trimmed.startsWith("http://") || trimmed.startsWith("https://")) &&
            trimmed.length > "https://".length
    }

    @StringRes
    fun pinError(pin: String): Int? =
        if (isPinValid(pin)) null else R.string.validation_pin_six_digits

    /**
     * Первая ошибка формы подключения, сверху вниз.
     *
     * Порядок важен: подсказка должна указывать на то поле, до которого
     * пользователь дошёл, а не на последнее в форме.
     */
    @StringRes
    fun activationError(
        serverUrl: String,
        installationCode: String,
        pin: String,
        pinRepeat: String,
    ): Int? = when {
        !isServerUrlValid(serverUrl) -> R.string.validation_server_url
        !isInstallationCodeValid(installationCode) -> R.string.validation_installation_code
        !isPinValid(pin) -> R.string.validation_pin_six_digits
        pin != pinRepeat -> R.string.validation_pin_mismatch
        else -> null
    }
}

/** Оставляет только цифры и обрезает до длины PIN — поле нельзя заполнить мусором. */
fun String.asPinInput(): String = filter(Char::isDigit).take(Validation.PIN_LENGTH)
