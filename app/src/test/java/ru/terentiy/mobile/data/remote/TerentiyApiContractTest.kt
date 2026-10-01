package ru.terentiy.mobile.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import ru.terentiy.mobile.core.error.AppError
import ru.terentiy.mobile.core.error.ErrorMapper
import ru.terentiy.mobile.core.network.apiCall
import ru.terentiy.mobile.domain.Tri

/**
 * Проверка пяти мобильных маршрутов на телах той формы, которую описывает
 * `contracts/stage01-mobile-api.yaml` версии 1.0.0.
 *
 * Тела взяты из контракта и из тестов сервера, а не придуманы: сверять клиент с
 * живым сервером на каждой правке дорого, и эти тесты — основная защита от
 * расхождения.
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
    fun tearDown() = server.shutdown()

    private fun respond(code: Int, body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    private val tokenBody = """
        {"access_token":"opaque-token","token_type":"bearer","household_id":"house-1"}
    """.trimIndent()

    @Test
    fun `проверка доступности сервера`() = runTest {
        respond(200, """{"status":"ok","database":"ok"}""")

        val response = api.health()

        assertEquals("/health", server.takeRequest().path)
        assertEquals("ok", response.body()?.database)
    }

    @Test
    fun `сервер отвечает, но база недоступна`() = runTest {
        respond(503, """{"status":"degraded","database":"unavailable"}""")

        val response = api.health()

        assertEquals(503, response.code())
    }

    @Test
    fun `активация отправляет поля в snake_case и получает токен`() = runTest {
        respond(201, tokenBody)

        val session = api.activate(
            MobileActivationRequestDto(
                installationCode = "HOME-SETUP-CODE",
                deviceId = "android-7f3a3b15",
                pin = "012345",
            )
        ).toDomain()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/mobile/activate", request.path)

        val sent = request.body.readUtf8()
        assertTrue(sent.contains("\"installation_code\":\"HOME-SETUP-CODE\""))
        assertTrue(sent.contains("\"device_id\":\"android-7f3a3b15\""))
        // PIN обязан уйти строкой: как число он потерял бы ведущий ноль, и вход
        // перестал бы работать у всех, чей PIN начинается с нуля.
        assertTrue(sent.contains("\"pin\":\"012345\""))

        assertEquals("opaque-token", session.accessToken)
        assertEquals("house-1", session.householdId)
    }

    @Test
    fun `повторная активация того же устройства отклоняется`() = runTest {
        respond(409, """{"code":"device_already_activated","message":"Mobile device is already activated"}""")

        val error = runCatching {
            apiCall(errorMapper) {
                api.activate(MobileActivationRequestDto("CODE", "device", "123456"))
            }
        }.exceptionOrNull()

        assertEquals(AppError.DeviceAlreadyActivated, error)
    }

    @Test
    fun `неверный PIN при входе не равен потере сессии`() = runTest {
        respond(401, """{"code":"invalid_pin","message":"Device ID or PIN is invalid"}""")

        val error = runCatching {
            apiCall(errorMapper) { api.login(MobileLoginRequestDto("device", "654321")) }
        }.exceptionOrNull()

        assertEquals(AppError.InvalidPin, error)
        assertEquals("/api/v1/mobile/login", server.takeRequest().path)
    }

    @Test
    fun `список агентов разбирается, включая полностью неизвестное состояние`() = runTest {
        respond(
            200,
            """
            {
              "items": [
                {
                  "id": "agent-1",
                  "household_id": "house-1",
                  "name": "Гостиная",
                  "camera_id": "camera-1",
                  "software_version": "1.0.0",
                  "created_at": "2026-09-20T10:00:00Z",
                  "reported_at": "2026-09-30T12:00:00Z",
                  "agent_online": true,
                  "camera_connected": true,
                  "stream_active": true,
                  "queue_size": 0,
                  "last_frame_at": "2026-09-30T11:59:58Z",
                  "online": true
                },
                {
                  "id": "agent-2",
                  "household_id": "house-1",
                  "name": "Спальня",
                  "camera_id": "camera-2",
                  "software_version": "1.0.0",
                  "created_at": "2026-09-21T10:00:00Z",
                  "reported_at": null,
                  "agent_online": null,
                  "camera_connected": null,
                  "stream_active": null,
                  "queue_size": null,
                  "last_frame_at": null,
                  "online": false
                }
              ]
            }
            """.trimIndent()
        )

        val agents = api.agents().toDomain()

        assertEquals("/api/v1/agents", server.takeRequest().path)
        assertEquals(2, agents.size)
        assertEquals(Tri.YES, agents[0].cameraConnected)
        // Явный null обязан остаться null, несмотря на coerceInputValues: иначе
        // «данных нет» превратилось бы в «камера не работает».
        assertEquals(Tri.UNKNOWN, agents[1].cameraConnected)
        assertEquals(Tri.UNKNOWN, agents[1].agentOnline)
        assertNull(agents[1].queueSize)
        assertNull(agents[1].reportedAt)
        assertEquals(false, agents[1].online)
    }

    @Test
    fun `неизвестное поле в ответе не роняет разбор`() = runTest {
        respond(
            200,
            """{"items":[{"id":"agent-1","online":true,"unexpected_field":"что-то новое"}]}""",
        )

        assertEquals(1, api.agents().toDomain().size)
    }

    @Test
    fun `недействительный токен требует повторного входа`() = runTest {
        respond(401, """{"code":"unauthorized","message":"Valid bearer token required"}""")

        val error = runCatching { apiCall(errorMapper) { api.agents() } }.exceptionOrNull()

        assertEquals(AppError.Unauthorized, error)
    }

    @Test
    fun `события приходят от новых к старым и фильтруются по агенту`() = runTest {
        respond(
            200,
            """
            {
              "items": [
                {
                  "id": "event-2",
                  "agent_id": "agent-1",
                  "frame_id": "frame-2",
                  "frame_media_id": "media-frame-2",
                  "captured_at": "2026-09-30T11:58:00Z",
                  "status": "confirmed",
                  "analysis": {"fall": {"detected": true, "confirmation_votes": 2, "confirmation_votes_required": 1}},
                  "clip_media_id": "media-clip-2",
                  "created_at": "2026-09-30T11:58:18Z"
                },
                {
                  "id": "event-1",
                  "agent_id": "agent-1",
                  "frame_id": "frame-1",
                  "frame_media_id": "media-frame-1",
                  "captured_at": "2026-09-29T21:30:00Z",
                  "status": "confirmed",
                  "analysis": {},
                  "clip_media_id": "media-clip-1",
                  "created_at": "2026-09-29T21:30:20Z"
                }
              ]
            }
            """.trimIndent()
        )

        val events = api.events(agentId = "agent-1").toDomain()

        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/api/v1/events?"))
        assertTrue(request.path!!.contains("agent_id=agent-1"))
        assertEquals(listOf("event-2", "event-1"), events.map { it.id })
        assertEquals(2, events[0].analysis?.confirmationVotes)
        assertNull(events[1].analysis)
    }

    @Test
    fun `фильтр по агенту не уходит, если его нет`() = runTest {
        respond(200, """{"items":[]}""")

        assertTrue(api.events().toDomain().isEmpty())
        assertEquals("/api/v1/events", server.takeRequest().path)
    }

    @Test
    fun `кадр приходит как JPEG`() = runTest {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x01)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/jpeg")
                .setBody(Buffer().write(bytes))
        )

        val response = api.media("media-frame-2")

        assertEquals("/api/v1/media/media-frame-2", server.takeRequest().path)
        assertEquals("image/jpeg", response.body()?.contentType()?.let { "${it.type}/${it.subtype}" })
        assertEquals(bytes.size, response.body()?.bytes()?.size)
    }

    @Test
    fun `клип приходит как MP4`() = runTest {
        val bytes = ByteArray(256) { it.toByte() }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/mp4")
                .setBody(Buffer().write(bytes))
        )

        val response = api.media("media-clip-2")

        assertEquals("video/mp4", response.body()?.contentType()?.let { "${it.type}/${it.subtype}" })
        // 256L, а не 256: contentLength возвращает Long, и сравнение с Int ушло бы
        // в перегрузку assertEquals(Object, Object), где Integer никогда не равен Long.
        assertEquals(256L, response.body()?.contentLength())
    }

    @Test
    fun `удалённый клип отвечает четыреста четыре`() = runTest {
        respond(404, """{"code":"media_not_found","message":"Media not found"}""")

        val response = api.media("media-clip-gone")
        val error = errorMapper.fromResponse(response.code(), response.errorBody()?.string())

        assertEquals(AppError.MediaNotFound, error)
    }
}
