package ru.terentiy.mobile.ui.eventdetail

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.terentiy.mobile.core.error.AppError
import ru.terentiy.mobile.core.error.messageRes
import ru.terentiy.mobile.data.repository.EventFeed
import ru.terentiy.mobile.data.repository.EventsRepository
import ru.terentiy.mobile.domain.FallEvent
import ru.terentiy.mobile.domain.MediaPayload

data class EventDetailUiState(
    val loading: Boolean = true,
    val event: FallEvent? = null,
    val frame: ByteArray? = null,
    val frameLoading: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
    @param:StringRes val frameErrorRes: Int? = null,
) {
    // Массив байтов сравнивается по ссылке, из-за чего Compose считал бы
    // состояние изменившимся зря.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EventDetailUiState) return false
        return loading == other.loading &&
            event == other.event &&
            (frame?.contentEquals(other.frame) ?: (other.frame == null)) &&
            frameLoading == other.frameLoading &&
            errorRes == other.errorRes &&
            frameErrorRes == other.frameErrorRes
    }

    override fun hashCode(): Int {
        var result = loading.hashCode()
        result = 31 * result + (event?.hashCode() ?: 0)
        result = 31 * result + (frame?.contentHashCode() ?: 0)
        result = 31 * result + frameLoading.hashCode()
        result = 31 * result + (errorRes ?: 0)
        result = 31 * result + (frameErrorRes ?: 0)
        return result
    }
}

/**
 * Карточка события.
 *
 * Событие берётся из общей ленты, а не запрашивается отдельно: маршрута
 * `GET /events/{id}` в контракте нет. Если лента ещё не читалась — например,
 * процесс подняли по возврату из истории навигации, — делается одно обновление.
 */
@HiltViewModel
class EventDetailViewModel @Inject constructor(
    private val feed: EventFeed,
    private val events: EventsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val eventId: String = savedStateHandle["eventId"] ?: ""

    private val _state = MutableStateFlow(EventDetailUiState())
    val state: StateFlow<EventDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, errorRes = null) }
        viewModelScope.launch {
            val event = feed.cached(eventId) ?: run {
                feed.refresh()
                feed.cached(eventId)
            }
            if (event == null) {
                _state.update { it.copy(loading = false, errorRes = AppError.EventNotFound.messageRes()) }
                return@launch
            }
            _state.update { it.copy(loading = false, event = event) }
            // Открыли карточку — значит событие просмотрено. Отметка постоянная,
            // в отличие от закрытия баннера крестиком.
            feed.markSeen(event.id)
            loadFrame(event)
        }
    }

    private suspend fun loadFrame(event: FallEvent) {
        _state.update { it.copy(frameLoading = true, frameErrorRes = null) }
        try {
            val payload = events.media(event.frameMediaId)
            val bytes = (payload as? MediaPayload.Image)?.bytes
            _state.update {
                it.copy(
                    frameLoading = false,
                    frame = bytes,
                    frameErrorRes = if (bytes == null) AppError.MediaUnsupported(null).messageRes() else null,
                )
            }
        } catch (error: AppError) {
            _state.update { it.copy(frameLoading = false, frameErrorRes = error.messageRes()) }
        }
    }

    fun retryFrame() {
        val event = _state.value.event ?: return
        viewModelScope.launch { loadFrame(event) }
    }
}
