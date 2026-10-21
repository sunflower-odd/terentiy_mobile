package ru.terentiy.mobile.core.error

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Сервер отдаёт ошибки в двух форматах, и оба обязаны разбираться: прикладной
 * `{code, message}` и собственный формат валидации FastAPI `{detail: [...]}`
 * **без поля code**. Третий случай — тело, которое не является ни тем, ни
 * другим, — не должен приводить к падению.
 *
 * Коды выписаны из `terentiy_ai/server/src/terentiy_server/application/services.py`.
 */
class ErrorMapperTest {

    private val mapper = ErrorMapper(Json { ignoreUnknownKeys = true })

    private fun httpError(status: Int, body: String): HttpException = HttpException(
        Response.error<Any>(status, body.toResponseBody("application/json".toMediaType()))
    )

    @Test
    fun `код установки не подошёл`() {
        assertEquals(
            AppError.InvalidInstallationCode,
            mapper.map(httpError(401, """{"code":"invalid_installation_code","message":"Installation code is invalid"}""")),
        )
    }

    @Test
    fun `устройство уже подключено`() {
        assertEquals(
            AppError.DeviceAlreadyActivated,
            mapper.map(httpError(409, """{"code":"device_already_activated","message":"Device is already activated"}""")),
        )
    }

    @Test
    fun `неверный PIN — это не потеря сессии`() {
        // Разница решает, стирать ли сохранённый токен: неверный PIN сессию не
        // трогает, неизвестный токен — стирает.
        val error = mapper.map(httpError(401, """{"code":"invalid_pin","message":"Device ID or PIN is invalid"}"""))
        assertEquals(AppError.InvalidPin, error)
        assertTrue(error != AppError.Unauthorized)
    }

    @Test
    fun `неизвестный токен требует повторного входа`() {
        assertEquals(
            AppError.Unauthorized,
            mapper.map(httpError(401, """{"code":"unauthorized","message":"Valid bearer token required"}""")),
        )
    }

    @Test
    fun `медиа не найдено`() {
        assertEquals(
            AppError.MediaNotFound,
            mapper.map(httpError(404, """{"code":"media_not_found","message":"Media not found"}""")),
        )
    }

    @Test
    fun `устаревший кадр зоны — отдельная ошибка, а не общая валидация`() {
        assertEquals(
            AppError.ZoneFrameOutdated,
            mapper.map(httpError(422, """{"code":"invalid_reference_frame","message":"reference_frame_id is not a current frame of this camera"}""")),
        )
    }

    @Test
    fun `прикладной отказ валидации приходит с кодом`() {
        assertTrue(
            mapper.map(httpError(422, """{"code":"invalid_activation","message":"PIN must contain six digits"}"""))
                is AppError.Validation
        )
    }

    @Test
    fun `отказ валидации pydantic приходит в собственном формате FastAPI без кода`() {
        val body = """{"detail":[{"loc":["body","pin"],"msg":"String should match pattern","type":"string_pattern_mismatch"}]}"""
        assertTrue(mapper.map(httpError(422, body)) is AppError.Validation)
    }

    @Test
    fun `статус без кода разбирается по статусу`() {
        assertEquals(AppError.Unauthorized, mapper.map(httpError(401, "")))
        // Единственный маршрут, дающий 404, — медиа: защищённые маршруты агентов
        // и событий отвечают только 401.
        assertEquals(AppError.MediaNotFound, mapper.map(httpError(404, "")))
        assertEquals(AppError.DeviceAlreadyActivated, mapper.map(httpError(409, "")))
    }

    @Test
    fun `неизвестный код не роняет разбор`() {
        assertEquals(AppError.Server("something_new", 500), mapper.map(httpError(500, """{"code":"something_new"}""")))
    }

    @Test
    fun `тело, не являющееся JSON, не роняет разбор`() {
        assertEquals(AppError.Server(null, 502), mapper.map(httpError(502, "<html><body>Bad Gateway</body></html>")))
    }

    @Test
    fun `пустое тело не роняет разбор`() {
        assertEquals(AppError.Server(null, 500), mapper.map(httpError(500, "")))
    }

    @Test
    fun `ответ, разобранный вручную, проходит тот же путь`() {
        // Медиа читается без конвертера Retrofit, поэтому у маппера есть второй вход.
        assertEquals(
            AppError.MediaNotFound,
            mapper.fromResponse(404, """{"code":"media_not_found","message":"Media not found"}"""),
        )
        assertEquals(AppError.Unauthorized, mapper.fromResponse(401, null))
    }

    @Test
    fun `таймаут и обрыв связи — это отсутствие сети`() {
        assertEquals(AppError.Network, mapper.map(SocketTimeoutException("timeout")))
        assertEquals(AppError.Network, mapper.map(IOException("connection reset")))
    }

    @Test
    fun `доменная ошибка проходит насквозь без изменений`() {
        assertEquals(AppError.InvalidPin, mapper.map(AppError.InvalidPin))
    }

    @Test
    fun `всё прочее становится неожиданной ошибкой`() {
        val cause = IllegalStateException("что-то пошло не так")
        assertEquals(AppError.Unexpected(cause), mapper.map(cause))
    }
}
