package ru.terentiy.mobile.ui.home

import androidx.annotation.StringRes
import ru.terentiy.mobile.R
import ru.terentiy.mobile.domain.Tri
import ru.terentiy.mobile.domain.WatchState
import ru.terentiy.mobile.ui.common.StatusMark

/**
 * Сопоставление состояния контура с текстом на экране.
 *
 * «Наблюдение работает» означает, что контур жив: агент на связи, камера
 * подключена, кадры доходят до сервера и анализируются. Это не утверждение о
 * том, что в кадре есть человек, и не обещание, что падение будет замечено
 * мгновенно.
 */
@StringRes
fun WatchState.titleRes(): Int = when (this) {
    WatchState.NO_AGENT -> R.string.watch_no_agent
    WatchState.UNKNOWN -> R.string.watch_unknown
    WatchState.AGENT_OFFLINE -> R.string.watch_agent_offline
    WatchState.CAMERA_UNAVAILABLE -> R.string.watch_camera_unavailable
    WatchState.STREAM_STALLED -> R.string.watch_stream_stalled
    WatchState.FRAMES_DELAYED -> R.string.watch_frames_delayed
    WatchState.WATCHING -> R.string.watch_watching
}

@StringRes
fun WatchState.hintRes(): Int = when (this) {
    WatchState.NO_AGENT -> R.string.watch_no_agent_hint
    WatchState.UNKNOWN -> R.string.watch_unknown_hint
    WatchState.AGENT_OFFLINE -> R.string.watch_agent_offline_hint
    WatchState.CAMERA_UNAVAILABLE -> R.string.watch_camera_unavailable_hint
    WatchState.STREAM_STALLED -> R.string.watch_stream_stalled_hint
    WatchState.FRAMES_DELAYED -> R.string.watch_frames_delayed_hint
    WatchState.WATCHING -> R.string.watch_watching_hint
}

/** Отметка справки в строке состояния: «неизвестно» отличается от «нет». */
fun Tri.mark(): StatusMark = when (this) {
    Tri.YES -> StatusMark.OK
    Tri.NO -> StatusMark.BAD
    Tri.UNKNOWN -> StatusMark.UNKNOWN
}

@StringRes
fun Tri.cameraLabelRes(): Int = when (this) {
    Tri.YES -> R.string.home_camera_connected
    Tri.NO -> R.string.home_camera_unavailable
    Tri.UNKNOWN -> R.string.home_camera_unknown
}

@StringRes
fun Tri.streamLabelRes(): Int = when (this) {
    Tri.YES -> R.string.home_stream_active
    Tri.NO -> R.string.home_stream_stalled
    Tri.UNKNOWN -> R.string.home_stream_unknown
}
