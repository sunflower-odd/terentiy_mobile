package ru.terentiy.mobile.domain

import java.time.Duration

/**
 * Состояние контура наблюдения, как его видит пользователь.
 *
 * Реализованный контракт 1.0.0 не отдаёт состояние наблюдения полем — управления
 * наблюдением в нём нет вовсе. Поэтому состояние **вычисляется на клиенте** из
 * пяти полей агента. Это не обходной путь, а удачная граница: если backend когда-
 * нибудь вернёт `observation.state`, поменяются мапперы и одна перегрузка
 * [WatchStatus.of], а экраны останутся как есть.
 *
 * Порядок констант — порядок серьёзности, от худшего к лучшему. [WatchStatus.worstOf]
 * опирается на него напрямую, поэтому переставлять значения нельзя.
 *
 * [UNKNOWN] стоит выше [AGENT_OFFLINE] намеренно: про выключенный агент известно,
 * что он выключен, а про молчащий неизвестно даже то, работал ли контур когда-либо.
 */
enum class WatchState {
    NO_AGENT,
    UNKNOWN,
    AGENT_OFFLINE,
    CAMERA_UNAVAILABLE,
    STREAM_STALLED,
    FRAMES_DELAYED,
    WATCHING,
}

/**
 * Пороги, которых нет в контракте.
 *
 * Сервер не публикует ни допустимое отставание кадров, ни размер очереди, при
 * котором пора беспокоиться. Значения выведены из фактов установки:
 * heartbeat агента — 10 секунд (`terentiy_ai/agent/config/agent.example.toml`),
 * порог offline на сервере — 30 секунд. Минута отставания означает, что кадры
 * перестали доходить, а не что один из них задержался.
 *
 * Числа держатся здесь, а не в теле функции, чтобы их можно было подменить в
 * тестах и чтобы через месяц было видно, откуда они взялись.
 */
data class WatchPolicy(
    val frameLagLimit: Duration = Duration.ofSeconds(60),
    val queueWarnAt: Int = 20,
) {
    companion object {
        val Default = WatchPolicy()
    }
}

/**
 * Вычисление состояния контура.
 *
 * Чистая функция без зависимостей от Android — вся матрица покрывается обычными
 * unit-тестами.
 */
object WatchStatus {

    /**
     * Состояние одного агента.
     *
     * Отставание кадров считается как `reportedAt − lastFrameAt`: **оба времени
     * серверные**, поэтому вердикт не зависит от часов телефона. Сравнение с
     * `Instant.now()` дало бы ложное «кадры не идут» на устройстве со сбитым
     * временем — часы телефона участвуют только в отображении.
     */
    fun of(agent: Agent, policy: WatchPolicy = WatchPolicy.Default): WatchState = when {
        agent.neverReported -> WatchState.UNKNOWN
        !agent.online -> WatchState.AGENT_OFFLINE
        agent.cameraConnected == Tri.NO -> WatchState.CAMERA_UNAVAILABLE
        agent.streamActive == Tri.NO -> WatchState.STREAM_STALLED

        // Агент отчитался, но про камеру или поток ничего не сообщил: это не
        // поломка, а неполные данные, и говорить «всё работает» нельзя.
        !agent.cameraConnected.isKnown || !agent.streamActive.isKnown -> WatchState.UNKNOWN

        // Кадров на сервере нет вообще, хотя агент считает, что поток идёт.
        agent.lastFrameAt == null -> WatchState.FRAMES_DELAYED

        frameLag(agent) > policy.frameLagLimit -> WatchState.FRAMES_DELAYED

        // Очередь на агенте растёт: он снимает, но отправить не может. Падение в
        // таком состоянии не будет обнаружено, хотя формально всё «включено».
        (agent.queueSize ?: 0) > policy.queueWarnAt -> WatchState.FRAMES_DELAYED

        else -> WatchState.WATCHING
    }

    /**
     * Сводное состояние установки.
     *
     * Агент обычно один, но полагаться на это нельзя: контракт возвращает список.
     * Пустой список — отдельное состояние, а не «всё хорошо»; при нескольких
     * агентах берётся худшее из состояний.
     */
    fun worstOf(agents: List<Agent>, policy: WatchPolicy = WatchPolicy.Default): WatchState =
        if (agents.isEmpty()) {
            WatchState.NO_AGENT
        } else {
            agents.map { of(it, policy) }.minBy { it.ordinal }
        }

    /**
     * Насколько кадры на сервере отстают от последнего heartbeat.
     *
     * [Duration.ZERO], если чего-то из двух времён нет: отсутствие данных не
     * должно выглядеть как отставание.
     */
    fun frameLag(agent: Agent): Duration {
        val reported = agent.reportedAt ?: return Duration.ZERO
        val frame = agent.lastFrameAt ?: return Duration.ZERO
        val lag = Duration.between(frame, reported)
        return if (lag.isNegative) Duration.ZERO else lag
    }
}
