package ru.terentiy.mobile.ui.zones

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.terentiy.mobile.R
import ru.terentiy.mobile.core.time.TimeFormat
import ru.terentiy.mobile.domain.Zone
import ru.terentiy.mobile.domain.ZonePoint
import ru.terentiy.mobile.domain.ZoneState
import ru.terentiy.mobile.ui.common.IconArrowBack
import ru.terentiy.mobile.ui.common.IconExpand
import ru.terentiy.mobile.ui.common.MessageBanner
import ru.terentiy.mobile.ui.common.PrimaryButton
import ru.terentiy.mobile.ui.common.SecondaryButton
import ru.terentiy.mobile.ui.eventdetail.decodeFrame

@Composable
fun ZonesScreen(
    onBack: () -> Unit,
    viewModel: ZonesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ZonesContent(
        state = state,
        onBack = onBack,
        actions = ZonesActions(
            fetchFrame = viewModel::fetchFrame,
            startNew = viewModel::startNew,
            edit = viewModel::edit,
            cancelEdit = viewModel::cancelEdit,
            setName = viewModel::setName,
            addPoint = viewModel::addPoint,
            movePoint = viewModel::movePoint,
            undoPoint = viewModel::undoPoint,
            clearPoints = viewModel::clearPoints,
            save = viewModel::save,
            delete = viewModel::delete,
        ),
    )
}

/** Действия экрана одним объектом: иначе у него десяток параметров. */
class ZonesActions(
    val fetchFrame: () -> Unit = {},
    val startNew: () -> Unit = {},
    val edit: (String) -> Unit = {},
    val cancelEdit: () -> Unit = {},
    val setName: (String) -> Unit = {},
    val addPoint: (ZonePoint) -> Unit = {},
    val movePoint: (Int, ZonePoint) -> Unit = { _, _ -> },
    val undoPoint: () -> Unit = {},
    val clearPoints: () -> Unit = {},
    val save: () -> Unit = {},
    val delete: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZonesContent(
    state: ZonesUiState,
    onBack: () -> Unit,
    actions: ZonesActions,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.zones_title)) },
                navigationIcon = {
                    IconButton(onClick = if (state.editing) actions.cancelEdit else onBack) {
                        Icon(imageVector = IconArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            state.noAgent -> Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                MessageBanner(
                    text = stringResource(R.string.zones_no_agent),
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                state.errorRes?.let { MessageBanner(text = stringResource(it)) }
                FrameView(state = state, actions = actions)
                if (state.editing) EditPanel(state = state, actions = actions)
                else ZoneList(state = state, actions = actions)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Вид с камеры: все зоны контуром, редактируемая — с заливкой и вершинами. Касания добавляют и двигают вершины только
 * в режиме рисования.
 *
 * Контейнер повторяет пропорции кадра, поэтому изображение заполняет его без
 * полей, и касание переводится в доли кадра простым делением на размер.
 */
@Composable
private fun FrameView(state: ZonesUiState, actions: ZonesActions) {
    val bitmap = remember(state.frame) { state.frame?.let { decodeFrame(it, ZOOM_FRAME_WIDTH) } }
    var fullScreen by rememberSaveable { mutableStateOf(false) }

    if (bitmap == null) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (state.frameLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = stringResource(R.string.zone_no_frame),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
        SecondaryButton(
            text = stringResource(R.string.zone_get_view),
            onClick = actions.fetchFrame,
            loading = state.frameLoading,
        )
        return
    }

    if (fullScreen) FrameFullScreen(state = state, bitmap = bitmap, actions = actions, onClose = { fullScreen = false })

    if (state.editing) {
        Text(
            text = stringResource(R.string.zone_draw_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    val fill = MaterialTheme.colorScheme.primary
    val other = MaterialTheme.colorScheme.tertiary
    val vertex = MaterialTheme.colorScheme.onPrimary
    val points by rememberUpdatedState(state.edited?.points.orEmpty())
    val radius = with(LocalDensity.current) { 24.dp.toPx() }

    val drawing = if (!state.editing) Modifier else Modifier
        .pointerInput(Unit) {
            detectTapGestures { tap -> actions.addPoint(ZonePoint(tap.x / size.width, tap.y / size.height)) }
        }
        .pointerInput(Unit) {
            var dragged = -1
            detectDragGestures(
                onDragStart = { start ->
                    dragged = points.indices
                        .map { index -> index to (Offset(points[index].x * size.width, points[index].y * size.height) - start).getDistance() }
                        .filter { (_, distance) -> distance <= radius }
                        .minByOrNull { (_, distance) -> distance }
                        ?.first ?: -1
                },
                onDragEnd = { dragged = -1 },
                onDragCancel = { dragged = -1 },
            ) { change, _ ->
                if (dragged >= 0) {
                    change.consume()
                    actions.movePoint(dragged, ZonePoint(change.position.x / size.width, change.position.y / size.height))
                }
            }
        }

    // По горизонтали ширина экрана больше высоты, и кадр во всю ширину уходил
    // за нижний край. Высота ограничена, кадр по центру, пропорции те же.
    val maxFrameHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .heightIn(max = maxFrameHeight)
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1), matchHeightConstraintsFirst = true),
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(modifier = Modifier.fillMaxSize().then(drawing)) {
                val toScreen = { p: ZonePoint -> Offset(p.x * size.width, p.y * size.height) }
                state.shownZones.forEach { zone -> drawZone(zone.polygon, other, filled = false, toScreen = toScreen) }
                state.edited?.let { edited ->
                    drawZone(edited.points, fill, filled = true, toScreen = toScreen)
                    drawVertices(edited.points, fill, vertex, toScreen = toScreen)
                }
            }
            FilledTonalIconButton(
                onClick = { fullScreen = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            ) {
                Icon(imageVector = IconExpand, contentDescription = stringResource(R.string.zone_expand))
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (state.editing) {
            TextButton(onClick = actions.undoPoint, enabled = points.isNotEmpty()) {
                Text(stringResource(R.string.zone_undo_point))
            }
            TextButton(onClick = actions.clearPoints, enabled = points.isNotEmpty()) {
                Text(stringResource(R.string.zone_clear_points))
            }
        } else {
            state.frameCapturedAt?.let {
                Text(
                    text = stringResource(R.string.zone_frame_time, TimeFormat.smart(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = actions.fetchFrame, enabled = !state.frameLoading) {
            Text(stringResource(R.string.zone_refresh_view))
        }
    }
}

/** Список зон с итогом проверки по последнему виду. */
@Composable
private fun ZoneList(state: ZonesUiState, actions: ZonesActions) {
    if (state.zones.isEmpty()) {
        Text(
            text = stringResource(R.string.zones_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    state.zones.forEach { zone ->
        ZoneRow(zone = zone, onClick = { actions.edit(zone.id) })
    }
    PrimaryButton(text = stringResource(R.string.zones_add), onClick = actions.startNew, enabled = state.canAdd)
    if (!state.canAdd) {
        Text(
            text = stringResource(R.string.zones_limit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ZoneRow(zone: Zone, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text = zone.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(zone.state.textRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Рисование зоны: название, сохранить, удалить. */
@Composable
private fun EditPanel(state: ZonesUiState, actions: ZonesActions) {
    val edited = state.edited ?: return
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.zone_delete)) },
            text = { Text(stringResource(R.string.zone_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.delete()
                }) { Text(stringResource(R.string.common_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_close)) }
            },
        )
    }

    Text(
        text = stringResource(if (edited.id == null) R.string.zone_new_title else R.string.zone_edit_title),
        style = MaterialTheme.typography.titleMedium,
    )
    OutlinedTextField(
        value = edited.name,
        onValueChange = actions.setName,
        label = { Text(stringResource(R.string.zone_name)) },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    state.problemRes?.let { MessageBanner(text = stringResource(it)) }
    PrimaryButton(text = stringResource(R.string.zone_save), onClick = actions.save, loading = state.saving, enabled = !state.saving)
    if (edited.id != null) {
        SecondaryButton(text = stringResource(R.string.zone_delete), onClick = { confirmDelete = true }, enabled = !state.saving)
    }
    TextButton(onClick = actions.cancelEdit, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.zone_cancel))
    }
}

private fun ZoneState.textRes(): Int = when (this) {
    ZoneState.DRAFT -> R.string.zone_state_draft
    ZoneState.BINDING -> R.string.zone_state_binding
    ZoneState.BOUND -> R.string.zone_state_bound
}

/**
 * Ширина, до которой уменьшается кадр при декодировании. Больше ширины экрана,
 * чтобы при увеличении кадр не расплывался; 4K уменьшается вдвое — так кадр
 * занимает около 8 МБ памяти.
 */
private const val ZOOM_FRAME_WIDTH = 1920

/** Контур зоны; [toScreen] переводит точку из долей кадра в координаты экрана. */
internal fun DrawScope.drawZone(points: List<ZonePoint>, color: Color, filled: Boolean, toScreen: (ZonePoint) -> Offset) {
    if (points.isEmpty()) return
    val path = Path().apply {
        points.forEachIndexed { index, p ->
            val (x, y) = toScreen(p)
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        if (points.size >= 3) close()
    }
    if (filled && points.size >= 3) drawPath(path = path, color = color.copy(alpha = 0.3f))
    drawPath(path = path, color = color, style = Stroke(width = 2.dp.toPx()))
}

/** Вершины редактируемой зоны: размер на экране не зависит от масштаба. */
internal fun DrawScope.drawVertices(points: List<ZonePoint>, fill: Color, vertex: Color, toScreen: (ZonePoint) -> Offset) {
    points.forEach { p ->
        val center = toScreen(p)
        drawCircle(color = fill, radius = 9.dp.toPx(), center = center)
        drawCircle(color = vertex, radius = 4.dp.toPx(), center = center)
    }
}
