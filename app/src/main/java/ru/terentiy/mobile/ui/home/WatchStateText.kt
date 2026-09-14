package ru.terentiy.mobile.ui.home

import androidx.annotation.StringRes
import ru.terentiy.mobile.R
import ru.terentiy.mobile.domain.Tri
import ru.terentiy.mobile.domain.WatchState

/**
 * Сопоставление доменного состояния с текстом на экране.
 *
 * «Наблюдение активно» означает, что контур работает: камера на связи и кадры
 * анализируются. Это не утверждение о том, что в кадре есть человек — такого
 * ответа backend на Stage 01 не даёт. См. docs/adr/0001-istochnik-statusa.md.
 */
@StringRes
fun WatchState.titleRes(): Int = when (this) {
    WatchState.NO_AGENT -> R.string.home_state_no_agent
    WatchState.AGENT_OFFLINE -> R.string.home_state_agent_offline
    WatchState.CAMERA_UNAVAILABLE -> R.string.home_state_camera_unavailable
    WatchState.ML_NOT_READY -> R.string.home_state_ml_not_ready
    WatchState.WATCHING -> R.string.home_state_watching
}

@StringRes
fun WatchState.hintRes(): Int = when (this) {
    WatchState.NO_AGENT -> R.string.home_state_no_agent_hint
    WatchState.AGENT_OFFLINE -> R.string.home_state_agent_offline_hint
    WatchState.CAMERA_UNAVAILABLE -> R.string.home_state_camera_unavailable_hint
    WatchState.ML_NOT_READY -> R.string.home_state_ml_not_ready_hint
    WatchState.WATCHING -> R.string.home_state_watching_hint
}

@StringRes
fun Tri.labelRes(): Int = when (this) {
    Tri.YES -> R.string.home_flag_yes
    Tri.NO -> R.string.home_flag_no
    Tri.UNKNOWN -> R.string.home_flag_unknown
}
