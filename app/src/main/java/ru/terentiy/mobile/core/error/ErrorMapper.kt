package ru.terentiy.mobile.core.error

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException
import timber.log.Timber

/**
 * Превращает любое исключение транспортного слоя в [AppError].
 *
 * Сервер отдаёт ошибки в двух разных форматах, и оба нужно пережить:
 *
 * 1. Прикладная ошибка через единственный обработчик FastAPI — `{"code", "message"}`.
 * 2. Отказ валидации pydantic — обработчик не вызывается вовсе, приходит
 *    собственный формат FastAPI `{"detail": [...]}` **без поля `code`**. На одном
 *    и том же маршруте `mobile/activate` возможны оба: PIN неверной формы
 *    отсекает pydantic, а пустой `device_id` — прикладная проверка.
 *
 * Третий случай — тело, которое не разбирается ни как то, ни как другое: пустой
 * ответ, HTML от постороннего сервера по ошибочному адресу, обрыв соединения на
 * середине. Он не должен приводить к падению, поэтому разбор целиком защищён.
 */
@Singleton
class ErrorMapper @Inject constructor(private val json: Json) {

    fun map(throwable: Throwable): AppError = when (throwable) {
        is AppError -> throwable
        is HttpException -> fromHttp(throwable.code(), throwable.errorBodyString())
        is IOException -> AppError.Network
        else -> AppError.Unexpected(throwable)
    }

    /** Для ответов, разобранных вручную: медиа читается без Retrofit-конвертера. */
    fun fromResponse(status: Int, body: String?): AppError = fromHttp(status, body)

    private fun HttpException.errorBodyString(): String? =
        runCatching { response()?.errorBody()?.string() }.getOrNull()

    private fun fromHttp(status: Int, body: String?): AppError {
        val parsed = body?.let(::parse)

        parsed?.code?.let { code -> byCode(code)?.let { return it } }

        Timber.w("Нераспознанная ошибка сервера | status=%s | code=%s", status, parsed?.code)
        return when (status) {
            401 -> AppError.Unauthorized
            // Единственный маршрут, дающий 404, — медиа. Остальные защищённые
            // маршруты отвечают только 401.
            404 -> AppError.MediaNotFound
            409 -> AppError.DeviceAlreadyActivated
            422 -> AppError.Validation(parsed?.detail)
            else -> AppError.Server(parsed?.code, status)
        }
    }

    /** Коды выписаны из модулей `server/src/terentiy_server/application` (terentiy_ai, коммит 712afd2). */
    private fun byCode(code: String): AppError? = when (code) {
        "invalid_installation_code" -> AppError.InvalidInstallationCode
        "device_already_activated" -> AppError.DeviceAlreadyActivated
        "invalid_pin" -> AppError.InvalidPin
        "unauthorized", "forbidden" -> AppError.Unauthorized
        "media_not_found" -> AppError.MediaNotFound
        "invalid_activation" -> AppError.Validation(null)
        // Критические зоны, mobile-server 2.0.0-draft. Чужая или удалённая
        // камера для пользователя означает то же самое: кадров с неё нет.
        "camera_unavailable", "agent_not_found" -> AppError.CameraUnavailable
        "zone_not_found" -> AppError.ZoneNotFound
        "zone_version_conflict" -> AppError.ZoneConflict
        "zone_binding_in_progress" -> AppError.ZoneBusy
        "zone_already_bound" -> AppError.ZoneAlreadyBound
        "binding_session_not_found" -> AppError.ZoneCheckNotFound
        "zone_limit_reached" -> AppError.ZoneLimit
        "invalid_reference_frame" -> AppError.ZoneFrameOutdated
        "invalid_zone" -> AppError.Validation(null)
        else -> null
    }

    /**
     * Достаёт из тела ответа код прикладной ошибки либо текст отказа валидации.
     * Любое неожиданное тело даёт пустой результат, а не исключение.
     */
    internal fun parse(body: String): ParsedError = runCatching {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return@runCatching ParsedError()
        val code = (root["code"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val detail = root["detail"]?.let { element ->
            if (element is JsonPrimitive) element.content else element.toString()
        }
        ParsedError(code = code, detail = detail)
    }.getOrElse { ParsedError() }

    internal data class ParsedError(val code: String? = null, val detail: String? = null)
}
