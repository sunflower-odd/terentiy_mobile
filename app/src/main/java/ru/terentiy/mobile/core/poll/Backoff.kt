package ru.terentiy.mobile.core.poll

import java.time.Duration

/**
 * Отступ при сетевых ошибках.
 *
 * Без него телефон, вышедший из зоны Wi-Fi, будит радио каждые несколько секунд
 * и сажает батарею, ничего не получая взамен. Интервал удваивается на каждой
 * неудаче до потолка и сбрасывается на первом успехе.
 *
 * Чистый класс без зависимостей — покрывается обычным unit-тестом.
 */
class Backoff(
    private val base: Duration,
    private val max: Duration = PollIntervals.MaxBackoff,
) {

    private var failures = 0

    val current: Duration
        get() {
            if (failures == 0) return base
            val factor = 1L shl failures.coerceAtMost(MAX_DOUBLINGS)
            val grown = base.multipliedBy(factor)
            return if (grown > max) max else grown
        }

    fun success() {
        failures = 0
    }

    fun failure() {
        if (failures < MAX_DOUBLINGS) failures++
    }

    private companion object {
        /** Ограничивает рост, а заодно исключает переполнение при сдвиге. */
        const val MAX_DOUBLINGS = 6
    }
}
