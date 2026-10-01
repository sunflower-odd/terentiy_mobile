package ru.terentiy.mobile.ui.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.terentiy.mobile.R
import ru.terentiy.mobile.core.time.TimeFormat
import ru.terentiy.mobile.ui.common.IconBadge
import ru.terentiy.mobile.ui.common.IconChevronRight
import ru.terentiy.mobile.ui.common.IconWarning
import ru.terentiy.mobile.ui.common.MessageBanner

@Composable
fun EventsScreen(
    contentPadding: PaddingValues,
    onOpenEvent: (String) -> Unit,
    viewModel: EventsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        val job = scope.launch { viewModel.keepFresh() }
        onPauseOrDispose { job.cancel() }
    }

    EventsContent(
        state = state,
        contentPadding = contentPadding,
        onRefresh = viewModel::refresh,
        onOpenEvent = onOpenEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsContent(
    state: EventsUiState,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onOpenEvent: (String) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        when {
            state.firstLoad -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }

            state.isEmpty -> EmptyHistory(errorRes = state.errorRes)

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.events_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }

                if (state.offline) {
                    item { MessageBanner(text = stringResource(R.string.home_offline_banner)) }
                }
                state.errorRes?.let { error ->
                    item { MessageBanner(text = stringResource(error)) }
                }

                items(state.rows, key = { it.event.id }) { row ->
                    EventListItem(row = row, onClick = { onOpenEvent(row.event.id) })
                }

                item {
                    // Прямо сказать, почему свежего события может ещё не быть, —
                    // дешевле, чем оставить пользователя думать, что всё сломалось.
                    Text(
                        text = stringResource(R.string.events_delay_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EventListItem(row: EventRow, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconBadge(
                icon = IconWarning,
                container = MaterialTheme.colorScheme.errorContainer,
                tint = MaterialTheme.colorScheme.error,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.event_fall),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = row.event.capturedAt?.let { TimeFormat.smart(it) }
                        ?: stringResource(R.string.event_time_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Непросмотренное помечается точкой: признака reviewed сервер не
            // хранит, «новое» — понятие этого устройства.
            if (!row.seen) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(10.dp),
                    content = {},
                )
            }
            Icon(
                imageVector = IconChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyHistory(errorRes: Int?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (errorRes != null) {
            MessageBanner(text = stringResource(errorRes))
        } else {
            Text(
                text = stringResource(R.string.events_empty_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.events_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
