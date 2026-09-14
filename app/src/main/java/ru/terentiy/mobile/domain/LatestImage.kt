package ru.terentiy.mobile.domain

import java.time.Instant

/**
 * Пара кадров одного момента: результат детекции людей и результат детекции движения.
 *
 * Backend формирует оба изображения из одного кадра и заменяет пару атомарно, поэтому
 * получить YOLO от одного момента и motion от другого невозможно.
 *
 * Это не data class намеренно: сравнивать байтовые массивы по ссылке бессмысленно,
 * а генерируемые `equals` и `hashCode` делали бы именно это.
 */
class LatestImage(
    val agentId: String,
    val capturedAt: Instant?,
    val modelName: String,
    val modelVersion: String,
    val personPresent: Boolean,
    val personCount: Int,
    val motionPresent: Boolean,
    val motionRegionCount: Int,
    val personJpeg: ByteArray,
    val motionJpeg: ByteArray,
)
