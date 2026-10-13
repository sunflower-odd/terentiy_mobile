package ru.terentiy.mobile.ui.zones

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
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
import ru.terentiy.mobile.domain.PersonBox
import ru.terentiy.mobile.domain.Zone
import ru.terentiy.mobile.domain.ZoneGeometry
import ru.terentiy.mobile.domain.ZonePoint
import ru.terentiy.mobile.domain.ZonePresence

/**
 * Зона, которую сейчас рисуют. [id] — `null` у новой зоны.
 */
data class ZoneDraft(
    val id: String? = null,
    val name: String = "",
    val points: List<ZonePoint> = emptyList(),
)

data class ZonesUiState(
    val loading: Boolean = true,
    val noAgent: Boolean = false,
    val zones: List<Zone> = emptyList(),
    /** Редактируемая зона; `null` — экран в режиме просмотра. */
    val draft: ZoneDraft? = null,
    val frame: ByteArray? = null,
    /** Время кадра на камере: кадр может быть не самым свежим. */
    val frameCapturedAt: Instant? = null,
    /** Люди на кадре — рамки детектора сервера. */
    val people: List<PersonBox> = emptyList(),
    val frameLoading: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
    @param:StringRes val problemRes: Int? = null,
) {
    val editing: Boolean get() = draft != null
    val canAdd: Boolean get() = !loading && !noAgent && !editing && zones.size < ZoneGeometry.MAX_ZONES

    /** Зоны, которые рисуются контуром: все, кроме редактируемой. */
    val shownZones: List<Zone> get() = zones.filter { it.id != draft?.id }

    /** Итог проверки зоны по полученному кадру; `null` — кадра ещё нет. */
    fun presenceOf(polygon: List<ZonePoint>): ZonePresence? =
        if (frame == null || polygon.size < ZoneGeometry.MIN_VERTICES) null else ZonePresence.of(polygon, people)

    // Массив байтов сравнивается по ссылке, из-за чего Compose считал бы
    // состояние изменившимся зря.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ZonesUiState) return false
        return loading == other.loading && noAgent == other.noAgent && zones == other.zones &&
            draft == other.draft && (frame?.contentEquals(other.frame) ?: (other.frame == null)) &&
            frameCapturedAt == other.frameCapturedAt && people == other.people &&
            frameLoading == other.frameLoading && errorRes == other.errorRes && problemRes == other.problemRes
    }

    override fun hashCode(): Int {
        var result = zones.hashCode()
        result = 31 * result + (draft?.hashCode() ?: 0)
        result = 31 * result + people.hashCode()
        result = 31 * result + (frame?.contentHashCode() ?: 0)
        result = 31 * result + loading.hashCode() + frameLoading.hashCode()
        return result
    }
}

/**
 * Критические зоны — один экран.
 *
 * Сверху вид с камеры: его берут кнопкой «Получить вид», не автообновлением.
 * На виде — все зоны и рамки людей, под ним — список зон с итогом «человек в
 * зоне или нет». Нажатие на зону или «Добавить зону» включает рисование на том
 * же виде. Зоны хранятся на телефоне.
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

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val agent = agents.agents().firstOrNull()?.id
                if (agent == null) {
                    _state.update { it.copy(loading = false, noAgent = true) }
                    return@launch
                }
                agentId = agent
                val zones = zonesRepository.zones(agent)
                _state.update { it.copy(loading = false, noAgent = false, zones = zones) }
            } catch (error: AppError) {
                _state.update { it.copy(loading = false, errorRes = error.messageRes()) }
            }
        }
    }

    fun fetchFrame() {
        val agent = agentId ?: return
        if (_state.value.frameLoading) return
        _state.update { it.copy(frameLoading = true, errorRes = null) }
        viewModelScope.launch {
            try {
                val frame = zonesRepository.currentFrame(agent)
                val bytes = (events.media(frame.mediaId) as? MediaPayload.Image)?.bytes
                    ?: throw AppError.MediaUnsupported(null)
                _state.update {
                    it.copy(frameLoading = false, frame = bytes, frameCapturedAt = frame.capturedAt, people = frame.people)
                }
            } catch (error: AppError) {
                _state.update { it.copy(frameLoading = false, errorRes = error.messageRes()) }
            }
        }
    }

    fun startNew() {
        if (!_state.value.canAdd) return
        _state.update { it.copy(draft = ZoneDraft(), problemRes = null) }
    }

    fun edit(zoneId: String) {
        val zone = _state.value.zones.firstOrNull { it.id == zoneId } ?: return
        _state.update { it.copy(draft = ZoneDraft(zone.id, zone.name, zone.polygon), problemRes = null) }
    }

    fun cancelEdit() {
        _state.update { it.copy(draft = null, problemRes = null) }
    }

    fun setName(value: String) = changeDraft { it.copy(name = value.take(ZoneGeometry.MAX_NAME_LENGTH)) }

    fun addPoint(point: ZonePoint) = changeDraft { draft ->
        if (draft.points.size >= ZoneGeometry.MAX_VERTICES) draft else draft.copy(points = draft.points + point.clamped())
    }

    fun movePoint(index: Int, point: ZonePoint) = changeDraft { draft ->
        if (index !in draft.points.indices) draft
        else draft.copy(points = draft.points.toMutableList().also { it[index] = point.clamped() })
    }

    fun undoPoint() = changeDraft { it.copy(points = it.points.dropLast(1)) }

    fun clearPoints() = changeDraft { it.copy(points = emptyList()) }

    fun save() {
        val agent = agentId ?: return
        val draft = _state.value.draft ?: return
        val problem = when {
            draft.name.isBlank() -> R.string.zone_problem_name
            else -> when (ZoneGeometry.problem(draft.points)) {
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
        val zone = Zone(
            id = draft.id ?: UUID.randomUUID().toString(),
            agentId = agent,
            name = draft.name.trim(),
            polygon = draft.points,
        )
        viewModelScope.launch {
            zonesRepository.save(zone)
            _state.update { it.copy(draft = null, zones = zonesRepository.zones(agent)) }
        }
    }

    fun delete() {
        val agent = agentId ?: return
        val id = _state.value.draft?.id ?: return
        viewModelScope.launch {
            zonesRepository.delete(id)
            _state.update { it.copy(draft = null, zones = zonesRepository.zones(agent)) }
        }
    }

    private fun changeDraft(transform: (ZoneDraft) -> ZoneDraft) {
        _state.update { state ->
            val draft = state.draft ?: return@update state
            state.copy(draft = transform(draft), problemRes = null)
        }
    }

    private fun ZonePoint.clamped() = ZonePoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}
