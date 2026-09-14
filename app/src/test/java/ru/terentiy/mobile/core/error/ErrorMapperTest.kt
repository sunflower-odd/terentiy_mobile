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
 * Backend отдаёт ошибки в двух разных форматах, и оба обязаны разбираться.
 * Третий случай — тело, которое не является ни тем, ни другим.
 */
class ErrorMapperTest {

    private val mapper = ErrorMapper(Json { ignoreUnknownKeys = true })

    private fun httpError(status: Int, body: String): HttpException = HttpException(
        Response.error<Any>(status, body.toResponseBody("application/json".toMediaType()))
    )

    @Test
    fun `прикладная ошибка backend разбирается по коду`() {
        val error = mapper.map(
            httpError(401, """{"code":"invalid_installation_code","message":"Installation code is invalid"}""")
        )
        assertEquals(AppError.InvalidInstallationCode, error)
    }

    @Test
    fun `повторная активация устройства`() {
        val error = mapper.map(
            httpError(409, """{"code":"device_already_activated","message":"Mobile device is already activated"}""")
        )
        assertEquals(AppError.DeviceAlreadyActivated, error)
    }

    @Test
    fun `неверный PIN`() {
        val error = mapper.map(httpError(401, """{"code":"invalid_pin","message":"Device ID or PIN is invalid"}"""))
        assertEquals(AppError.InvalidPin, error)
    }

    @Test
    fun `недействительный токен`() {
        val error = mapper.map(httpError(401, """{"code":"unauthorized","message":"Valid bearer token required"}"""))
        assertEquals(AppError.Unauthorized, error)
    }

    @Test
    fun `кадра ещё нет`() {
        val error = mapper.map(
            httpError(404, """{"code":"image_not_available","message":"Agent has not sent an image"}""")
        )
        assertEquals(AppError.ImageNotAvailable, error)
    }

    @Test
    fun `отказ валидации pydantic приходит в собственном формате FastAPI`() {
        val body = """{"detail":[{"loc":["body","pin"],"msg":"String should match pattern","type":"string_pattern_mismatch"}]}"""
        val error = mapper.map(httpError(422, body))
        assertTrue(error is AppError.Validation)
    }

    @Test
    fun `неизвестный код прикладной ошибки не роняет разбор`() {
        val error = mapper.map(httpError(500, """{"code":"something_new","message":"..."}"""))
        assertEquals(AppError.Server("something_new", 500), error)
    }

    @Test
    fun `тело, не являющееся JSON, не роняет разбор`() {
        val error = mapper.map(httpError(502, "<html><body>Bad Gateway</body></html>"))
        assertEquals(AppError.Server(null, 502), error)
    }

    @Test
    fun `пустое тело не роняет разбор`() {
        val error = mapper.map(httpError(500, ""))
        assertEquals(AppError.Server(null, 500), error)
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
