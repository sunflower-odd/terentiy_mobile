package ru.terentiy.mobile.ui.pin

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.terentiy.mobile.core.error.AppError
import ru.terentiy.mobile.core.error.messageRes
import ru.terentiy.mobile.data.repository.AuthRepository
import ru.terentiy.mobile.ui.common.Validation
import ru.terentiy.mobile.ui.common.asPinInput

data class PinUiState(
    val pin: String = "",
    val loading: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
    val lockSecondsLeft: Int = 0,
    val loggedIn: Boolean = false,
    val forgotten: Boolean = false,
) {
    val locked: Boolean get() = lockSecondsLeft > 0
    val canSubmit: Boolean get() = !loading && !locked && Validation.isPinValid(pin)
}

/**
 * Вход по PIN.
 *
 * Задержка после неудачных попыток — мера против случайного перебора на самом
 * устройстве, а не защита: backend не ограничивает частоту обращений к
 * `POST /api/v1/mobile/login`, и шестизначный PIN перебирается запросами в обход
 * приложения. Настоящее ограничение нужно на сервере, см. docs/backlog-mobile.md §4.
 */
@HiltViewModel
class PinViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PinUiState())
    val state: StateFlow<PinUiState> = _state.asStateFlow()

    private var failedAttempts = 0

    fun onPinChange(value: String) = _state.update { it.copy(pin = value.asPinInput(), errorRes = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return

        _state.update { it.copy(loading = true, errorRes = null) }
        viewModelScope.launch {
            try {
                auth.login(current.pin)
                failedAttempts = 0
                _state.update { it.copy(loading = false, loggedIn = true) }
            } catch (error: AppError) {
                if (error is AppError.InvalidPin) failedAttempts++
                _state.update { it.copy(loading = false, pin = "", errorRes = error.messageRes()) }
                applyLockIfNeeded()
            }
        }
    }

    /** Полный сброс привязки: следующий экран — активация. */
    fun forgetActivation() {
        viewModelScope.launch {
            auth.forgetActivation()
            _state.update { it.copy(forgotten = true) }
        }
    }

    private fun applyLockIfNeeded() {
        val seconds = lockSeconds(failedAttempts)
        if (seconds <= 0) return
        viewModelScope.launch {
            for (left in seconds downTo 1) {
                _state.update { it.copy(lockSecondsLeft = left) }
                delay(1_000)
            }
            _state.update { it.copy(lockSecondsLeft = 0) }
        }
    }

    private fun lockSeconds(attempts: Int): Int = when {
        attempts < FREE_ATTEMPTS -> 0
        else -> minOf(MAX_LOCK_SECONDS, BASE_LOCK_SECONDS * (attempts - FREE_ATTEMPTS + 1))
    }

    private companion object {
        const val FREE_ATTEMPTS = 3
        const val BASE_LOCK_SECONDS = 15
        const val MAX_LOCK_SECONDS = 120
    }
}
