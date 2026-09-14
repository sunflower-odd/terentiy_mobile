package ru.terentiy.mobile.ui.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.terentiy.mobile.R
import ru.terentiy.mobile.core.time.TimeFormat
import ru.terentiy.mobile.domain.LatestImage
import ru.terentiy.mobile.ui.common.MessageBanner

/**
 * Просмотр последней пары кадров.
 *
 * Оба изображения приходят одним ответом и декодируются здесь. Это снимок на
 * момент запроса, а не живой поток, и подпись это проговаривает: иначе «кадр
 * минутной давности» легко принять за то, что происходит прямо сейчас.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSheet(state: ImageUiState, onDismiss: () -> Unit) {
    if (state is ImageUiState.Hidden) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.image_title),
                style = MaterialTheme.typography.titleLarge,
            )

            when (state) {
                is ImageUiState.Hidden -> Unit

                is ImageUiState.Loading -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }

                is ImageUiState.Failed -> MessageBanner(text = stringResource(state.errorRes))

                is ImageUiState.Ready -> ImageContent(state.image)
            }
        }
    }
}

@Composable
private fun ImageContent(image: LatestImage) {
    var tab by remember { mutableIntStateOf(0) }

    // Декодирование привязано к содержимому: пока лист открыт, повторно не выполняется.
    val personBitmap = remember(image) { image.personJpeg.toImageBitmapOrNull() }
    val motionBitmap = remember(image) { image.motionJpeg.toImageBitmapOrNull() }

    TabRow(selectedTabIndex = tab) {
        Tab(
            selected = tab == 0,
            onClick = { tab = 0 },
            text = { Text(stringResource(R.string.image_tab_person)) },
        )
        Tab(
            selected = tab == 1,
            onClick = { tab = 1 },
            text = { Text(stringResource(R.string.image_tab_motion)) },
        )
    }

    val bitmap = if (tab == 0) personBitmap else motionBitmap
    if (bitmap == null) {
        MessageBanner(text = stringResource(R.string.error_unknown))
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = stringResource(
                if (tab == 0) R.string.image_tab_person else R.string.image_tab_motion
            ),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat()),
        )
    }

    image.capturedAt?.let {
        Text(
            text = stringResource(R.string.image_captured_at, TimeFormat.smart(it)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    Text(
        text = if (tab == 0) {
            if (image.personCount > 0) {
                stringResource(R.string.image_person_count, image.personCount)
            } else {
                stringResource(R.string.image_no_person)
            }
        } else {
            stringResource(R.string.image_motion_regions, image.motionRegionCount)
        },
        style = MaterialTheme.typography.bodyMedium,
    )

    Text(
        text = stringResource(R.string.image_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Повреждённый или пустой JPEG не должен ронять экран: BitmapFactory в этом случае
 * возвращает null, и это нормальный путь, а не исключительный.
 */
private fun ByteArray.toImageBitmapOrNull(): ImageBitmap? {
    if (isEmpty()) return null
    return runCatching { BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap() }.getOrNull()
}
