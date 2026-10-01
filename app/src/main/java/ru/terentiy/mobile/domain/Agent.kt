package ru.terentiy.mobile.domain

import java.time.Instant

/**
 * Агент установки и его последнее известное техническое состояние.
 *
 * ## [online] и [agentOnline] — разные вещи
 *
 * [agentOnline] — то, что агент сообщил о себе в последнем heartbeat. На мёртвом
 * агенте это значение остаётся `true` навсегда: последним, что он успел сказать,
 * было «я работаю».
 *
 * [online] считает сервер, сравнивая время heartbeat с текущим по порогу
 * `agent_offline_after_seconds`. Часы телефона в расчёте не участвуют, поэтому
 * сбитое время на устройстве картину не искажает.
 *
 * Вердикт о состоянии контура строится на [online]. [agentOnline] идёт только в
 * диагностику — использовать его для вердикта значит показывать «всё хорошо» на
 * агенте, который умер час назад.
 */
data class Agent(
    val id: String,
    val householdId: String,
    val name: String,
    val cameraId: String,
    val softwareVersion: String,
    val createdAt: Instant?,
    val online: Boolean,
    val agentOnline: Tri,
    val cameraConnected: Tri,
    val streamActive: Tri,
    val queueSize: Int?,
    val reportedAt: Instant?,
    val lastFrameAt: Instant?,
) {
    /** Агент ни разу не выходил на связь: технических данных о нём нет вообще. */
    val neverReported: Boolean get() = reportedAt == null
}
