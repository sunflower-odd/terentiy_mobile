package ru.terentiy.mobile.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.terentiy.mobile.R
import ru.terentiy.mobile.core.time.TimeFormat
import ru.terentiy.mobile.domain.Agent
import ru.terentiy.mobile.domain.WatchState
import ru.terentiy.mobile.ui.common.MessageBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSessionExpired: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val image by viewModel.image.collectAsStateWithLifecycle()

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) onSessionExpired()
    }

    // Опрос идёт, только пока экран на переднем плане. Фоновых сервисов на Stage 01 нет.
    LifecycleResumeEffect(Unit) {
        viewModel.startPolling()
        onPauseOrDispose { viewModel.stopPolling() }
    }

    ImageSheet(
        state = image,
        onDismiss = viewModel::dismissImage,
    )

    HomeContent(
        state = state,
        onRefresh = viewModel::refresh,
        onRequestImage = viewModel::requestImage,
    )
}

/**
 * Содержимое главного экрана без зависимости от ViewModel: проверяется
 * Compose-тестами без поднятия графа зависимостей и работает в превью.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onRequestImage: (String) -> Unit,
) {
    Scaffold { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.firstLoad) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
                return@PullToRefreshBox
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = state.userName.takeIf { it.isNotBlank() }
                            ?.let { stringResource(R.string.home_greeting, it) }
                            ?: stringResource(R.string.home_greeting_anonymous),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }

                if (state.offline) {
                    item { MessageBanner(text = stringResource(R.string.home_offline_banner)) }
                }

                state.errorRes?.let { error ->
                    item { MessageBanner(text = stringResource(error)) }
                }

                item { WatchStateCard(state.watchState) }

                items(state.agents, key = { it.id }) { agent ->
                    AgentCard(
                        agent = agent,
                        onRequestImage = { onRequestImage(agent.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchStateCard(state: WatchState) {
    val container = when (state) {
        WatchState.WATCHING -> MaterialTheme.colorScheme.primaryContainer
        WatchState.ML_NOT_READY -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when (state) {
        WatchState.WATCHING -> MaterialTheme.colorScheme.onPrimaryContainer
        WatchState.ML_NOT_READY -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.home_status_title),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = stringResource(state.titleRes()),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(state.hintRes()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AgentCard(agent: Agent, onRequestImage: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = agent.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.home_camera, agent.cameraId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.home_software_version, agent.softwareVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            FlagRow(stringResource(R.string.home_flag_camera), stringResource(agent.cameraConnected.labelRes()))
            FlagRow(stringResource(R.string.home_flag_stream), stringResource(agent.streamActive.labelRes()))
            FlagRow(stringResource(R.string.home_flag_ml), stringResource(agent.mlReady.labelRes()))

            Text(
                text = agent.lastFrameAt
                    ?.let { stringResource(R.string.home_last_frame, TimeFormat.smart(it)) }
                    ?: stringResource(
                        if (agent.neverReported) R.string.home_no_data_yet
                        else R.string.home_last_frame_never
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(onClick = onRequestImage, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_get_view))
            }
        }
    }
}

@Composable
private fun FlagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
