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
import ru.terentiy.mobile.data.local.AppSettings
import ru.terentiy.mobile.data.local.DataSourceMode
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
    val checkingServer: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
    @param:StringRes val noticeRes: Int? = null,
    val activated: Boolean = false,
    val demoMode: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !loading && !checkingServer &&
            Validation.activationError(serverUrl, installationCode, pin, pinRepeat) == null
}

/**
 * Подключение устройства к установке.
 *
 * Имя пользователя на сервер не уходит: в `MobileActivationRequest` его нет.
 * Оно хранится локально и нужно только для приветствия на главном экране.
 */
@HiltViewModel
class ActivationViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val settings: AppSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(ActivationUiState())
    val state: StateFlow<ActivationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = auth.serverUrl.first()
            _state.update { if (it.serverUrl.isEmpty()) it.copy(serverUrl = saved) else it }
        }
        // Переключатель источника данных стоит на этом экране, а не только в
        // Профиле: до активации Профиля не существует, и выбор «сервер или
        // показ без сервера» делается именно здесь.
        viewModelScope.launch {
            settings.dataSourceMode.collect { mode ->
                _state.update { it.copy(demoMode = mode == DataSourceMode.DEMO) }
            }
        }
    }

    fun onDemoModeChange(enabled: Boolean) {
        viewModelScope.launch {
            settings.saveDataSourceMode(if (enabled) DataSourceMode.DEMO else DataSourceMode.NETWORK)
        }
    }

    fun onServerUrlChange(value: String) = _state.update {
        it.copy(serverUrl = value, errorRes = null, noticeRes = null)
    }

    fun onCodeChange(value: String) = _state.update {
        it.copy(installationCode = value, errorRes = null)
    }

    fun onNameChange(value: String) = _state.update { it.copy(userName = value) }

    fun onPinChange(value: String) = _state.update { it.copy(pin = value.asPinInput(), errorRes = null) }

    fun onPinRepeatChange(value: String) = _state.update {
        it.copy(pinRepeat = value.asPinInput(), errorRes = null)
    }

    /**
     * Проверка адреса до активации.
     *
     * `/health` — единственный маршрут без авторизации, и он отвечает на вопрос,
     * который иначе не различить: сервер недоступен или код установки не подошёл.
     */
    fun checkServer() {
        val url = _state.value.serverUrl
        if (!Validation.isServerUrlValid(url)) {
            _state.update { it.copy(errorRes = R.string.validation_server_url, noticeRes = null) }
            return
        }
        _state.update { it.copy(checkingServer = true, errorRes = null, noticeRes = null) }
        viewModelScope.launch {
            try {
                auth.checkServer(url)
                _state.update {
                    it.copy(checkingServer = false, noticeRes = R.string.activation_server_ok)
                }
            } catch (error: AppError) {
                _state.update { it.copy(checkingServer = false, errorRes = error.messageRes()) }
            }
        }
    }

    fun submit() {
        val current = _state.value
        Validation.activationError(
            serverUrl = current.serverUrl,
            installationCode = current.installationCode,
            pin = current.pin,
            pinRepeat = current.pinRepeat,
        )?.let { error ->
            _state.update { it.copy(errorRes = error) }
            return
        }

        _state.update { it.copy(loading = true, errorRes = null, noticeRes = null) }
        viewModelScope.launch {
            try {
                auth.activate(
                    serverUrl = current.serverUrl,
                    installationCode = current.installationCode,
                    userName = current.userName,
                    pin = current.pin,
                )
                _state.update { it.copy(loading = false, activated = true) }
            } catch (error: AppError) {
                // PIN не стирается: пользователь его только что придумал, и
                // заставлять вводить заново из-за неверного кода установки — грубо.
                _state.update { it.copy(loading = false, errorRes = error.messageRes()) }
            }
        }
    }
}
