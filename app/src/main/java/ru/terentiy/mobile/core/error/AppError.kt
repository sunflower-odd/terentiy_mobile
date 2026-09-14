package ru.terentiy.mobile.core.error

/**
 * Доменная ошибка приложения.
 *
 * Единственный тип ошибок, который видят репозитории и ViewModel. Технические коды
 * backend, HTTP-статусы и исключения OkHttp дальше [ErrorMapper] не проходят.
 * Пользователю показывается текст из strings.xml, сопоставленный с типом ошибки:
 * технические коды в интерфейс не попадают (stage-01-mobile.md §6).
 */
sealed class AppError : Exception() {

    /** `401 invalid_installation_code` — код установки не найден в реестре. */
    data object InvalidInstallationCode : AppError()

    /** `409 device_already_activated` — этот `device_id` уже привязан. */
    data object DeviceAlreadyActivated : AppError()

    /** `401 invalid_pin` — неверный PIN или неизвестный `device_id`. */
    data object InvalidPin : AppError()

    /** `401 unauthorized` — токен недействителен. Требуется повторный вход по PIN. */
    data object Unauthorized : AppError()

    /** `404 agent_not_found` — агент не принадлежит установке этого устройства. */
    data object AgentNotFound : AppError()

    /** `404 image_not_available` — агент активен, но кадр ещё не публиковал. */
    data object ImageNotAvailable : AppError()

    /** Сеть недоступна, таймаут, неверный адрес сервера. */
    data object Network : AppError()

    /** `422` — тело запроса не прошло проверку. На клиенте должно отсекаться заранее. */
    data class Validation(val detail: String?) : AppError()

    /** Прочий ответ сервера, который клиент не разбирает по существу. */
    data class Server(val code: String?, val status: Int) : AppError()

    /** Всё остальное, включая повреждённое тело ответа. */
    data class Unexpected(val reason: Throwable?) : AppError()
}
