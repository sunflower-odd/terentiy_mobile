package ru.terentiy.mobile.ui.home

import androidx.annotation.StringRes
import ru.terentiy.mobile.R
import ru.terentiy.mobile.domain.WatchState

/**
 * Сопоставление состояния наблюдения с текстом на экране.
 *
 * «Наблюдение работает» означает, что устройство наблюдения на связи и кадры с
 * камеры доходят до сервера. Это не утверждение о том, что в кадре есть человек.
 */
@StringRes
fun WatchState.titleRes(): Int = when (this) {
    WatchState.NO_AGENT -> R.string.watch_no_agent
    WatchState.SERVER_UNAVAILABLE -> R.string.watch_server_unavailable
    WatchState.AGENT_OFFLINE -> R.string.watch_agent_offline
    WatchState.CAMERA_UNAVAILABLE -> R.string.watch_camera_unavailable
    WatchState.WATCHING -> R.string.watch_watching
}

@StringRes
fun WatchState.hintRes(): Int = when (this) {
    WatchState.NO_AGENT -> R.string.watch_no_agent_hint
    WatchState.SERVER_UNAVAILABLE -> R.string.watch_server_unavailable_hint
    WatchState.AGENT_OFFLINE -> R.string.watch_agent_offline_hint
    WatchState.CAMERA_UNAVAILABLE -> R.string.watch_camera_unavailable_hint
    WatchState.WATCHING -> R.string.watch_watching_hint
}
