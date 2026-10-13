package ru.terentiy.mobile.domain

import java.time.Instant
import kotlin.math.abs

/**
 * Критическая зона: участок пола на кадре камеры.
 *
 * В этой версии зона хранится только на телефоне и нужна, чтобы проверить
 * «человек в зоне или нет» по кадру, полученному кнопкой «Получить вид».
 * Тревог по зонам пока нет: условия и хранение на сервере — следующий шаг.
 *
 * Координаты вершин — доли ширины и высоты кадра от 0 до 1, а не пиксели:
 * так зона не зависит от разрешения JPEG и от размера экрана, на котором её
 * рисовали.
 */
data class Zone(
    val id: String,
    val agentId: String,
    val name: String,
    val polygon: List<ZonePoint>,
)

data class ZonePoint(val x: Float, val y: Float)

/**
 * Человек на кадре — рамка детектора сервера в долях кадра (0…1, начало
 * координат в левом верхнем углу).
 */
data class PersonBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    /**
     * Опора — нижняя четверть рамки: то, чем человек касается пола. У
     * стоящего это ступни и голени, у лежащего — всё тело вдоль пола.
     *
     * Одной точки «середина нижней стороны» мало: у лежащего она приходится
     * на середину тела, и зона у его ног человека «не видела».
     */
    val support: PersonBox get() = copy(top = bottom - (bottom - top) * SUPPORT_SHARE)

    companion object {
        /** Какая часть высоты рамки снизу считается опорой. */
        const val SUPPORT_SHARE = 0.25f
    }
}

/** Последний проанализированный кадр камеры и люди на нём. */
data class CurrentFrame(
    val frameId: String,
    val mediaId: String,
    val capturedAt: Instant?,
    val width: Int,
    val height: Int,
    val people: List<PersonBox>,
)

/** Итог проверки зоны по одному кадру. */
enum class ZonePresence {
    /** На кадре никого нет. */
    NOBODY,

    /** Люди на кадре есть, но ни один не стоит в зоне. */
    OUTSIDE,

    /** Хотя бы один человек стоит в зоне. */
    INSIDE;

    companion object {
        /**
         * Человек в зоне, если в неё попадает хотя бы [MIN_SUPPORT_INSIDE] его
         * опоры ([PersonBox.support]). Порог отсекает касание краем: человек
         * рядом с зоной в неё не попадает.
         */
        fun of(polygon: List<ZonePoint>, people: List<PersonBox>): ZonePresence = when {
            people.isEmpty() -> NOBODY
            people.any { ZoneGeometry.shareInside(polygon, it.support) >= MIN_SUPPORT_INSIDE } -> INSIDE
            else -> OUTSIDE
        }

        /** Доля опоры внутри зоны, начиная с которой человек считается в зоне. */
        const val MIN_SUPPORT_INSIDE = 0.25f
    }
}

/** Проверка контура: ошибка видна до сохранения. */
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

    /**
     * Какая доля прямоугольника [box] лежит внутри многоугольника, от 0 до 1.
     *
     * Многоугольник обрезается прямоугольником (Сазерленд — Ходжмен: окно
     * выпуклое, сам контур может быть невыпуклым), затем площади делятся.
     */
    fun shareInside(polygon: List<ZonePoint>, box: PersonBox): Float {
        val boxArea = (box.right - box.left) * (box.bottom - box.top)
        if (polygon.size < MIN_VERTICES || boxArea <= 0f) return 0f
        var clipped = polygon
        clipped = clip(clipped, { it.x >= box.left }) { a, b -> crossX(a, b, box.left) }
        clipped = clip(clipped, { it.x <= box.right }) { a, b -> crossX(a, b, box.right) }
        clipped = clip(clipped, { it.y >= box.top }) { a, b -> crossY(a, b, box.top) }
        clipped = clip(clipped, { it.y <= box.bottom }) { a, b -> crossY(a, b, box.bottom) }
        if (clipped.size < MIN_VERTICES) return 0f
        return (area(clipped) / boxArea).coerceIn(0f, 1f)
    }

    private fun clip(
        points: List<ZonePoint>,
        inside: (ZonePoint) -> Boolean,
        cross: (ZonePoint, ZonePoint) -> ZonePoint,
    ): List<ZonePoint> {
        if (points.isEmpty()) return points
        val result = ArrayList<ZonePoint>(points.size + 4)
        var previous = points.last()
        for (current in points) {
            val currentIn = inside(current)
            val previousIn = inside(previous)
            if (currentIn) {
                if (!previousIn) result += cross(previous, current)
                result += current
            } else if (previousIn) {
                result += cross(previous, current)
            }
            previous = current
        }
        return result
    }

    private fun crossX(a: ZonePoint, b: ZonePoint, x: Float): ZonePoint =
        ZonePoint(x, a.y + (x - a.x) / (b.x - a.x) * (b.y - a.y))

    private fun crossY(a: ZonePoint, b: ZonePoint, y: Float): ZonePoint =
        ZonePoint(a.x + (y - a.y) / (b.y - a.y) * (b.x - a.x), y)

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
