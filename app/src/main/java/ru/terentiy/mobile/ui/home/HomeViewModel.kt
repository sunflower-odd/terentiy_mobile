package ru.terentiy.mobile.ui.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.terentiy.mobile.core.error.AppError
import ru.terentiy.mobile.core.error.messageRes
import ru.terentiy.mobile.data.local.AppSettings
import ru.terentiy.mobile.data.repository.AgentsRepository
import ru.terentiy.mobile.domain.Agent
import ru.terentiy.mobile.domain.LatestImage
import ru.terentiy.mobile.domain.WatchState
import ru.terentiy.mobile.domain.WatchStatus

data class HomeUiState(
    val userName: String = "",
    val agents: List<Agent> = emptyList(),
    val watchState: WatchState = WatchState.NO_AGENT,
    val firstLoad: Boolean = true,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
    val sessionExpired: Boolean = false,
)

/** Состояние листа с кадром. Кадр запрашивается только по действию пользователя. */
sealed interface ImageUiState {
    data object Hidden : ImageUiState
    data class Loading(val agentId: String) : ImageUiState
    data class Ready(val image: LatestImage) : ImageUiState
    data class Failed(@param:StringRes val errorRes: Int) : ImageUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val agentsRepository: AgentsRepository,
    private val settings: AppSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _image = MutableStateFlow<ImageUiState>(ImageUiState.Hidden)
    val image: StateFlow<ImageUiState> = _image.asStateFlow()

    private var pollingJob: Job? = null

    init {
        viewModelScope.launch {
            _state.update { it.copy(userName = settings.userName.first()) }
        }
    }

    /**
     * Опрос статуса, пока экран виден.
     *
     * Никаких фоновых сервисов: на Stage 01 приложение обновляет данные только тогда,
     * когда пользователь на них смотрит. Запускается из `repeatOnLifecycle(STARTED)`
     * и отменяется вместе с ним.
     */
    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                load(userInitiated = false)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun refresh() {
        viewModelScope.launch { load(userInitiated = true) }
    }

    private suspend fun load(userInitiated: Boolean) {
        if (userInitiated) _state.update { it.copy(refreshing = true) }
        try {
            val agents = agentsRepository.agents()
            _state.update {
                it.copy(
                    agents = agents,
                    watchState = WatchStatus.worstOf(agents),
                    firstLoad = false,
                    refreshing = false,
                    offline = false,
                    errorRes = null,
                )
            }
        } catch (error: AppError) {
            handle(error, userInitiated)
        }
    }

    /**
     * Потеря связи не стирает уже показанные данные: пользователю честнее видеть
     * последнее известное состояние с пометкой, чем пустой экран.
     */
    private fun handle(error: AppError, userInitiated: Boolean) {
        when (error) {
            AppError.Unauthorized -> _state.update {
                it.copy(refreshing = false, sessionExpired = true)
            }

            AppError.Network -> _state.update {
                it.copy(
                    refreshing = false,
                    firstLoad = false,
                    offline = true,
                    errorRes = if (it.agents.isEmpty()) error.messageRes() else null,
                )
            }

            else -> _state.update {
                it.copy(
                    refreshing = false,
                    firstLoad = false,
                    errorRes = if (userInitiated || it.agents.isEmpty()) error.messageRes() else null,
                )
            }
        }
    }

    fun requestImage(agentId: String) {
        _image.value = ImageUiState.Loading(agentId)
        viewModelScope.launch {
            _image.value = try {
                ImageUiState.Ready(agentsRepository.latestImage(agentId))
            } catch (error: AppError) {
                if (error is AppError.Unauthorized) {
                    _state.update { it.copy(sessionExpired = true) }
                }
                ImageUiState.Failed(error.messageRes())
            }
        }
    }

    /** Закрытие листа освобождает байты кадров: два JPEG живут в памяти процесса. */
    fun dismissImage() {
        _image.value = ImageUiState.Hidden
    }

    private companion object {
        const val POLL_INTERVAL_MS = 20_000L
    }
}
