package ru.terentiy.mobile.core.error

/**
 * Доменная ошибка приложения.
 *
 * Единственный тип ошибок, который видят репозитории и ViewModel. Технические
 * коды сервера, HTTP-статусы и исключения OkHttp дальше [ErrorMapper] не
 * проходят. Пользователю показывается текст из strings.xml, сопоставленный с
 * типом ошибки: технические коды в интерфейс не попадают.
 *
 * Набор соответствует реализованному контракту 1.0.0 — там, где сервер
 * действительно различает случаи. Проверено по коду `application/services.py`.
 */
sealed class AppError : Exception() {

    /** Код установки не найден в реестре сервера. */
    data object InvalidInstallationCode : AppError()

    /** Это устройство уже активировано; нужен вход по PIN. */
    data object DeviceAlreadyActivated : AppError()

    /**
     * Неверный PIN либо неизвестное устройство — сервер отвечает одним кодом на
     * оба случая, чтобы по ответу нельзя было перебирать `device_id`.
     *
     * Важно: это **не** [Unauthorized]. Неверный PIN сессию не стирает —
     * стирать нечего, вход и так не выполнен.
     */
    data object InvalidPin : AppError()

    /**
     * Сервер не знает предъявленный токен.
     *
     * Истечения не существует: токены бессрочные. Значит `401` на защищённом
     * маршруте означает «токен неизвестен или устройство удалено», и
     * единственная реакция — повторный вход по PIN.
     */
    data object Unauthorized : AppError()

    /** Медиа не найдено: срок хранения истёк либо файл удалён с диска. */
    data object MediaNotFound : AppError()

    /**
     * Ответ медиа-маршрута пришёл с неожиданным типом.
     *
     * Практически это означает, что по введённому адресу отвечает не наш сервер:
     * контракт допускает только `image/jpeg` и `video/mp4`.
     */
    data class MediaUnsupported(val contentType: String?) : AppError()

    /** Событие не найдено в загруженной ленте. Локальная ошибка, не серверная. */
    data object EventNotFound : AppError()

    /** Камера давно не присылала кадров, или сервер не умеет отдавать вид. */
    data object CameraUnavailable : AppError()

    /** Зона не найдена — возможно, её уже удалили. */
    data object ZoneNotFound : AppError()

    /** Зону изменили с другого устройства, пока её редактировали здесь. */
    data object ZoneConflict : AppError()

    /** Зону сейчас проверяет сервер — менять её нельзя. */
    data object ZoneBusy : AppError()

    /** У камеры уже максимум зон. */
    data object ZoneLimit : AppError()

    /**
     * Сервер не принял кадр, на котором нарисована зона: он уже не текущий.
     * Сервер принимает только свежий кадр камеры (`agent_offline_after_seconds`, 30 с).
     */
    data object ZoneFrameOutdated : AppError()

    /** На сервере ещё нет маршрутов зон. */
    data object ZonesUnsupported : AppError()

    /** Зона уже проверена — повторно проверять её сервер не даёт. */
    data object ZoneAlreadyBound : AppError()

    /** Сессия проверки зоны не найдена: её уже нет на сервере. */
    data object ZoneCheckNotFound : AppError()

    /** Проверке зоны нужен сервер с камерой; в показе без сервера её нет. */
    data object ZoneCheckNeedsServer : AppError()

    /** Сеть недоступна, таймаут, неверный адрес сервера. */
    data object Network : AppError()

    /** Тело запроса не прошло проверку. На клиенте должно отсекаться заранее. */
    data class Validation(val detail: String?) : AppError()

    /** Прочий ответ сервера, который клиент не разбирает по существу. */
    data class Server(val code: String?, val status: Int) : AppError()

    /** Всё остальное, включая повреждённое тело ответа. */
    data class Unexpected(val reason: Throwable?) : AppError()
}
