package ru.terentiy.mobile.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * DTO по контракту 1.0.0 — тому, который backend действительно реализует.
 *
 * Классы намеренно повторяют форму ответа сервера, включая nullable-поля, и не
 * используются выше слоя данных. Это единственное место в приложении, знающее
 * имена JSON-полей: контракт менялся уже трижды, и когда это повторится, правка
 * должна ограничиться этим файлом и мапперами.
 *
 * Перечисления приходят строками и разбираются вручную: kotlinx.serialization по
 * умолчанию бросает исключение на неизвестном значении, а сервер вправе добавить
 * вариант раньше, чем обновится установленное приложение.
 */

@Serializable
data class MobileActivationRequestDto(
    @SerialName("installation_code") val installationCode: String,
    @SerialName("device_id") val deviceId: String,
    /**
     * PIN — **строка** по схеме `^\d{6}$`, никогда не число: `"012345"` обязан
     * сохранить ведущий ноль на всём пути до сервера.
     */
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
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("household_id") val householdId: String,
)

@Serializable
data class HealthDto(
    val status: String = "",
    val database: String = "",
)

@Serializable
data class AgentListDto(
    val items: List<AgentDto> = emptyList(),
)

/**
 * Состояние агента.
 *
 * Все `Boolean?` и `Int?` обязаны иметь значение по умолчанию `null` и не иметь
 * ненулевого default: в `AppModule` включён `coerceInputValues`, и при ненулевом
 * default явный `null` из ответа молча превратился бы в это значение. Нам нужен
 * именно `null` — он означает «агент ни разу не отчитывался».
 *
 * У `online` default ненулевой сознательно: поле в контракте обязательное и
 * не nullable, а если сервер однажды пришлёт `null`, безопаснее считать агента
 * недоступным, чем уронить разбор всего списка.
 */
@Serializable
data class AgentDto(
    val id: String,
    @SerialName("household_id") val householdId: String = "",
    val name: String = "",
    @SerialName("camera_id") val cameraId: String = "",
    @SerialName("software_version") val softwareVersion: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    val online: Boolean = false,
    @SerialName("reported_at") val reportedAt: String? = null,
    @SerialName("agent_online") val agentOnline: Boolean? = null,
    @SerialName("camera_connected") val cameraConnected: Boolean? = null,
    @SerialName("stream_active") val streamActive: Boolean? = null,
    @SerialName("queue_size") val queueSize: Int? = null,
    @SerialName("last_frame_at") val lastFrameAt: String? = null,
)

@Serializable
data class FallEventListDto(
    val items: List<FallEventDto> = emptyList(),
)

/**
 * Событие падения.
 *
 * `analysis` остаётся сырым [JsonObject] и разбирается в мапперe: это свободный
 * объект контракта, и его форма меняется вместе с правилами распознавания.
 * Выше слоя данных [JsonObject] не поднимается.
 */
@Serializable
data class FallEventDto(
    val id: String,
    @SerialName("agent_id") val agentId: String = "",
    @SerialName("frame_id") val frameId: String = "",
    @SerialName("frame_media_id") val frameMediaId: String = "",
    @SerialName("clip_media_id") val clipMediaId: String? = null,
    @SerialName("captured_at") val capturedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val status: String = "",
    val analysis: JsonObject? = null,
)

/**
 * Ответ `GET /api/v1/agents/{agent_id}/current-frame`:
 * последний проанализированный кадр и люди на нём, рамки в пикселях кадра.
 */
@Serializable
data class CurrentFrameDto(
    @SerialName("frame_id") val frameId: String,
    @SerialName("media_id") val mediaId: String,
    @SerialName("captured_at") val capturedAt: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val people: List<DetectedPersonDto> = emptyList(),
)

@Serializable
data class DetectedPersonDto(
    /** `[x1, y1, x2, y2]` в пикселях, начало в левом верхнем углу. */
    @SerialName("bbox_xyxy") val bboxXyxy: List<Double> = emptyList(),
    val confidence: Double? = null,
)
