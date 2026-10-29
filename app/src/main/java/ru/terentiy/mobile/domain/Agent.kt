package ru.terentiy.mobile.domain

/**
 * Агент установки — устройство наблюдения с камерой.
 *
 * [online] считает сервер: агент выходил на связь не позже порога
 * `agent_offline_after_seconds`. [hasFreshFrame] — есть ли на сервере свежий кадр
 * этой камеры (`current-frame` ответил кадром, а не `camera_unavailable`). Его
 * заполняет репозиторий агентов, в ответе `GET /api/v1/agents` такого поля нет.
 */
data class Agent(
    val id: String,
    val name: String,
    val cameraId: String,
    val online: Boolean,
    val hasFreshFrame: Boolean = false,
)
