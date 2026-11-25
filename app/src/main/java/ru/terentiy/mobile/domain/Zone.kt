package ru.terentiy.mobile.domain

import java.time.Instant
import java.time.LocalTime
import kotlin.math.abs

/**
 * Критическая зона: участок пола на кадре камеры. Хранится на сервере.
 *
 * Новая или перерисованная зона — в [ZoneState.DRAFT] и ничего не отслеживает,
 * пока сервер её не проверит: человек встаёт в зону, сервер по свежим кадрам
 * подтверждает, что видит его внутри (сессия привязки, [ZoneBindingSession]).
 * Решение «человек в зоне» принимает только сервер.
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
    /**
     * Номер правки: меняется при любом изменении зоны, уходит в PATCH. [version] —
     * версия разметки: меняется только при перерисовке, на неё ссылается проверка.
     */
    val revision: Int = 1,
    /** Переключатель: выключенная зона хранится, но тревог не даёт. */
    val enabled: Boolean = true,
    /** Условия тревоги. Приложение показывает и меняет первое; остальные сохраняет как есть. */
    val rules: List<ZoneRule> = listOf(ZoneRule()),
) {
    /** Правило, которое видит пользователь. У зоны без правил — значение по умолчанию. */
    val rule: ZoneRule get() = rules.firstOrNull() ?: ZoneRule()

    /** Зона следит: проверена и включена. Непроверенную включить нельзя. */
    val active: Boolean get() = enabled && state == ZoneState.BOUND
}

/**
 * Условие тревоги зоны. [schedule] — `null`, если правило действует всегда.
 * [id] — `null` у правила, которого ещё нет на сервере.
 */
data class ZoneRule(
    val id: String? = null,
    val trigger: ZoneTrigger = ZoneTrigger.ENTER,
    val schedule: ZoneSchedule? = null,
)

/** Что считается тревогой: человек вошёл в зону или вышел из неё. */
enum class ZoneTrigger(val wire: String) {
    ENTER("enter"),
    EXIT("exit");

    companion object {
        fun parse(raw: String?): ZoneTrigger = entries.firstOrNull { it.wire == raw } ?: ENTER
    }
}

/**
 * Ежедневный промежуток, когда правило действует: с [from] до [to] по местному
 * времени. Если [to] раньше [from], промежуток идёт через полночь
 * (22:00–07:00). Начало входит в промежуток, конец — нет.
 */
data class ZoneSchedule(val from: LocalTime, val to: LocalTime) {

    val overnight: Boolean get() = to < from

    /** Пустой промежуток: начало совпадает с концом. Такой не сохраняется. */
    val empty: Boolean get() = from == to

    companion object {
        /** Предлагается, когда пользователь включает «Только в заданное время». */
        val DEFAULT_NIGHT = ZoneSchedule(LocalTime.of(22, 0), LocalTime.of(7, 0))
    }
}

data class ZonePoint(val x: Float, val y: Float)

enum class ZoneState(val wire: String) {
    DRAFT("draft"),
    BINDING("binding"),
    BOUND("bound");

    companion object {
        fun parse(raw: String?): ZoneState = entries.firstOrNull { it.wire == raw } ?: DRAFT
    }
}

/**
 * Сессия проверки (привязки) зоны. Опрашивается, пока не придёт конечное
 * состояние. Числовые счётчики сервера пользователю не показываются — только
 * [state] и маркер [observation].
 */
data class ZoneBindingSession(
    val id: String,
    val zoneId: String,
    val zoneVersion: Int,
    val state: ZoneBindingState,
    val startedAt: Instant?,
    val expiresAt: Instant?,
    val observation: ZoneObservation?,
    val failure: ZoneBindingFailure?,
)

enum class ZoneBindingState(val wire: String, val terminal: Boolean) {
    WAITING_FOR_FRAME("waiting_for_frame", false),
    WAITING_FOR_PERSON("waiting_for_person", false),
    COLLECTING("collecting", false),
    BOUND("bound", true),
    FAILED("failed", true),
    EXPIRED("expired", true),
    CANCELLED("cancelled", true);

    companion object {
        /** Незнакомое состояние считается промежуточным: опрос продолжается до конечного. */
        fun parse(raw: String?): ZoneBindingState = entries.firstOrNull { it.wire == raw } ?: WAITING_FOR_FRAME
    }
}

/**
 * Последнее, что сервер увидел на кадре: точка опоры человека в долях кадра
 * [mediaId] и попала ли она в зону. [inZone] решает сервер, клиент не пересчитывает.
 */
data class ZoneObservation(
    val mediaId: String,
    val point: ZonePoint,
    val inZone: Boolean,
)

/** Причина неудачной проверки. Незнакомый код — [UNKNOWN]. */
enum class ZoneBindingFailure(val wire: String?) {
    CAMERA_UNAVAILABLE("camera_unavailable"),
    NO_PERSON("no_person_detected"),
    NO_SUPPORT_POINT("no_reliable_support_point"),
    NOT_INSIDE("insufficient_inside_observations"),
    ZONE_CHANGED("zone_version_changed"),
    UNKNOWN(null);

    companion object {
        fun parse(raw: String?): ZoneBindingFailure = entries.firstOrNull { it.wire != null && it.wire == raw } ?: UNKNOWN
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

/** Новая зона: контур на кадре [referenceFrameId], переключатель и условия тревоги. */
data class ZoneDraft(
    val name: String,
    val polygon: List<ZonePoint>,
    val referenceFrameId: String,
    val enabled: Boolean = true,
    val rules: List<ZoneRule> = listOf(ZoneRule()),
)

/**
 * Частичная правка зоны: `null` — поле не меняется. [redraw] — новый контур на
 * текущем кадре; только он сбрасывает проверку зоны. [rules] заменяет список целиком.
 */
data class ZonePatch(
    val name: String? = null,
    val redraw: ZoneRedraw? = null,
    val enabled: Boolean? = null,
    val rules: List<ZoneRule>? = null,
) {
    val empty: Boolean get() = name == null && redraw == null && enabled == null && rules == null
}

data class ZoneRedraw(val polygon: List<ZonePoint>, val referenceFrameId: String)

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
