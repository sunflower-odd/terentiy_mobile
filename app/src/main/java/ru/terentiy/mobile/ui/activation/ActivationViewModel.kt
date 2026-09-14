package ru.terentiy.mobile.ui.activation

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.terentiy.mobile.R
import ru.terentiy.mobile.core.error.AppError
import ru.terentiy.mobile.core.error.messageRes
import ru.terentiy.mobile.data.repository.AuthRepository
import ru.terentiy.mobile.ui.common.Validation
import ru.terentiy.mobile.ui.common.asPinInput

data class ActivationUiState(
    val serverUrl: String = "",
    val installationCode: String = "",
    val userName: String = "",
    val pin: String = "",
    val pinRepeat: String = "",
    val loading: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
    val activated: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !loading &&
            Validation.isServerUrlValid(serverUrl) &&
            installationCode.isNotBlank() &&
            userName.isNotBlank() &&
            Validation.isPinValid(pin) &&
            pin == pinRepeat
}

@HiltViewModel
class ActivationViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ActivationUiState())
    val state: StateFlow<ActivationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(serverUrl = auth.serverUrl.first()) }
        }
    }

    fun onServerUrlChange(value: String) = _state.update { it.copy(serverUrl = value, errorRes = null) }

    fun onCodeChange(value: String) = _state.update { it.copy(installationCode = value.trim(), errorRes = null) }

    fun onNameChange(value: String) = _state.update { it.copy(userName = value, errorRes = null) }

    fun onPinChange(value: String) = _state.update { it.copy(pin = value.asPinInput(), errorRes = null) }

    fun onPinRepeatChange(value: String) = _state.update { it.copy(pinRepeat = value.asPinInput(), errorRes = null) }

    fun submit() {
        val current = _state.value
        if (current.loading) return

        validate(current)?.let { error ->
            _state.update { it.copy(errorRes = error) }
            return
        }

        _state.update { it.copy(loading = true, errorRes = null) }
        viewModelScope.launch {
            try {
                auth.activate(
                    serverUrl = current.serverUrl.trim().trimEnd('/'),
                    installationCode = current.installationCode,
                    userName = current.userName.trim(),
                    pin = current.pin,
                )
                _state.update { it.copy(loading = false, activated = true) }
            } catch (error: AppError) {
                _state.update { it.copy(loading = false, errorRes = error.messageRes()) }
            }
        }
    }

    @StringRes
    private fun validate(state: ActivationUiState): Int? = when {
        !Validation.isServerUrlValid(state.serverUrl) -> R.string.validation_server_url
        state.installationCode.isBlank() -> R.string.validation_code_required
        state.userName.isBlank() -> R.string.validation_name_required
        !Validation.isPinValid(state.pin) -> R.string.validation_pin_six_digits
        state.pin != state.pinRepeat -> R.string.validation_pin_mismatch
        else -> null
    }
}
