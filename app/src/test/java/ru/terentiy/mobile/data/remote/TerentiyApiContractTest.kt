package ru.terentiy.mobile.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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

/**
 * Проверка мобильных маршрутов на телах той формы, которую описывает
 * `contracts/stage01-mobile-api.yaml`.
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
    fun `список агентов разбирается по контракту 2_0_0-draft`() = runTest {
        respond(
            200,
            """
            {
              "items": [
                {"agent_id": "agent-1", "name": "Гостиная", "camera_id": "camera-1", "online": true},
                {"agent_id": "agent-2", "name": "Спальня", "camera_id": "camera-2", "online": false}
              ]
            }
            """.trimIndent()
        )

        val agents = api.agents().toDomain()

        assertEquals("/api/v1/agents", server.takeRequest().path)
        assertEquals(listOf("agent-1", "agent-2"), agents.map { it.id })
        assertEquals("camera-1", agents[0].cameraId)
        assertEquals(true, agents[0].online)
        assertEquals(false, agents[1].online)
    }

    @Test
    fun `неизвестное поле в ответе не роняет разбор`() = runTest {
        respond(
            200,
            """{"items":[{"agent_id":"agent-1","online":true,"unexpected_field":"что-то новое"}]}""",
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

    @Test
    fun `вид с камеры — по пути камеры агента`() = runTest {
        respond(
            200,
            """
            {"frame_id":"frame-1","media_id":"media-frame-1","captured_at":"2026-10-13T10:00:00Z","width":640,"height":480}
            """.trimIndent(),
        )

        val frame = api.currentFrame("agent-1", "camera-1").toDomain()

        assertEquals("/api/v1/agents/agent-1/cameras/camera-1/current-frame", server.takeRequest().path)
        assertEquals("media-frame-1", frame.mediaId)
        assertEquals(640, frame.width)
    }

    @Test
    fun `камера без кадров отвечает кодом camera_unavailable`() = runTest {
        respond(404, """{"code":"camera_unavailable","message":"No recent frame from this camera"}""")

        val error = runCatching { apiCall(errorMapper) { api.currentFrame("agent-1", "camera-1") } }.exceptionOrNull()

        assertEquals(AppError.CameraUnavailable, error)
    }

    @Test
    fun `зона создаётся телом без лишних полей`() = runTest {
        respond(
            201,
            """
            {"zone_id":"z1","agent_id":"agent-1","camera_id":"camera-1","name":"Дверь",
             "polygon":[[0.1,0.2],[0.5,0.2],[0.5,0.6]],"reference_frame_id":"frame-1","reference_media_id":"media-1",
             "state":"draft","version":1,"created_at":"2026-10-13T10:00:00Z","updated_at":"2026-10-13T10:00:00Z"}
            """.trimIndent(),
        )
        val draft = ru.terentiy.mobile.domain.ZoneDraft(
            name = "Дверь",
            polygon = listOf(
                ru.terentiy.mobile.domain.ZonePoint(0.1f, 0.2f),
                ru.terentiy.mobile.domain.ZonePoint(0.5f, 0.2f),
                ru.terentiy.mobile.domain.ZonePoint(0.5f, 0.6f),
            ),
            referenceFrameId = "frame-1",
        )

        val zone = api.createZone("agent-1", "camera-1", draft.toRequest()).toDomain()

        val request = server.takeRequest()
        assertEquals("/api/v1/agents/agent-1/cameras/camera-1/zones", request.path)
        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(setOf("name", "polygon", "reference_frame_id"), body.keys)
        assertEquals("z1", zone.id)
        assertEquals(ru.terentiy.mobile.domain.ZoneState.DRAFT, zone.state)
    }

    @Test
    fun `конфликт версии зоны`() = runTest {
        respond(409, """{"code":"zone_version_conflict","message":"Zone was changed"}""")

        val error = runCatching {
            apiCall(errorMapper) {
                api.updateZone("agent-1", "camera-1", "z1", ZoneUpdateRequestDto("Дверь", emptyList(), "frame-1", 1))
            }
        }.exceptionOrNull()

        assertEquals(AppError.ZoneConflict, error)
    }

    // Тела — из `SqlAlchemyZoneService._session_json` (terentiy_ai, 2b81111).
    private fun sessionBody(state: String, observation: String = "null", failure: String = "null") = """
        {"binding_session_id":"s1","zone_id":"z1","zone_version":2,"state":"$state",
         "started_at":"2026-11-02T10:00:00Z","expires_at":"2026-11-02T10:01:00Z",
         "progress":{"accepted_observations":1,"required_observations":6,"last_observation":$observation},
         "binding":null,"failure":$failure}
    """.trimIndent()

    @Test
    fun `проверка зоны стартует с версией зоны`() = runTest {
        respond(201, sessionBody("waiting_for_frame"))

        val session = api.startZoneBinding("agent-1", "camera-1", "z1", StartZoneBindingRequestDto(2)).toDomain()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/agents/agent-1/cameras/camera-1/zones/z1/binding-sessions", request.path)
        assertEquals("""{"zone_version":2}""", request.body.readUtf8())
        assertEquals("s1", session.id)
        assertEquals(ru.terentiy.mobile.domain.ZoneBindingState.WAITING_FOR_FRAME, session.state)
        assertNull(session.observation)
    }

    @Test
    fun `опрос проверки отдаёт маркер и решение сервера`() = runTest {
        val observation = """{"captured_at":"2026-11-02T10:00:05Z","frame_id":"f1","media_id":"m1",
            "point":{"x":0.41,"y":0.77},"quality":"full","in_zone":false}"""
        respond(200, sessionBody("collecting", observation))

        val session = api.zoneBinding("agent-1", "camera-1", "z1", "s1").toDomain()

        assertEquals("/api/v1/agents/agent-1/cameras/camera-1/zones/z1/binding-sessions/s1", server.takeRequest().path)
        assertEquals(ru.terentiy.mobile.domain.ZoneBindingState.COLLECTING, session.state)
        assertEquals("m1", session.observation?.mediaId)
        assertEquals(0.41f, session.observation?.point?.x ?: 0f, 0.0001f)
        assertEquals(false, session.observation?.inZone)
    }

    @Test
    fun `неудачная проверка приходит с причиной`() = runTest {
        respond(200, sessionBody("expired", failure = """{"code":"no_person_detected","message":"No person was detected"}"""))

        val session = api.zoneBinding("agent-1", "camera-1", "z1", "s1").toDomain()

        assertTrue(session.state.terminal)
        assertEquals(ru.terentiy.mobile.domain.ZoneBindingFailure.NO_PERSON, session.failure)
    }

    @Test
    fun `отмена проверки — DELETE без тела`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val response = api.cancelZoneBinding("agent-1", "camera-1", "z1", "s1")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/agents/agent-1/cameras/camera-1/zones/z1/binding-sessions/s1", request.path)
        assertTrue(response.isSuccessful)
    }
}
