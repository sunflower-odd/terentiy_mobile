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

/** Агент в ответе `GET /api/v1/agents` (контракт 2.0.0-draft). */
@Serializable
data class AgentDto(
    @SerialName("agent_id") val id: String,
    val name: String = "",
    @SerialName("camera_id") val cameraId: String = "",
    val online: Boolean = false,
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

// --- Критические зоны (mobile-server 2.0.0-draft) ---

/** Последний кадр камеры для разметки. JPEG — через `GET /api/v1/media/{media_id}`. */
@Serializable
data class CurrentFrameDto(
    @SerialName("frame_id") val frameId: String,
    @SerialName("media_id") val mediaId: String,
    @SerialName("captured_at") val capturedAt: String? = null,
    val width: Int = 0,
    val height: Int = 0,
)

/** Зона камеры. Вершины — доли кадра `reference_frame_id`: `[[x, y], ...]`. */
@Serializable
data class ZoneDto(
    @SerialName("zone_id") val zoneId: String,
    @SerialName("agent_id") val agentId: String = "",
    @SerialName("camera_id") val cameraId: String = "",
    val name: String = "",
    val polygon: List<List<Double>> = emptyList(),
    @SerialName("reference_frame_id") val referenceFrameId: String = "",
    @SerialName("reference_media_id") val referenceMediaId: String = "",
    val state: String = "draft",
    val version: Int = 1,
)

@Serializable
data class ZoneListDto(val items: List<ZoneDto> = emptyList())

/** Тело `POST …/zones`. Лишних полей сервер не принимает (`additionalProperties: false`). */
@Serializable
data class ZoneRequestDto(
    val name: String,
    val polygon: List<List<Double>>,
    @SerialName("reference_frame_id") val referenceFrameId: String,
)

/** Тело `PUT …/zones/{zone_id}`: то же плюс версия, которую редактировали. */
@Serializable
data class ZoneUpdateRequestDto(
    val name: String,
    val polygon: List<List<Double>>,
    @SerialName("reference_frame_id") val referenceFrameId: String,
    val version: Int,
)

/** Тело `POST …/binding-sessions`: версия зоны, которую пользователь только что видел. */
@Serializable
data class StartZoneBindingRequestDto(
    @SerialName("zone_version") val zoneVersion: Int,
)

/** Сессия проверки зоны. `binding` (запись о результате) приложению не нужна и не разбирается. */
@Serializable
data class ZoneBindingSessionDto(
    @SerialName("binding_session_id") val bindingSessionId: String,
    @SerialName("zone_id") val zoneId: String = "",
    @SerialName("zone_version") val zoneVersion: Int = 0,
    val state: String = "",
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    val progress: ZoneBindingProgressDto? = null,
    val failure: ZoneBindingFailureDto? = null,
)

@Serializable
data class ZoneBindingProgressDto(
    @SerialName("last_observation") val lastObservation: ZoneAnchorObservationDto? = null,
)

@Serializable
data class ZoneAnchorObservationDto(
    @SerialName("media_id") val mediaId: String = "",
    val point: NormalizedPointDto? = null,
    @SerialName("in_zone") val inZone: Boolean = false,
)

@Serializable
data class NormalizedPointDto(val x: Double, val y: Double)

@Serializable
data class ZoneBindingFailureDto(val code: String? = null)
