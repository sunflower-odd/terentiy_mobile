package ru.terentiy.mobile.ui.zones

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.terentiy.mobile.R
import ru.terentiy.mobile.domain.ZoneBindingFailure
import ru.terentiy.mobile.domain.ZoneBindingState
import ru.terentiy.mobile.domain.ZonePoint
import ru.terentiy.mobile.ui.common.PrimaryButton
import ru.terentiy.mobile.ui.common.SecondaryButton
import ru.terentiy.mobile.ui.eventdetail.decodeFrame

/**
 * Проверка зоны: кадр с контуром и маркером точки, где сервер увидел человека,
 * статус и кнопки. Тексты статусов — по контракту; числовые счётчики сервера
 * пользователю не показываются.
 */
@Composable
internal fun ZoneCheckView(check: ZoneCheck, actions: ZonesActions) {
    Text(
        text = stringResource(R.string.zone_check_title, check.zoneName),
        style = MaterialTheme.typography.titleMedium,
    )
    CheckFrame(check)

    when {
        check.running -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(text = stringResource(check.state.statusRes()), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = stringResource(
                    if (check.state == ZoneBindingState.COLLECTING && check.observation?.inZone == false) {
                        R.string.zone_check_step_inside
                    } else {
                        R.string.zone_check_hint
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryButton(text = stringResource(R.string.zone_check_cancel), onClick = actions.cancelCheck)
        }

        check.bound -> {
            Text(
                text = stringResource(R.string.zone_check_bound),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            PrimaryButton(text = stringResource(R.string.zone_done), onClick = actions.closeCheck)
        }

        else -> {
            Text(text = stringResource(R.string.zone_check_failed), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(check.errorRes ?: check.failureRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(text = stringResource(R.string.zone_check_retry), onClick = actions.retryCheck)
            TextButton(onClick = actions.closeCheck, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.common_close))
            }
        }
    }
}

/** Кадр проверки: контур зоны и маркер. Маркер в зоне — цветом зоны, вне её — цветом ошибки. */
@Composable
private fun CheckFrame(check: ZoneCheck) {
    val bitmap = remember(check.frame) { check.frame?.let { decodeFrame(it, CHECK_FRAME_WIDTH) } }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    if (bitmap == null) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).heightIn(max = maxHeight),
        ) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }
    val zoneColor = MaterialTheme.colorScheme.primary
    val outside = MaterialTheme.colorScheme.error
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .heightIn(max = maxHeight)
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1), matchHeightConstraintsFirst = true)
                .testTag(CHECK_FRAME_TAG),
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val toScreen = { p: ZonePoint -> Offset(p.x * size.width, p.y * size.height) }
                drawZone(check.polygon, zoneColor, filled = true, toScreen = toScreen)
                check.observation?.let { observation ->
                    val center = toScreen(observation.point)
                    val color = if (observation.inZone) zoneColor else outside
                    drawCircle(color = Color.White, radius = 12.dp.toPx(), center = center)
                    drawCircle(color = color, radius = 9.dp.toPx(), center = center)
                    drawCircle(color = Color.White, radius = 16.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
    }
}

@StringRes
private fun ZoneBindingState?.statusRes(): Int = when (this) {
    null -> R.string.zone_check_starting
    ZoneBindingState.WAITING_FOR_FRAME -> R.string.zone_check_waiting_frame
    ZoneBindingState.WAITING_FOR_PERSON -> R.string.zone_check_waiting_person
    else -> R.string.zone_check_collecting
}

@StringRes
private fun ZoneCheck.failureRes(): Int = when {
    state == ZoneBindingState.CANCELLED -> R.string.zone_check_cancelled
    else -> when (failure) {
        ZoneBindingFailure.CAMERA_UNAVAILABLE -> R.string.zone_check_failure_camera
        ZoneBindingFailure.NO_PERSON -> R.string.zone_check_failure_no_person
        ZoneBindingFailure.NO_SUPPORT_POINT -> R.string.zone_check_failure_no_point
        ZoneBindingFailure.NOT_INSIDE -> R.string.zone_check_failure_not_inside
        ZoneBindingFailure.ZONE_CHANGED -> R.string.zone_check_failure_zone_changed
        ZoneBindingFailure.UNKNOWN, null -> R.string.zone_check_failure_unknown
    }
}

/** Кадр проверки только показывается, не увеличивается: хватает ширины экрана. */
private const val CHECK_FRAME_WIDTH = 1280

internal const val CHECK_FRAME_TAG = "zone_check_frame"
