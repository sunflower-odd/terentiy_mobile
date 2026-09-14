package ru.terentiy.mobile.domain

import java.time.Instant

/**
 * Агент установки и его последнее известное техническое состояние.
 *
 * [online] вычисляет backend: `TerentiyService.list_agents` сравнивает время последнего
 * heartbeat с текущим по порогу `agent_offline_after_seconds`. Часы телефона в расчёте
 * не участвуют, поэтому сбитое время на устройстве не искажает картину.
 */
data class Agent(
    val id: String,
    val name: String,
    val cameraId: String,
    val softwareVersion: String,
    val modelVersion: String?,
    val online: Boolean,
    val agentOnline: Tri,
    val cameraConnected: Tri,
    val streamActive: Tri,
    val mlReady: Tri,
    val queueSize: Int?,
    val reportedAt: Instant?,
    val lastFrameAt: Instant?,
) {
    /** Агент ни разу не выходил на связь: технических данных о нём нет вообще. */
    val neverReported: Boolean get() = reportedAt == null
}
