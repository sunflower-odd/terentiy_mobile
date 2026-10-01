package ru.terentiy.mobile.ui.clip

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.terentiy.mobile.core.error.AppError
import ru.terentiy.mobile.core.error.messageRes
import ru.terentiy.mobile.data.repository.EventsRepository
import ru.terentiy.mobile.domain.MediaPayload

data class ClipUiState(
    val downloading: Boolean = true,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val file: File? = null,
    @param:StringRes val errorRes: Int? = null,
) {
    /** Доля загрузки, если сервер сообщил размер. */
    val progress: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { (downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
}

/**
 * Загрузка и проигрывание клипа.
 *
 * Клип скачивается целиком в файл, и только потом проигрывается: сервер не
 * поддерживает Range, а значит перемотка по сети невозможна, и попытка играть
 * прямо из URL дала бы паузы на каждом касании ползунка.
 */
@HiltViewModel
class ClipViewModel @Inject constructor(
    private val events: EventsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mediaId: String = savedStateHandle["mediaId"] ?: ""

    private val _state = MutableStateFlow(ClipUiState())
    val state: StateFlow<ClipUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { ClipUiState(downloading = true) }
        viewModelScope.launch {
            try {
                val payload = events.media(mediaId) { downloaded, total ->
                    _state.update { it.copy(downloadedBytes = downloaded, totalBytes = total) }
                }
                when (payload) {
                    is MediaPayload.Video -> _state.update {
                        it.copy(downloading = false, file = payload.file)
                    }

                    is MediaPayload.Image -> _state.update {
                        it.copy(
                            downloading = false,
                            errorRes = AppError.MediaUnsupported("image/jpeg").messageRes(),
                        )
                    }
                }
            } catch (error: AppError) {
                _state.update { it.copy(downloading = false, errorRes = error.messageRes()) }
            }
        }
    }
}
