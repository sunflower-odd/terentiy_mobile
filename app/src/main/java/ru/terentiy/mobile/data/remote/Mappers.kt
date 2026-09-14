package ru.terentiy.mobile.data.remote

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import ru.terentiy.mobile.domain.Agent
import ru.terentiy.mobile.domain.LatestImage
import ru.terentiy.mobile.domain.Tri
import timber.log.Timber

/**
 * Граница между формой ответа backend и доменной моделью.
 *
 * Здесь — единственное место, где `null` из DTO превращается в [Tri.UNKNOWN].
 * Ниже по коду `Boolean?` отсутствует как тип, поэтому перепутать «неизвестно»
 * и «нет» уже невозможно.
 */

fun AgentStatusDto.toDomain(): Agent = Agent(
    id = id,
    name = name,
    cameraId = cameraId,
    softwareVersion = softwareVersion,
    modelVersion = modelVersion,
    online = online,
    agentOnline = Tri.of(agentOnline),
    cameraConnected = Tri.of(cameraConnected),
    streamActive = Tri.of(streamActive),
    mlReady = Tri.of(mlReady),
    queueSize = queueSize,
    reportedAt = reportedAt.toInstantOrNull(),
    lastFrameAt = lastFrameAt.toInstantOrNull(),
)

fun LatestImageDto.toDomain(): LatestImage = LatestImage(
    agentId = agentId,
    capturedAt = capturedAt.toInstantOrNull(),
    modelName = modelName,
    modelVersion = modelVersion,
    personPresent = detection.personPresent,
    personCount = detection.personCount,
    motionPresent = motion.motionPresent,
    motionRegionCount = motion.regionCount,
    personJpeg = contentBase64.decodeBase64(),
    motionJpeg = motionContentBase64.decodeBase64(),
)

/**
 * Backend хранит и передаёт время в UTC с суффиксом `Z`, но поле `reported_at`
 * приходит от агента и в принципе может нести смещение. Поэтому разбор идёт
 * по трём вариантам, а непарсящееся значение даёт `null`, а не исключение:
 * время на экране справочное, состояние агента — нет, и портить второе из-за
 * первого нельзя.
 */
internal fun String?.toInstantOrNull(): Instant? {
    if (this.isNullOrBlank()) return null
    runCatching { return Instant.parse(this) }
    runCatching { return OffsetDateTime.parse(this).toInstant() }
    runCatching { return LocalDateTime.parse(this).toInstant(ZoneOffset.UTC) }
    Timber.w("Не удалось разобрать время из ответа backend | value=%s", this)
    return null
}

/**
 * Используется java.util.Base64, а не android.util.Base64: он доступен с API 26,
 * а это минимальный уровень приложения, и при этом остаётся обычным JVM-классом.
 * Благодаря этому мапперы проверяются обычными unit-тестами без Robolectric.
 */
internal fun String.decodeBase64(): ByteArray =
    runCatching { Base64.getDecoder().decode(this) }
        .getOrElse {
            Timber.w("Повреждённое изображение в ответе backend")
            ByteArray(0)
        }
