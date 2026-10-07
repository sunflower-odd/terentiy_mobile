package ru.terentiy.mobile.ui.eventdetail

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.terentiy.mobile.R
import ru.terentiy.mobile.core.time.TimeFormat
import ru.terentiy.mobile.ui.common.IconArrowBack
import ru.terentiy.mobile.ui.common.MessageBanner
import ru.terentiy.mobile.ui.common.PrimaryButton

@Composable
fun EventDetailScreen(
    onBack: () -> Unit,
    onPlayClip: (String) -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EventDetailContent(
        state = state,
        onBack = onBack,
        onPlayClip = onPlayClip,
        onRetry = viewModel::load,
        onRetryFrame = viewModel::retryFrame,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailContent(
    state: EventDetailUiState,
    onBack: () -> Unit,
    onPlayClip: (String) -> Unit,
    onRetry: () -> Unit,
    onRetryFrame: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = IconArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            state.errorRes?.let { error ->
                MessageBanner(text = stringResource(error))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
                return@Column
            }

            val event = state.event ?: return@Column

            Text(
                text = stringResource(R.string.event_fall),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = event.capturedAt?.let { TimeFormat.smart(it) }
                    ?: stringResource(R.string.event_time_unknown),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FrameBlock(state = state, onRetryFrame = onRetryFrame)

            // Формулировка утвердительная, как в модели сервера, но рядом всегда
            // стоит основание: решение приняли правила по позе человека, а не
            // человек. Пользователь должен понимать, что именно он видит.
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.event_basis),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                )
            }

            PrimaryButton(
                text = stringResource(R.string.event_play_clip),
                onClick = { onPlayClip(event.clipMediaId) },
            )

            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.event_back_to_history))
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FrameBlock(state: EventDetailUiState, onRetryFrame: () -> Unit) {
    val targetWidth = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.roundToPx()
    }
    val bitmap = remember(state.frame, targetWidth) {
        state.frame?.let { decodeFrame(it, targetWidth) }
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                bitmap != null -> Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.event_frame_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                state.frameLoading -> CircularProgressIndicator()

                else -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(state.frameErrorRes ?: R.string.event_frame_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    TextButton(onClick = onRetryFrame) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }
        }
    }
}
