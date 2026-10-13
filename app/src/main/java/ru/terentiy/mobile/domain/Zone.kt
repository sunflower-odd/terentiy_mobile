package ru.terentiy.mobile.domain

import java.time.Instant
import kotlin.math.abs

/**
 * Критическая зона: участок пола на кадре камеры. Хранится на сервере.
 *
 * Новая или перерисованная зона — в [ZoneState.DRAFT] и ничего не отслеживает,
 * пока сервер её не проверит (сессия привязки по контракту 2.0.0-draft — в
 * приложении её пока нет). Решение «человек в зоне» принимает только сервер.
 *
 * Координаты вершин — доли ширины и высоты кадра [referenceFrameId] от 0 до 1.
 */
data class Zone(
    val id: String,
    val agentId: String,
    val cameraId: String,
    val name: String,
    val polygon: List<ZonePoint>,
    val referenceFrameId: String,
    val referenceMediaId: String,
    val state: ZoneState,
    val version: Int,
)

data class ZonePoint(val x: Float, val y: Float)

enum class ZoneState(val wire: String) {
    DRAFT("draft"),
    BINDING("binding"),
    BOUND("bound"),
    NEEDS_REBINDING("needs_rebinding");

    companion object {
        fun parse(raw: String?): ZoneState = entries.firstOrNull { it.wire == raw } ?: DRAFT
    }
}

/** Последний кадр камеры для разметки. */
data class CurrentFrame(
    val frameId: String,
    val mediaId: String,
    val capturedAt: Instant?,
    val width: Int,
    val height: Int,
)

/** Что рисует и сохраняет редактор: тело создания или замены зоны. */
data class ZoneDraft(
    val name: String,
    val polygon: List<ZonePoint>,
    val referenceFrameId: String,
)

/** Проверка контура — те же правила, что на сервере: ошибка видна до отправки. */
object ZoneGeometry {

    const val MIN_VERTICES = 3
    const val MAX_VERTICES = 12
    const val MAX_ZONES = 10
    const val MAX_NAME_LENGTH = 40

    /** Минимальная площадь — 0,5 % кадра. */
    const val MIN_AREA = 0.005f

    enum class Problem { TOO_FEW_POINTS, TOO_MANY_POINTS, SELF_INTERSECTING, TOO_SMALL }

    fun problem(points: List<ZonePoint>): Problem? = when {
        points.size < MIN_VERTICES -> Problem.TOO_FEW_POINTS
        points.size > MAX_VERTICES -> Problem.TOO_MANY_POINTS
        isSelfIntersecting(points) -> Problem.SELF_INTERSECTING
        area(points) < MIN_AREA -> Problem.TOO_SMALL
        else -> null
    }

    fun area(points: List<ZonePoint>): Float {
        var sum = 0f
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2f
    }

    fun isSelfIntersecting(points: List<ZonePoint>): Boolean {
        val n = points.size
        if (n < 4) return false
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                // Соседние стороны делят вершину по построению.
                if (j == i + 1 || (i == 0 && j == n - 1)) continue
                if (segmentsIntersect(points[i], points[(i + 1) % n], points[j], points[(j + 1) % n])) return true
            }
        }
        return false
    }

    private fun orientation(a: ZonePoint, b: ZonePoint, c: ZonePoint): Float =
        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    private fun onSegment(a: ZonePoint, b: ZonePoint, p: ZonePoint): Boolean =
        abs(orientation(a, b, p)) <= EPSILON &&
            p.x in (minOf(a.x, b.x) - EPSILON)..(maxOf(a.x, b.x) + EPSILON) &&
            p.y in (minOf(a.y, b.y) - EPSILON)..(maxOf(a.y, b.y) + EPSILON)

    private fun segmentsIntersect(a: ZonePoint, b: ZonePoint, c: ZonePoint, d: ZonePoint): Boolean {
        val o1 = orientation(a, b, c)
        val o2 = orientation(a, b, d)
        val o3 = orientation(c, d, a)
        val o4 = orientation(c, d, b)
        val proper = ((o1 > EPSILON && o2 < -EPSILON) || (o1 < -EPSILON && o2 > EPSILON)) &&
            ((o3 > EPSILON && o4 < -EPSILON) || (o3 < -EPSILON && o4 > EPSILON))
        return proper || onSegment(a, b, c) || onSegment(a, b, d) || onSegment(c, d, a) || onSegment(c, d, b)
    }

    private const val EPSILON = 1e-6f
}
