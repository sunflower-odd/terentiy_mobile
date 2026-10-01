package ru.terentiy.mobile.ui.events

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.terentiy.mobile.core.error.messageRes
import ru.terentiy.mobile.data.repository.EventFeed
import ru.terentiy.mobile.domain.FallEvent

/** Строка списка: событие плюс локальная отметка просмотра. */
data class EventRow(val event: FallEvent, val seen: Boolean)

data class EventsUiState(
    val rows: List<EventRow> = emptyList(),
    val firstLoad: Boolean = true,
    val offline: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
) {
    val isEmpty: Boolean get() = !firstLoad && rows.isEmpty()
}

/**
 * История событий.
 *
 * Пагинации в контракте нет: сервер отдаёт всю историю домохозяйства от новых к
 * старым одним ответом. Поэтому ни курсора, ни кнопки «показать ещё» здесь нет —
 * список приходит целиком.
 */
@HiltViewModel
class EventsViewModel @Inject constructor(
    private val feed: EventFeed,
) : ViewModel() {

    val state: StateFlow<EventsUiState> = feed.state
        .map { feedState ->
            EventsUiState(
                rows = feedState.events.map { EventRow(it, feedState.seen.isSeen(it)) },
                firstLoad = feedState.firstLoad,
                offline = feedState.offline,
                errorRes = feedState.error?.takeIf { feedState.events.isEmpty() }?.messageRes(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventsUiState())

    suspend fun keepFresh() {
        feed.keepFresh()
    }

    fun refresh() {
        viewModelScope.launch { feed.refresh() }
    }
}
