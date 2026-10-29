package ru.terentiy.mobile.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import ru.terentiy.mobile.domain.WatchState
import ru.terentiy.mobile.ui.common.AlertCard
import ru.terentiy.mobile.ui.common.HeroMood
import ru.terentiy.mobile.ui.common.MessageBanner
import ru.terentiy.mobile.ui.common.SecondaryButton
import ru.terentiy.mobile.ui.common.StatusHero

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenEvent: (String) -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Опрос живёт, только пока экран на переднем плане: в фоне приложение о
    // событиях не узнаёт — push-канала в контракте нет.
    LifecycleResumeEffect(Unit) {
        val job = scope.launch { viewModel.keepFresh() }
        onPauseOrDispose { job.cancel() }
    }

    HomeContent(
        state = state,
        contentPadding = contentPadding,
        onRefresh = viewModel::refresh,
        onOpenEvent = onOpenEvent,
        onOpenHistory = onOpenHistory,
        onDismissAlert = viewModel::dismissAlert,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onDismissAlert: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        if (state.firstLoad) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
            return@PullToRefreshBox
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.brand_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = state.userName.takeIf { it.isNotBlank() }
                    ?.let { stringResource(R.string.home_greeting, it) }
                    ?: stringResource(R.string.brand_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.errorRes?.let { MessageBanner(text = stringResource(it)) }

            state.alert?.let { alert ->
                AlertCard(
                    title = stringResource(R.string.event_fall),
                    statement = stringResource(R.string.event_basis),
                    time = alert.capturedAt?.let { TimeFormat.smart(it) }.orEmpty(),
                    onOpen = { onOpenEvent(alert.id) },
                    onDismiss = onDismissAlert,
                )
            }

            StatusHero(
                mood = when {
                    state.alert != null -> HeroMood.ALERT
                    state.watchState == WatchState.WATCHING -> HeroMood.WATCHING
                    else -> HeroMood.SLEEPING
                }
            )

            Text(
                text = stringResource(state.watchState.titleRes()),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(state.watchState.hintRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            val agent = state.agent
            if (state.watchState == WatchState.NO_AGENT) {
                MessageBanner(
                    text = stringResource(R.string.home_no_agent),
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (agent != null) {
                Text(
                    text = agent.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(Modifier.height(4.dp))

            SecondaryButton(
                text = if (state.unseenCount > 0) {
                    stringResource(R.string.home_history_with_unseen, state.unseenCount)
                } else {
                    stringResource(R.string.home_history)
                },
                onClick = onOpenHistory,
            )
        }
    }
}
