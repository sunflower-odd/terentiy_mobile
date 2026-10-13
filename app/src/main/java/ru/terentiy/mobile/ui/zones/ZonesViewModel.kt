package ru.terentiy.mobile.ui.zones

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.terentiy.mobile.R
import ru.terentiy.mobile.core.error.AppError
import ru.terentiy.mobile.core.error.messageRes
import ru.terentiy.mobile.data.repository.AgentsRepository
import ru.terentiy.mobile.data.repository.EventsRepository
import ru.terentiy.mobile.data.repository.ZonesRepository
import ru.terentiy.mobile.domain.MediaPayload
import ru.terentiy.mobile.domain.Zone
import ru.terentiy.mobile.domain.ZoneDraft
import ru.terentiy.mobile.domain.ZoneGeometry
import ru.terentiy.mobile.domain.ZonePoint

/** Зона, которую сейчас рисуют. [id] и [version] — `null` у новой зоны. */
data class EditedZone(
    val id: String? = null,
    val version: Int? = null,
    val name: String = "",
    val points: List<ZonePoint> = emptyList(),
)

data class ZonesUiState(
    val loading: Boolean = true,
    val noAgent: Boolean = false,
    val zones: List<Zone> = emptyList(),
    /** Редактируемая зона; `null` — экран в режиме просмотра. */
    val edited: EditedZone? = null,
    /** Показанный кадр и его идентификатор: на нём рисуют, он уходит как `reference_frame_id`. */
    val frame: ByteArray? = null,
    val frameId: String? = null,
    val frameCapturedAt: Instant? = null,
    val frameLoading: Boolean = false,
    val saving: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
    @param:StringRes val problemRes: Int? = null,
) {
    val editing: Boolean get() = edited != null
    val canAdd: Boolean get() = !loading && !noAgent && !editing && zones.size < ZoneGeometry.MAX_ZONES

    /** Зоны, которые рисуются контуром: все, кроме редактируемой. */
    val shownZones: List<Zone> get() = zones.filter { it.id != edited?.id }

    // Массив байтов сравнивается по ссылке, из-за чего Compose считал бы
    // состояние изменившимся зря.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ZonesUiState) return false
        return loading == other.loading && noAgent == other.noAgent && zones == other.zones &&
            edited == other.edited && (frame?.contentEquals(other.frame) ?: (other.frame == null)) &&
            frameId == other.frameId && frameCapturedAt == other.frameCapturedAt &&
            frameLoading == other.frameLoading && saving == other.saving &&
            errorRes == other.errorRes && problemRes == other.problemRes
    }

    override fun hashCode(): Int {
        var result = zones.hashCode()
        result = 31 * result + (edited?.hashCode() ?: 0)
        result = 31 * result + (frameId?.hashCode() ?: 0)
        result = 31 * result + (frame?.contentHashCode() ?: 0)
        result = 31 * result + loading.hashCode() + frameLoading.hashCode() + saving.hashCode()
        return result
    }
}

/**
 * Критические зоны — один экран (mobile-server 2.0.0-draft).
 *
 * Сверху вид с камеры — по кнопке «Получить вид», не автообновлением. На виде
 * все зоны камеры, под ним — их список. Нажатие на зону или «Добавить зону»
 * включает рисование. Зоны хранятся на сервере; проверку зоны (человек встаёт в
 * неё, сервер подтверждает) приложение пока не запускает.
 *
 * Камера одна на дом, поэтому берётся первый агент — как на главном экране.
 */
@HiltViewModel
class ZonesViewModel @Inject constructor(
    private val agents: AgentsRepository,
    private val zonesRepository: ZonesRepository,
    private val events: EventsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ZonesUiState())
    val state: StateFlow<ZonesUiState> = _state.asStateFlow()

    private var agentId: String? = null
    private var cameraId: String? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val agent = agents.agents().firstOrNull()
                if (agent == null) {
                    _state.update { it.copy(loading = false, noAgent = true) }
                    return@launch
                }
                agentId = agent.id
                cameraId = agent.cameraId
                val zones = zonesRepository.zones(agent.id, agent.cameraId)
                _state.update { it.copy(loading = false, noAgent = false, zones = zones) }
            } catch (error: AppError) {
                _state.update { it.copy(loading = false, errorRes = error.messageRes()) }
            }
        }
    }

    fun fetchFrame() {
        val agent = agentId ?: return
        val camera = cameraId ?: return
        if (_state.value.frameLoading) return
        _state.update { it.copy(frameLoading = true, errorRes = null) }
        viewModelScope.launch {
            try {
                val frame = zonesRepository.currentFrame(agent, camera)
                val bytes = image(frame.mediaId)
                _state.update {
                    it.copy(frameLoading = false, frame = bytes, frameId = frame.frameId, frameCapturedAt = frame.capturedAt)
                }
            } catch (error: AppError) {
                _state.update { it.copy(frameLoading = false, errorRes = error.messageRes()) }
            }
        }
    }

    fun startNew() {
        if (!_state.value.canAdd) return
        _state.update { it.copy(edited = EditedZone(), problemRes = null, errorRes = null) }
    }

    /**
     * Изменить зону. Контур нарисован на её кадре — он и показывается, чтобы
     * вершины легли туда же. «Обновить вид» заменит кадр на свежий.
     */
    fun edit(zoneId: String) {
        val zone = _state.value.zones.firstOrNull { it.id == zoneId } ?: return
        _state.update {
            it.copy(edited = EditedZone(zone.id, zone.version, zone.name, zone.polygon), problemRes = null, errorRes = null)
        }
        if (_state.value.frameId == zone.referenceFrameId) return
        _state.update { it.copy(frameLoading = true) }
        viewModelScope.launch {
            try {
                val bytes = image(zone.referenceMediaId)
                _state.update { it.copy(frameLoading = false, frame = bytes, frameId = zone.referenceFrameId, frameCapturedAt = null) }
            } catch (error: AppError) {
                // Кадр могли удалить по сроку хранения — тогда рисуют на свежем виде.
                _state.update { it.copy(frameLoading = false, errorRes = error.messageRes()) }
            }
        }
    }

    fun cancelEdit() {
        _state.update { it.copy(edited = null, problemRes = null) }
    }

    fun setName(value: String) = change { it.copy(name = value.take(ZoneGeometry.MAX_NAME_LENGTH)) }

    fun addPoint(point: ZonePoint) = change { zone ->
        if (zone.points.size >= ZoneGeometry.MAX_VERTICES) zone else zone.copy(points = zone.points + point.clamped())
    }

    fun movePoint(index: Int, point: ZonePoint) = change { zone ->
        if (index !in zone.points.indices) zone
        else zone.copy(points = zone.points.toMutableList().also { it[index] = point.clamped() })
    }

    fun undoPoint() = change { it.copy(points = it.points.dropLast(1)) }

    fun clearPoints() = change { it.copy(points = emptyList()) }

    fun save() {
        val agent = agentId ?: return
        val camera = cameraId ?: return
        val state = _state.value
        val edited = state.edited ?: return
        if (state.saving) return
        val problem = when {
            edited.name.isBlank() -> R.string.zone_problem_name
            state.frameId == null -> R.string.zone_no_frame
            else -> when (ZoneGeometry.problem(edited.points)) {
                ZoneGeometry.Problem.TOO_FEW_POINTS -> R.string.zone_problem_few_points
                ZoneGeometry.Problem.TOO_MANY_POINTS -> R.string.zone_problem_many_points
                ZoneGeometry.Problem.SELF_INTERSECTING -> R.string.zone_problem_intersecting
                ZoneGeometry.Problem.TOO_SMALL -> R.string.zone_problem_small
                null -> null
            }
        }
        if (problem != null) {
            _state.update { it.copy(problemRes = problem) }
            return
        }
        val draft = ZoneDraft(name = edited.name, polygon = edited.points, referenceFrameId = state.frameId!!)
        _state.update { it.copy(saving = true, errorRes = null) }
        viewModelScope.launch {
            try {
                if (edited.id == null || edited.version == null) {
                    zonesRepository.create(agent, camera, draft)
                } else {
                    zonesRepository.update(agent, camera, edited.id, edited.version, draft)
                }
                val zones = zonesRepository.zones(agent, camera)
                _state.update { it.copy(saving = false, edited = null, zones = zones) }
            } catch (error: AppError) {
                _state.update { it.copy(saving = false, errorRes = error.messageRes()) }
            }
        }
    }

    fun delete() {
        val agent = agentId ?: return
        val camera = cameraId ?: return
        val id = _state.value.edited?.id ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, errorRes = null) }
        viewModelScope.launch {
            try {
                zonesRepository.delete(agent, camera, id)
                val zones = zonesRepository.zones(agent, camera)
                _state.update { it.copy(saving = false, edited = null, zones = zones) }
            } catch (error: AppError) {
                _state.update { it.copy(saving = false, errorRes = error.messageRes()) }
            }
        }
    }

    private suspend fun image(mediaId: String): ByteArray =
        (events.media(mediaId) as? MediaPayload.Image)?.bytes ?: throw AppError.MediaUnsupported(null)

    private fun change(transform: (EditedZone) -> EditedZone) {
        _state.update { state ->
            val edited = state.edited ?: return@update state
            state.copy(edited = transform(edited), problemRes = null)
        }
    }

    private fun ZonePoint.clamped() = ZonePoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}
