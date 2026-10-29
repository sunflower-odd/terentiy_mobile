package ru.terentiy.mobile.data.remote

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.terentiy.mobile.domain.EventStatus

/**
 * Граница между формой ответа сервера и доменной моделью.
 *
 * Отдельное внимание — полю `analysis`: в контракте это свободный объект, его
 * форма меняется вместе с правилами распознавания, и неожиданное содержимое
 * обязано давать `null`, а не ронять весь список событий.
 */
class MappersTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun analysis(raw: String): JsonObject = json.parseToJsonElement(raw) as JsonObject

    @Test
    fun `токен переносится вместе с идентификатором дома`() {
        val session = MobileTokenDto(
            accessToken = "opaque-token",
            householdId = "house-1",
        ).toDomain()

        assertEquals("opaque-token", session.accessToken)
        assertEquals("house-1", session.householdId)
    }

    @Test
    fun `агент разбирается, свежесть кадра по умолчанию не подтверждена`() {
        val agent = AgentDto(id = "agent-1", name = "Гостиная", cameraId = "camera-1", online = true).toDomain()

        assertEquals("agent-1", agent.id)
        assertEquals("Гостиная", agent.name)
        assertEquals("camera-1", agent.cameraId)
        assertEquals(true, agent.online)
        // Свежесть кадра проверяет репозиторий отдельным запросом: пока он её
        // не подтвердил, «наблюдение работает» показывать нельзя.
        assertEquals(false, agent.hasFreshFrame)
    }

    @Test
    fun `событие разбирается вместе с разбором правил`() {
        val event = FallEventDto(
            id = "event-1",
            agentId = "agent-1",
            frameId = "frame-1",
            frameMediaId = "media-frame-1",
            clipMediaId = "media-clip-1",
            capturedAt = "2026-09-30T11:58:00Z",
            createdAt = "2026-09-30T11:58:18Z",
            status = "confirmed",
            analysis = analysis(
                """
                {
                  "captured_at": "2026-09-30T11:58:00Z",
                  "ml": {"person_model": "yolo26m", "pose_model": "yolo26n-pose"},
                  "people": [{"bbox_xyxy": [1.0, 2.0, 3.0, 4.0], "confidence": 0.91}],
                  "poses": [{"track_id": 3, "keypoints": [{"name": "nose", "x": 1.0, "y": 2.0}]}],
                  "fall": {
                    "detected": true,
                    "status": "fall_confirmed",
                    "track_id": 3,
                    "candidate_rules": ["aspect_ratio=1.400"],
                    "confirmation_rules": ["angular_speed=118deg/s"],
                    "candidate_votes": 1,
                    "candidate_votes_required": 1,
                    "confirmation_votes": 2,
                    "confirmation_votes_required": 1
                  },
                  "processing_time_ms": 84.219
                }
                """.trimIndent()
            ),
        ).toDomain()

        assertNotNull(event)
        assertEquals(EventStatus.CONFIRMED, event!!.status)
        assertEquals("media-frame-1", event.frameMediaId)
        assertEquals("media-clip-1", event.clipMediaId)
        assertEquals(Instant.parse("2026-09-30T11:58:00Z"), event.capturedAt)

        val fall = event.analysis!!
        assertEquals(3, fall.trackId)
        assertEquals(listOf("aspect_ratio=1.400"), fall.candidateRules)
        assertEquals(listOf("angular_speed=118deg/s"), fall.confirmationRules)
        assertEquals(2, fall.confirmationVotes)
        assertEquals(1, fall.confirmationVotesRequired)
        assertEquals(84.219, fall.processingTimeMs!!, 0.001)
    }

    @Test
    fun `analysis неожиданной формы не роняет событие`() {
        // Самый важный тест на прямую совместимость: правила на сервере меняются,
        // и приложение не должно переставать показывать события.
        val strange = FallEventDto(
            id = "event-2",
            clipMediaId = "media-clip-2",
            analysis = analysis("""{"fall": "не объект", "unexpected": [1, 2, 3]}"""),
        ).toDomain()

        assertNotNull(strange)
        assertNull(strange!!.analysis)

        val empty = FallEventDto(id = "event-3", clipMediaId = "clip-3", analysis = analysis("{}")).toDomain()
        assertNotNull(empty)
        assertNull(empty!!.analysis)

        val missing = FallEventDto(id = "event-4", clipMediaId = "clip-4", analysis = null).toDomain()
        assertNotNull(missing)
        assertNull(missing!!.analysis)
    }

    @Test
    fun `неизвестное состояние события не роняет разбор`() {
        val event = FallEventDto(id = "e", clipMediaId = "c", status = "suspected").toDomain()
        assertEquals(EventStatus.OTHER, event!!.status)
    }

    @Test
    fun `событие без клипа отбрасывается`() {
        // По контракту такого быть не может: сервер отдаёт только события с
        // загруженным клипом. Но запись с пустым идентификатором увела бы
        // карточку за медиа в никуда.
        assertNull(FallEventDto(id = "e1", clipMediaId = null).toDomain())
        assertNull(FallEventDto(id = "e2", clipMediaId = "  ").toDomain())

        val page = FallEventListDto(
            items = listOf(
                FallEventDto(id = "good", clipMediaId = "clip-1"),
                FallEventDto(id = "bad", clipMediaId = null),
            )
        ).toDomain()
        assertEquals(listOf("good"), page.map { it.id })
    }

    @Test
    fun `время принимается в трёх форматах`() {
        assertEquals(Instant.parse("2026-09-30T10:00:00Z"), "2026-09-30T10:00:00Z".toInstantOrNull())
        assertEquals(Instant.parse("2026-09-30T07:00:00Z"), "2026-09-30T10:00:00+03:00".toInstantOrNull())
        // Без смещения значение трактуется как UTC: так его отдаёт агент.
        assertEquals(Instant.parse("2026-09-30T10:00:00Z"), "2026-09-30T10:00:00".toInstantOrNull())
    }

    @Test
    fun `неразбираемое время даёт null, но не ломает состояние`() {
        assertNull("вчера вечером".toInstantOrNull())
        assertNull("".toInstantOrNull())
        assertNull(null.toInstantOrNull())

    }
}
