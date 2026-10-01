package ru.terentiy.mobile.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.terentiy.mobile.data.local.AppSettings
import ru.terentiy.mobile.data.local.DataSourceMode
import ru.terentiy.mobile.data.local.MediaCache
import ru.terentiy.mobile.data.repository.AuthRepository

data class ProfileUiState(
    val userName: String = "",
    val householdId: String = "",
    val serverUrl: String = "",
    val demoMode: Boolean = true,
    val cacheMegabytes: Long = 0,
    val loggingOut: Boolean = false,
    val loggedOut: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val settings: AppSettings,
    private val mediaCache: MediaCache,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settings.userName,
                settings.householdId,
                settings.serverUrl,
                settings.dataSourceMode,
            ) { name, household, url, mode ->
                ProfileUiState(
                    userName = name,
                    householdId = household,
                    serverUrl = url,
                    demoMode = mode == DataSourceMode.DEMO,
                )
            }.collect { fresh ->
                _state.update {
                    fresh.copy(
                        cacheMegabytes = megabytes(),
                        loggingOut = it.loggingOut,
                        loggedOut = it.loggedOut,
                    )
                }
            }
        }
    }

    /** Скачанные клипы копятся молча, и их размер стоит показывать. */
    private fun megabytes(): Long = mediaCache.sizeBytes() / 1024 / 1024

    fun clearMediaCache() {
        mediaCache.clear()
        _state.update { it.copy(cacheMegabytes = megabytes()) }
    }

    fun setDemoMode(enabled: Boolean) {
        viewModelScope.launch {
            settings.saveDataSourceMode(if (enabled) DataSourceMode.DEMO else DataSourceMode.NETWORK)
        }
    }

    /**
     * Выход.
     *
     * Маршрута выхода в контракте нет, и токен на сервере не отзывается — он
     * останется действительным навсегда. Всё, что может клиент, — стереть токен
     * у себя; привязка устройства при этом сохраняется, и для возврата нужен
     * только PIN. См. docs/backlog-mobile.md.
     */
    fun logout() {
        if (_state.value.loggingOut) return
        _state.update { it.copy(loggingOut = true) }
        viewModelScope.launch {
            auth.logout()
            _state.update { it.copy(loggingOut = false, loggedOut = true) }
        }
    }
}
