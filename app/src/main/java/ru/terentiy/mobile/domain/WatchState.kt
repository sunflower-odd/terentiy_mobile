package ru.terentiy.mobile.domain

/**
 * Состояние наблюдения, как его видит пользователь: всё в порядке или нет и с
 * какой стороны проблема. Технических подробностей пользователь не видит.
 *
 * Порядок констант — порядок серьёзности, от худшего к лучшему: [WatchStatus.of]
 * при нескольких агентах берёт худшее.
 */
enum class WatchState {
    NO_AGENT,
    SERVER_UNAVAILABLE,
    AGENT_OFFLINE,
    CAMERA_UNAVAILABLE,
    WATCHING,
}

/** Вычисление состояния. Чистые функции — вся матрица покрывается unit-тестами. */
object WatchStatus {

    fun of(agent: Agent): WatchState = when {
        !agent.online -> WatchState.AGENT_OFFLINE
        !agent.hasFreshFrame -> WatchState.CAMERA_UNAVAILABLE
        else -> WatchState.WATCHING
    }

    /**
     * Сводное состояние установки.
     *
     * [serverReachable] — последний опрос сервера прошёл. Если нет, данные об
     * агентах устарели, и показывать по ним «всё работает» нельзя.
     */
    fun of(agents: List<Agent>, serverReachable: Boolean): WatchState = when {
        !serverReachable -> WatchState.SERVER_UNAVAILABLE
        agents.isEmpty() -> WatchState.NO_AGENT
        else -> agents.map(::of).minBy { it.ordinal }
    }
}
