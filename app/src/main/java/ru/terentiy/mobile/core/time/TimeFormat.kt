package ru.terentiy.mobile.core.time

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Backend хранит и передаёт время в UTC. Пользователю оно показывается в часовом
 * поясе устройства — так же, как это делает CLI-эмулятор (`cli.py:to_local_time`).
 */
object TimeFormat {

    private val timeOnly = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val dateAndTime = DateTimeFormatter.ofPattern("d MMMM, HH:mm:ss")

    fun localTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        timeOnly.format(instant.atZone(zone))

    fun localDateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        dateAndTime.format(instant.atZone(zone))

    /**
     * Время показывается коротко, если это сегодняшний день, и с датой — если нет.
     * Иначе «12:03:40» на кадре трёхдневной давности вводит в заблуждение.
     */
    fun smart(instant: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val sameDay = instant.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()
        return if (sameDay) localTime(instant, zone) else localDateTime(instant, zone)
    }
}
