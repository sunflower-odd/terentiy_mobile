package ru.terentiy.mobile.ui.zones

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import ru.terentiy.mobile.R
import ru.terentiy.mobile.domain.ZonePoint
import ru.terentiy.mobile.ui.common.ButtonText
import ru.terentiy.mobile.ui.common.CompactButtonPadding

/**
 * Кадр на весь экран с увеличением.
 *
 * Двумя пальцами — увеличить и сдвинуть, одним — сдвинуть увеличенный кадр.
 * При рисовании зоны касание ставит точку, точку можно перетащить; двойное
 * касание здесь не используется, иначе каждая точка ставилась бы с задержкой,
 * поэтому масштаб сбрасывается кнопкой. При просмотре двойное касание
 * увеличивает кадр к месту касания и возвращает обратно.
 */
@Composable
internal fun FrameFullScreen(
    state: ZonesUiState,
    bitmap: ImageBitmap,
    actions: ZonesActions,
    onClose: () -> Unit,
) {
    val transform = remember { mutableStateOf<FrameTransform?>(null) }
    val editing = state.editing
    val points = state.edited?.points

    // Отступов от системных панелей и выреза камеры здесь нет: окно диалога и так
    // их обходит. С safeDrawingPadding по горизонтали размер окна зацикливался
    // (вырез то попадал в окно, то нет), кадр вписывался заново и масштаб сбрасывался.
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag(FULL_SCREEN_TAG),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(if (editing) R.string.zone_zoom_hint_edit else R.string.zone_zoom_hint_view),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f),
                )
                if (transform.value?.zoomed == true) {
                    TextButton(onClick = { transform.value = transform.value?.reset() }) {
                        Text(stringResource(R.string.zone_reset_zoom), color = Color.White)
                    }
                }
            }
            ZoomableFrame(
                bitmap = bitmap,
                shownZones = state.shownZones.map { it.polygon },
                editedPoints = points,
                editable = editing,
                transform = transform,
                onAddPoint = actions.addPoint,
                onMovePoint = actions.movePoint,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            // Кнопки всегда в один ряд; на узком экране подписи мельче, а не переносятся.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                if (editing) {
                    TextButton(onClick = actions.undoPoint, enabled = !points.isNullOrEmpty(), contentPadding = CompactButtonPadding, modifier = Modifier.weight(1f)) {
                        ButtonText(stringResource(R.string.zone_undo_point))
                    }
                    TextButton(onClick = actions.clearPoints, enabled = !points.isNullOrEmpty(), contentPadding = CompactButtonPadding, modifier = Modifier.weight(1f)) {
                        ButtonText(stringResource(R.string.zone_clear_points))
                    }
                    TextButton(onClick = onClose, contentPadding = CompactButtonPadding, modifier = Modifier.weight(1f)) {
                        ButtonText(stringResource(R.string.zone_done))
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onClose) { ButtonText(stringResource(R.string.common_close)) }
                }
            }
        }
    }
}

@Composable
private fun ZoomableFrame(
    bitmap: ImageBitmap,
    shownZones: List<List<ZonePoint>>,
    editedPoints: List<ZonePoint>?,
    editable: Boolean,
    transform: MutableState<FrameTransform?>,
    onAddPoint: (ZonePoint) -> Unit,
    onMovePoint: (Int, ZonePoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = MaterialTheme.colorScheme.primary
    val other = MaterialTheme.colorScheme.tertiary
    val vertex = MaterialTheme.colorScheme.onPrimary
    val aspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
    val radius = with(LocalDensity.current) { 24.dp.toPx() }

    val points by rememberUpdatedState(editedPoints.orEmpty())
    val add by rememberUpdatedState(onAddPoint)
    val move by rememberUpdatedState(onMovePoint)

    Box(
        modifier = modifier.onSizeChanged { size ->
            val container = Size(size.width.toFloat(), size.height.toFloat())
            if (size.width > 0 && size.height > 0 && transform.value?.container != container) {
                transform.value = FrameTransform.fit(container, aspect)
            }
        },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(editable) {
                    var lastTapAt = 0L
                    var lastTapPosition = Offset.Zero
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = transform.value ?: return@awaitEachGesture
                        var dragged = if (editable) nearestVertex(points, start, down.position, radius) else -1
                        var moved = 0f
                        var transforming = false
                        var multiTouch = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size > 1) {
                                multiTouch = true
                                dragged = -1
                                transforming = true
                            }
                            if (dragged >= 0) {
                                val position = pressed.first().position
                                transform.value?.let { move(dragged, it.toPoint(position)) }
                                event.changes.forEach { it.consume() }
                                continue
                            }
                            val pan = event.calculatePan()
                            moved += pan.getDistance()
                            if (moved > viewConfiguration.touchSlop) transforming = true
                            if (transforming) {
                                transform.value?.let { transform.value = it.zoomedBy(event.calculateCentroid(), event.calculateZoom(), pan) }
                                event.changes.forEach { it.consume() }
                            }
                        }

                        if (dragged >= 0 || transforming || multiTouch) return@awaitEachGesture
                        val tr = transform.value ?: return@awaitEachGesture
                        if (!tr.contains(down.position)) return@awaitEachGesture
                        if (editable) {
                            add(tr.toPoint(down.position))
                        } else if (
                            down.uptimeMillis - lastTapAt < viewConfiguration.doubleTapTimeoutMillis &&
                            (down.position - lastTapPosition).getDistance() < radius * 2
                        ) {
                            transform.value = tr.toggledAt(down.position)
                            lastTapAt = 0L
                        } else {
                            lastTapAt = down.uptimeMillis
                            lastTapPosition = down.position
                        }
                    }
                },
        ) {
            val tr = transform.value ?: return@Canvas
            clipRect {
                val topLeft = tr.toScreen(ZonePoint(0f, 0f))
                val bottomRight = tr.toScreen(ZonePoint(1f, 1f))
                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                    dstSize = IntSize((bottomRight.x - topLeft.x).roundToInt(), (bottomRight.y - topLeft.y).roundToInt()),
                    filterQuality = FilterQuality.High,
                )
                shownZones.forEach { zone -> drawZone(zone, other, filled = false, toScreen = tr::toScreen) }
                editedPoints?.let { edited ->
                    drawZone(edited, fill, filled = true, toScreen = tr::toScreen)
                    drawVertices(edited, fill, vertex, toScreen = tr::toScreen)
                }
            }
        }
    }
}

/** Ближайшая к касанию вершина в пределах [radius] на экране, иначе -1. */
private fun nearestVertex(points: List<ZonePoint>, transform: FrameTransform, touch: Offset, radius: Float): Int =
    points.indices
        .map { index -> index to (transform.toScreen(points[index]) - touch).getDistance() }
        .filter { (_, distance) -> distance <= radius }
        .minByOrNull { (_, distance) -> distance }
        ?.first ?: -1

internal const val FULL_SCREEN_TAG = "zone_frame_full_screen"
