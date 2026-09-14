package ru.terentiy.mobile.data.remote

import java.time.Instant
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.terentiy.mobile.domain.Tri

class MappersTest {

    private fun dto(
        agentOnline: Boolean? = true,
        cameraConnected: Boolean? = true,
        streamActive: Boolean? = true,
        mlReady: Boolean? = true,
        reportedAt: String? = "2026-09-13T07:00:00Z",
        lastFrameAt: String? = "2026-09-13T07:00:01Z",
    ) = AgentStatusDto(
        id = "e2c1",
        householdId = "house-test-001",
        name = "Ноутбук",
        cameraId = "camera-room-1",
        softwareVersion = "0.1.0",
        createdAt = "2026-09-01T00:00:00Z",
        online = true,
        reportedAt = reportedAt,
        agentOnline = agentOnline,
        cameraConnected = cameraConnected,
        streamActive = streamActive,
        mlReady = mlReady,
        queueSize = 0,
        modelVersion = "yolo26m.pt",
        lastFrameAt = lastFrameAt,
    )

    @Test
    fun `отсутствующие флаги становятся UNKNOWN`() {
        val agent = dto(
            agentOnline = null,
            cameraConnected = null,
            streamActive = null,
            mlReady = null,
        ).toDomain()

        assertEquals(Tri.UNKNOWN, agent.agentOnline)
        assertEquals(Tri.UNKNOWN, agent.cameraConnected)
        assertEquals(Tri.UNKNOWN, agent.streamActive)
        assertEquals(Tri.UNKNOWN, agent.mlReady)
    }

    @Test
    fun `false остаётся NO и не смешивается с неизвестностью`() {
        val agent = dto(cameraConnected = false).toDomain()
        assertEquals(Tri.NO, agent.cameraConnected)
    }

    @Test
    fun `агент без heartbeat помечается как никогда не выходивший на связь`() {
        val agent = dto(reportedAt = null, lastFrameAt = null).toDomain()
        assertTrue(agent.neverReported)
        assertNull(agent.lastFrameAt)
    }

    @Test
    fun `время в UTC разбирается`() {
        assertEquals(Instant.parse("2026-09-13T07:00:00Z"), "2026-09-13T07:00:00Z".toInstantOrNull())
    }

    @Test
    fun `время со смещением приводится к UTC`() {
        assertEquals(
            Instant.parse("2026-09-13T07:00:00Z"),
            "2026-09-13T10:00:00+03:00".toInstantOrNull(),
        )
    }

    @Test
    fun `время без зоны считается UTC`() {
        assertEquals(Instant.parse("2026-09-13T07:00:00Z"), "2026-09-13T07:00:00".toInstantOrNull())
    }

    @Test
    fun `испорченное время не роняет разбор ответа`() {
        assertNull("вчера вечером".toInstantOrNull())
        assertNull("".toInstantOrNull())
        assertNull(null.toInstantOrNull())
    }

    @Test
    fun `изображения декодируются из base64`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val image = LatestImageDto(
            agentId = "e2c1",
            capturedAt = "2026-09-13T07:00:00Z",
            detection = DetectionDto(personPresent = true, personCount = 2),
            motion = MotionDto(motionPresent = true, regionCount = 3),
            contentBase64 = encoded,
            motionContentBase64 = encoded,
        ).toDomain()

        assertArrayEquals(bytes, image.personJpeg)
        assertArrayEquals(bytes, image.motionJpeg)
        assertEquals(2, image.personCount)
        assertEquals(3, image.motionRegionCount)
    }

    @Test
    fun `повреждённое изображение даёт пустой массив, а не исключение`() {
        val image = LatestImageDto(
            agentId = "e2c1",
            contentBase64 = "это не base64!!!",
            motionContentBase64 = "тоже нет",
        ).toDomain()

        assertEquals(0, image.personJpeg.size)
        assertEquals(0, image.motionJpeg.size)
    }
}
