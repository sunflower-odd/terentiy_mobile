package ru.terentiy.mobile.core.error

import androidx.annotation.StringRes
import ru.terentiy.mobile.R

/**
 * Сопоставление доменной ошибки с текстом для пользователя.
 *
 * Единственное место, где [AppError] превращается в то, что видно на экране.
 * Технические коды, HTTP-статусы и сообщения backend сюда не просачиваются.
 */
@StringRes
fun AppError.messageRes(): Int = when (this) {
    AppError.InvalidInstallationCode -> R.string.error_invalid_installation_code
    AppError.DeviceAlreadyActivated -> R.string.error_device_already_activated
    AppError.InvalidPin -> R.string.error_invalid_pin
    AppError.Unauthorized -> R.string.error_unauthorized
    AppError.AgentNotFound -> R.string.error_agent_not_found
    AppError.ImageNotAvailable -> R.string.error_image_not_available
    is AppError.Validation -> R.string.error_validation
    is AppError.Server -> R.string.error_server
    AppError.Network -> R.string.error_network
    is AppError.Unexpected -> R.string.error_unknown
}
