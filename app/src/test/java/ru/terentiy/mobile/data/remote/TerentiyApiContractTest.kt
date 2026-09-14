package ru.terentiy.mobile.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import ru.terentiy.mobile.core.error.AppError
import ru.terentiy.mobile.core.error.ErrorMapper
import ru.terentiy.mobile.core.network.apiCall
import ru.terentiy.mobile.domain.Tri

/**
 * Проверка разбора реальных ответов backend по contracts/stage01-mobile-api.yaml.
 *
 * Тела ответов взяты в той форме, в которой их отдаёт `TerentiyService`, включая
 * плоский формат ошибок `{code, message}` и формат отказа валидации FastAPI.
 */
class TerentiyApiContractTest {

    private lateinit var server: MockWebServer
    private lateinit var api: TerentiyApi
    private lateinit var errorMapper: ErrorMapper

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        errorMapper = ErrorMapper(json)
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TerentiyApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun respond(code: Int, body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    private suspend fun <T> call(block: suspend () -> T): T = apiCall(errorMapper, block)

    @Test
    fun `активация возвращает токен и household`() = runTest {
        respond(201, """{"access_token":"abc","token_type":"bearer","household_id":"house-test-001"}""")

        val token = call {
            api.activate(MobileActivationRequestDto("TEST-INSTALLATION-CODE-001", "device-1", "123456"))
        }

        assertEquals("abc", token.accessToken)
        assertEquals("house-test-001", token.householdId)

        val request = server.takeRequest()
        assertEquals("/api/v1/mobile/activate", request.path)
        val sent = request.body.readUtf8()
        assertTrue(sent.contains("installation_code"))
        assertTrue(sent.contains("device_id"))
    }

    @Test
    fun `неверный код установки превращается в доменную ошибку`() = runTest {
        respond(401, """{"code":"invalid_installation_code","message":"Installation code is invalid"}""")

        val error = runCatching {
            call { api.activate(MobileActivationRequestDto("НЕВЕРНЫЙ", "device-1", "123456")) }
        }.exceptionOrNull()

        assertEquals(AppError.InvalidInstallationCode, error)
    }

    @Test
    fun `повторная активация того же устройства`() = runTest {
        respond(409, """{"code":"device_already_activated","message":"Mobile device is already activated"}""")

        val error = runCatching {
            call { api.activate(MobileActivationRequestDto("TEST-INSTALLATION-CODE-001", "device-1", "123456")) }
        }.exceptionOrNull()

        assertEquals(AppError.DeviceAlreadyActivated, error)
    }

    @Test
    fun `вход по PIN отправляет device_id и pin`() = runTest {
        respond(200, """{"access_token":"xyz","token_type":"bearer","household_id":"house-test-001"}""")

        call { api.login(MobileLoginRequestDto("device-1", "123456")) }

        val request = server.takeRequest()
        assertEquals("/api/v1/mobile/login", request.path)
        assertEquals("""{"device_id":"device-1","pin":"123456"}""", request.body.readUtf8())
    }

    @Test
    fun `неверный PIN`() = runTest {
        respond(401, """{"code":"invalid_pin","message":"Device ID or PIN is invalid"}""")

        val error = runCatching { call { api.login(MobileLoginRequestDto("device-1", "000000")) } }
            .exceptionOrNull()

        assertEquals(AppError.InvalidPin, error)
    }

    @Test
    fun `отказ валидации PIN приходит в формате FastAPI`() = runTest {
        respond(
            422,
            """{"detail":[{"type":"string_pattern_mismatch","loc":["body","pin"],"msg":"String should match pattern"}]}"""
        )

        val error = runCatching { call { api.login(MobileLoginRequestDto("device-1", "12345")) } }
            .exceptionOrNull()

        assertTrue(error is AppError.Validation)
    }

    @Test
    fun `список агентов с полными данными`() = runTest {
        respond(
            200,
            """
            {"items":[{
              "id":"1d2c","household_id":"house-test-001","name":"Ноутбук",
              "camera_id":"camera-room-1","software_version":"0.1.0",
              "created_at":"2026-09-01T00:00:00Z","online":true,
              "reported_at":"2026-09-13T07:00:00Z","agent_online":true,
              "camera_connected":true,"stream_active":true,"ml_ready":true,
              "queue_size":0,"model_version":"yolo26m.pt",
              "last_frame_at":"2026-09-13T06:59:59Z"
            }]}
            """.trimIndent()
        )

        val agents = call { api.agents().items.map { it.toDomain() } }

        assertEquals(1, agents.size)
        assertEquals(Tri.YES, agents.first().cameraConnected)
        assertEquals("Ноутбук", agents.first().name)
    }

    @Test
    fun `агент без heartbeat приходит с null-полями и не роняет разбор`() = runTest {
        respond(
            200,
            """
            {"items":[{
              "id":"1d2c","household_id":"house-test-001","name":"Ноутбук",
              "camera_id":"camera-room-1","software_version":"0.1.0",
              "created_at":"2026-09-01T00:00:00Z","online":false,
              "reported_at":null,"agent_online":null,"camera_connected":null,
              "stream_active":null,"ml_ready":null,"queue_size":null,
              "last_frame_at":null
            }]}
            """.trimIndent()
        )

        val agent = call { api.agents().items.map { it.toDomain() } }.first()

        assertEquals(Tri.UNKNOWN, agent.cameraConnected)
        assertEquals(Tri.UNKNOWN, agent.mlReady)
        assertTrue(agent.neverReported)
    }

    @Test
    fun `новое поле в ответе backend не ломает клиент`() = runTest {
        respond(
            200,
            """
            {"items":[{
              "id":"1d2c","household_id":"house-test-001","name":"Ноутбук",
              "camera_id":"camera-room-1","software_version":"0.1.0",
              "created_at":"2026-09-01T00:00:00Z","online":true,
              "person_present":true,"person_count":2
            }],"server_time":"2026-09-13T07:00:00Z"}
            """.trimIndent()
        )

        val agents = call { api.agents().items.map { it.toDomain() } }
        assertEquals(1, agents.size)
    }

    @Test
    fun `пустой список агентов — валидный ответ`() = runTest {
        respond(200, """{"items":[]}""")
        assertTrue(call { api.agents().items }.isEmpty())
    }

    @Test
    fun `истёкший токен`() = runTest {
        respond(401, """{"code":"unauthorized","message":"Valid bearer token required"}""")

        val error = runCatching { call { api.agents() } }.exceptionOrNull()
        assertEquals(AppError.Unauthorized, error)
    }

    @Test
    fun `кадр разбирается вместе с обоими изображениями`() = runTest {
        val jpeg = Base64.getEncoder().encodeToString(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        respond(
            200,
            """
            {"agent_id":"1d2c","captured_at":"2026-09-13T07:00:00Z",
             "model_name":"UltralyticsYoloPersonDetectionModel","model_version":"yolo26m.pt",
             "detection":{"person_present":true,"person_count":2,"max_confidence":0.91,"inference_time_ms":183.4},
             "motion":{"success":true,"background_ready":true,"motion_present":true,"region_count":2,
                       "changed_area_ratio":0.0312,"processing_time_ms":12.5,"error_code":null},
             "media_type":"image/jpeg","content_base64":"$jpeg","motion_content_base64":"$jpeg"}
            """.trimIndent()
        )

        val image = call { api.latestImage("1d2c").toDomain() }

        assertEquals(2, image.personCount)
        assertEquals(2, image.motionRegionCount)
        assertEquals(3, image.personJpeg.size)
    }

    @Test
    fun `кадра ещё не было`() = runTest {
        respond(404, """{"code":"image_not_available","message":"Agent has not sent an image"}""")

        val error = runCatching { call { api.latestImage("1d2c") } }.exceptionOrNull()
        assertEquals(AppError.ImageNotAvailable, error)
    }

    @Test
    fun `чужой агент`() = runTest {
        respond(404, """{"code":"agent_not_found","message":"Agent not found"}""")

        val error = runCatching { call { api.latestImage("чужой") } }.exceptionOrNull()
        assertEquals(AppError.AgentNotFound, error)
    }

    @Test
    fun `повреждённый JSON не роняет приложение`() = runTest {
        respond(200, """{"items":[{"id":""" )

        val error = runCatching { call { api.agents() } }.exceptionOrNull()
        assertTrue(error is AppError)
    }
}
