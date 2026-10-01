package ru.terentiy.mobile.ui.clip

import android.content.Intent
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import ru.terentiy.mobile.BuildConfig
import ru.terentiy.mobile.R
import ru.terentiy.mobile.ui.common.IconArrowBack
import ru.terentiy.mobile.ui.common.MessageBanner
import ru.terentiy.mobile.ui.common.SecondaryButton
import timber.log.Timber

@Composable
fun ClipScreen(
    onBack: () -> Unit,
    viewModel: ClipViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ClipContent(state = state, onBack = onBack, onRetry = viewModel::load)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipContent(
    state: ClipUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.clip_title)) },
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                state.errorRes != null -> {
                    MessageBanner(text = stringResource(state.errorRes))
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
                }

                state.file != null -> ClipPlayer(file = state.file)

                else -> DownloadProgress(state)
            }
        }
    }
}

@Composable
private fun DownloadProgress(state: ClipUiState) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val progress = state.progress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                )
            } else {
                CircularProgressIndicator()
            }
            Text(
                text = stringResource(R.string.clip_downloading, state.downloadedBytes / 1024 / 1024),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/**
 * Проигрыватель.
 *
 * Штатный [VideoView] вместо Media3: файл локальный, скачан целиком, один MP4 на
 * тридцать секунд — ни адаптивных форматов, ни потокового вещания здесь нет, и
 * ExoPlayer окупился бы только вместе с ними. Зато не добавляется ни одной
 * зависимости, а значит нечему разойтись по версиям с AGP.
 *
 * Запасной выход рядом — открыть во внешнем плеере. Если [VideoView] на чужом
 * устройстве поведёт себя плохо, показ не сорвётся.
 */
@Composable
private fun ClipPlayer(file: File) {
    val context = LocalContext.current

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val view = remember {
                VideoView(context).apply {
                    setVideoPath(file.absolutePath)
                    setMediaController(
                        MediaController(context).also { controller -> controller.setAnchorView(this) }
                    )
                    setOnPreparedListener { start() }
                    setOnErrorListener { _, what, extra ->
                        Timber.w("Клип не воспроизводится | what=%s | extra=%s", what, extra)
                        false
                    }
                }
            }

            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())

            // Без остановки звук продолжает играть после выхода с экрана.
            DisposableEffect(view) {
                onDispose { view.stopPlayback() }
            }
        }
    }

    SecondaryButton(
        text = stringResource(R.string.clip_open_external),
        onClick = {
            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(intent) }
                .onFailure { Timber.w(it, "Внешний плеер не найден") }
        },
    )
}
