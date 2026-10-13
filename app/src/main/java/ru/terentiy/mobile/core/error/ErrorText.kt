package ru.terentiy.mobile.core.error

import androidx.annotation.StringRes
import ru.terentiy.mobile.R

/**
 * Текст ошибки для пользователя.
 *
 * Технические коды и статусы в интерфейс не попадают: [AppError.Server] и
 * [AppError.Unexpected] получают общие формулировки, а подробности уходят в
 * журнал Timber.
 */
@StringRes
fun AppError.messageRes(): Int = when (this) {
    AppError.InvalidInstallationCode -> R.string.error_invalid_installation_code
    AppError.DeviceAlreadyActivated -> R.string.error_device_already_activated
    AppError.InvalidPin -> R.string.error_invalid_pin
    AppError.Unauthorized -> R.string.error_unauthorized
    AppError.MediaNotFound -> R.string.error_media_not_found
    is AppError.MediaUnsupported -> R.string.error_media_unsupported
    AppError.EventNotFound -> R.string.error_event_not_found
    AppError.CameraUnavailable -> R.string.error_camera_unavailable
    AppError.ZoneNotFound -> R.string.error_zone_not_found
    AppError.ZoneConflict -> R.string.error_zone_conflict
    AppError.ZoneBusy -> R.string.error_zone_busy
    AppError.ZoneLimit -> R.string.error_zone_limit
    AppError.ZonesUnsupported -> R.string.error_zones_unsupported
    AppError.Network -> R.string.error_network
    is AppError.Validation -> R.string.error_validation
    is AppError.Server -> R.string.error_server
    is AppError.Unexpected -> R.string.error_unknown
}
