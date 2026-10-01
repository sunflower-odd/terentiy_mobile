package ru.terentiy.mobile.core.poll

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Отступ при сетевых ошибках: без него телефон вне сети будит радио каждые
 * несколько секунд и сажает батарею, ничего не получая.
 */
class BackoffTest {

    @Test
    fun `без ошибок интервал базовый`() {
        val backoff = Backoff(Duration.ofSeconds(10))
        assertEquals(Duration.ofSeconds(10), backoff.current)
    }

    @Test
    fun `каждая ошибка удваивает интервал`() {
        val backoff = Backoff(Duration.ofSeconds(10))
        backoff.failure()
        assertEquals(Duration.ofSeconds(20), backoff.current)
        backoff.failure()
        assertEquals(Duration.ofSeconds(40), backoff.current)
    }

    @Test
    fun `рост ограничен потолком`() {
        val backoff = Backoff(Duration.ofSeconds(30), max = Duration.ofMinutes(2))
        repeat(20) { backoff.failure() }
        assertEquals(Duration.ofMinutes(2), backoff.current)
    }

    @Test
    fun `первый успех сбрасывает отступ`() {
        val backoff = Backoff(Duration.ofSeconds(10))
        repeat(3) { backoff.failure() }
        backoff.success()
        assertEquals(Duration.ofSeconds(10), backoff.current)
    }
}
