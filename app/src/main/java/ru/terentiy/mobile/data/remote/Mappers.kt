package ru.terentiy.mobile.data.remote

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import ru.terentiy.mobile.domain.Agent
import ru.terentiy.mobile.domain.EventStatus
import ru.terentiy.mobile.domain.FallAnalysis
import ru.terentiy.mobile.domain.FallEvent
import ru.terentiy.mobile.domain.Session
import ru.terentiy.mobile.domain.Tri
import timber.log.Timber

/**
 * Граница между формой ответа сервера и доменной моделью.
 *
 * Здесь — единственное место, где строки превращаются в перечисления, а сырой
 * JSON `analysis` в [FallAnalysis]. Неизвестное значение не роняет разбор:
 * контракт уже менялся трижды, и новые варианты появятся раньше, чем обновится
 * установленное приложение.
 */

fun MobileTokenDto.toDomain(): Session = Session(
    accessToken = accessToken,
    householdId = householdId,
)

fun AgentDto.toDomain(): Agent = Agent(
    id = id,
    householdId = householdId,
    name = name,
    cameraId = cameraId,
    softwareVersion = softwareVersion,
    createdAt = createdAt.toInstantOrNull(),
    online = online,
    agentOnline = Tri.of(agentOnline),
    cameraConnected = Tri.of(cameraConnected),
    streamActive = Tri.of(streamActive),
    queueSize = queueSize,
    reportedAt = reportedAt.toInstantOrNull(),
    lastFrameAt = lastFrameAt.toInstantOrNull(),
)

fun AgentListDto.toDomain(): List<Agent> = items.map { it.toDomain() }

/**
 * Событие.
 *
 * Событие без `clip_media_id` по контракту невозможно: сервер отдаёт только
 * события с загруженным клипом. Но если такое всё же пришло, оно **отбрасывается**
 * в [FallEventListDto.toDomain], а не превращается в запись с пустым
 * идентификатором, по которой карточка потом сходит за медиа в никуда.
 */
fun FallEventDto.toDomain(): FallEvent? {
    val clip = clipMediaId?.takeIf { it.isNotBlank() } ?: run {
        Timber.w("Событие без клипа отброшено | id=%s", id)
        return null
    }
    return FallEvent(
        id = id,
        agentId = agentId,
        frameId = frameId,
        frameMediaId = frameMediaId,
        clipMediaId = clip,
        capturedAt = capturedAt.toInstantOrNull(),
        createdAt = createdAt.toInstantOrNull(),
        status = EventStatus.parse(status),
        analysis = analysis?.toFallAnalysis(),
    )
}

fun FallEventListDto.toDomain(): List<FallEvent> = items.mapNotNull { it.toDomain() }

/**
 * Разбор свободного поля `analysis`.
 *
 * Целиком защищён: `analysis` — ML-диагностика, её форма меняется вместе с
 * правилами распознавания, и неожиданное содержимое обязано давать `null`, а не
 * ронять весь список событий. Берётся только вложенный объект `fall` и
 * `processing_time_ms` — остальное (`people`, `poses`, `pose_diagnostics`, `ml`)
 * не показывается пользователю и не разбирается.
 */
internal fun JsonObject.toFallAnalysis(): FallAnalysis? = runCatching {
    val fall = this["fall"]?.jsonObject ?: return@runCatching null
    FallAnalysis(
        detected = fall.bool("detected") ?: false,
        trackId = fall.int("track_id"),
        candidateRules = fall.strings("candidate_rules"),
        confirmationRules = fall.strings("confirmation_rules"),
        candidateVotes = fall.int("candidate_votes"),
        candidateVotesRequired = fall.int("candidate_votes_required"),
        confirmationVotes = fall.int("confirmation_votes"),
        confirmationVotesRequired = fall.int("confirmation_votes_required"),
        processingTimeMs = double("processing_time_ms"),
    )
}.getOrElse { error ->
    Timber.w(error, "Не удалось разобрать analysis события")
    null
}

private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

private fun JsonObject.bool(key: String): Boolean? = primitive(key)?.booleanOrNull

private fun JsonObject.int(key: String): Int? = primitive(key)?.intOrNull

private fun JsonObject.double(key: String): Double? = primitive(key)?.doubleOrNull

private fun JsonObject.strings(key: String): List<String> =
    runCatching {
        this[key]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content }
    }.getOrNull().orEmpty()

/**
 * Время из ответа сервера.
 *
 * Сервер отдаёт UTC с суффиксом `Z`, но отдельные поля приходят от агента и могут
 * нести смещение. Разбор идёт по трём вариантам, а непарсящееся значение даёт
 * `null`: время на экране справочное, а состояние контура — нет, и портить
 * второе из-за первого нельзя.
 */
internal fun String?.toInstantOrNull(): Instant? {
    if (this.isNullOrBlank()) return null
    runCatching { return Instant.parse(this) }
    runCatching { return OffsetDateTime.parse(this).toInstant() }
    runCatching { return LocalDateTime.parse(this).toInstant(ZoneOffset.UTC) }
    Timber.w("Не удалось разобрать время из ответа сервера | value=%s", this)
    return null
}
