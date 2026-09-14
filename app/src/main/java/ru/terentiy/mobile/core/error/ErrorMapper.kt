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
 * Backend отдаёт ошибки в двух разных форматах, и оба нужно пережить:
 *
 * 1. Прикладная ошибка через глобальный обработчик FastAPI — `{"code": ..., "message": ...}`.
 * 2. Отказ валидации pydantic (например, `pin` не соответствует `^\d{6}$`) — обработчик
 *    не вызывается вовсе, и приходит собственный формат FastAPI `{"detail": [...]}`.
 *
 * Третий случай — тело, которое не разбирается ни как то, ни как другое: пустой ответ,
 * HTML от постороннего сервера по ошибочному адресу, обрыв соединения на середине.
 * Он не должен приводить к падению, поэтому разбор целиком защищён.
 *
 * Расхождение форматов зафиксировано как запрос к backend: docs/backlog-mobile.md §6.
 */
@Singleton
class ErrorMapper @Inject constructor(private val json: Json) {

    fun map(throwable: Throwable): AppError = when (throwable) {
        is AppError -> throwable
        is HttpException -> fromHttp(throwable)
        is IOException -> AppError.Network
        else -> AppError.Unexpected(throwable)
    }

    private fun fromHttp(exception: HttpException): AppError {
        val status = exception.code()
        val body = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
        val parsed = body?.let(::parse)

        parsed?.code?.let { code -> byCode(code)?.let { return it } }

        Timber.w("Нераспознанная ошибка backend | status=%s | code=%s", status, parsed?.code)
        return when (status) {
            401 -> AppError.Unauthorized
            404 -> AppError.AgentNotFound
            409 -> AppError.DeviceAlreadyActivated
            422 -> AppError.Validation(parsed?.detail)
            else -> AppError.Server(parsed?.code, status)
        }
    }

    private fun byCode(code: String): AppError? = when (code) {
        "invalid_installation_code" -> AppError.InvalidInstallationCode
        "device_already_activated" -> AppError.DeviceAlreadyActivated
        "invalid_pin" -> AppError.InvalidPin
        "unauthorized", "forbidden" -> AppError.Unauthorized
        "agent_not_found" -> AppError.AgentNotFound
        "image_not_available" -> AppError.ImageNotAvailable
        "invalid_activation", "invalid_status", "invalid_image" -> AppError.Validation(null)
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
