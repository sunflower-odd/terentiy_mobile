package ru.terentiy.mobile.domain

/**
 * Состояние наблюдения, как его видит пользователь.
 *
 * На Stage 01 это техническое здоровье контура, а не присутствие человека: backend
 * отдаёт `person_present` только внутри `latest-image`, вместе с двумя JPEG в base64.
 * См. docs/adr/0001-istochnik-statusa.md.
 *
 * Порядок констант — это порядок серьёзности, от худшего к лучшему. [worstOf]
 * опирается на него напрямую, поэтому переставлять значения нельзя.
 */
enum class WatchState {
    NO_AGENT,
    AGENT_OFFLINE,
    CAMERA_UNAVAILABLE,
    ML_NOT_READY,
    WATCHING,
}

/**
 * Вычисление состояния наблюдения.
 *
 * Чистая функция без зависимостей от Android — вся матрица состояний покрывается
 * обычными unit-тестами. Когда backend научится отдавать присутствие человека,
 * сюда добавляются новые состояния, а экран не переписывается.
 */
object WatchStatus {

    /** Состояние одного агента. */
    fun of(agent: Agent): WatchState = when {
        !agent.online -> WatchState.AGENT_OFFLINE
        !agent.cameraConnected.isYes -> WatchState.CAMERA_UNAVAILABLE
        !agent.streamActive.isYes -> WatchState.CAMERA_UNAVAILABLE
        !agent.mlReady.isYes -> WatchState.ML_NOT_READY
        else -> WatchState.WATCHING
    }

    /**
     * Сводное состояние установки.
     *
     * На Stage 01 агент всегда один, но полагаться на это нельзя: `GET /api/v1/agents`
     * возвращает список. Пустой список — это отдельное состояние, а не «всё хорошо»;
     * при нескольких агентах берётся худшее из состояний.
     */
    fun worstOf(agents: List<Agent>): WatchState =
        if (agents.isEmpty()) {
            WatchState.NO_AGENT
        } else {
            agents.map(::of).minBy { it.ordinal }
        }
}
