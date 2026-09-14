package ru.terentiy.mobile.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO ровно по контракту contracts/stage01-mobile-api.yaml.
 *
 * Эти классы намеренно повторяют форму ответа backend, включая nullable-поля, и
 * не используются выше слоя данных. Доменные модели живут отдельно: контракт уже
 * один раз разошёлся с реализацией, и когда это повторится, правка должна
 * ограничиться DTO и мапперами.
 */

@Serializable
data class MobileActivationRequestDto(
    @SerialName("installation_code") val installationCode: String,
    @SerialName("device_id") val deviceId: String,
    val pin: String,
)

@Serializable
data class MobileLoginRequestDto(
    @SerialName("device_id") val deviceId: String,
    val pin: String,
)

@Serializable
data class MobileTokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("household_id") val householdId: String,
)

@Serializable
data class AgentListDto(
    val items: List<AgentStatusDto> = emptyList(),
)

@Serializable
data class AgentStatusDto(
    val id: String,
    @SerialName("household_id") val householdId: String,
    val name: String,
    @SerialName("camera_id") val cameraId: String,
    @SerialName("software_version") val softwareVersion: String,
    @SerialName("created_at") val createdAt: String,
    val online: Boolean,
    @SerialName("reported_at") val reportedAt: String? = null,
    @SerialName("agent_online") val agentOnline: Boolean? = null,
    @SerialName("camera_connected") val cameraConnected: Boolean? = null,
    @SerialName("stream_active") val streamActive: Boolean? = null,
    @SerialName("ml_ready") val mlReady: Boolean? = null,
    @SerialName("queue_size") val queueSize: Int? = null,
    @SerialName("model_version") val modelVersion: String? = null,
    @SerialName("last_frame_at") val lastFrameAt: String? = null,
)

@Serializable
data class LatestImageDto(
    @SerialName("agent_id") val agentId: String,
    @SerialName("captured_at") val capturedAt: String? = null,
    @SerialName("model_name") val modelName: String = "",
    @SerialName("model_version") val modelVersion: String = "",
    val detection: DetectionDto = DetectionDto(),
    val motion: MotionDto = MotionDto(),
    @SerialName("media_type") val mediaType: String = "image/jpeg",
    @SerialName("content_base64") val contentBase64: String,
    @SerialName("motion_content_base64") val motionContentBase64: String,
)

@Serializable
data class DetectionDto(
    @SerialName("person_present") val personPresent: Boolean = false,
    @SerialName("person_count") val personCount: Int = 0,
    @SerialName("max_confidence") val maxConfidence: Double = 0.0,
    @SerialName("inference_time_ms") val inferenceTimeMs: Double? = null,
)

@Serializable
data class MotionDto(
    val success: Boolean = false,
    @SerialName("background_ready") val backgroundReady: Boolean = false,
    @SerialName("motion_present") val motionPresent: Boolean = false,
    @SerialName("region_count") val regionCount: Int = 0,
    @SerialName("changed_area_ratio") val changedAreaRatio: Double = 0.0,
    @SerialName("processing_time_ms") val processingTimeMs: Double? = null,
    @SerialName("error_code") val errorCode: String? = null,
)
