package ru.terentiy.mobile.ui.zones

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.terentiy.mobile.R
import ru.terentiy.mobile.core.error.AppError
import ru.terentiy.mobile.core.error.messageRes
import ru.terentiy.mobile.data.repository.AgentsRepository
import ru.terentiy.mobile.data.repository.EventsRepository
import ru.terentiy.mobile.data.repository.ZonesRepository
import ru.terentiy.mobile.domain.CurrentFrame
import ru.terentiy.mobile.domain.MediaPayload
import ru.terentiy.mobile.domain.Zone
import ru.terentiy.mobile.domain.ZoneBindingFailure
import ru.terentiy.mobile.domain.ZoneBindingSession
import ru.terentiy.mobile.domain.ZoneBindingState
import ru.terentiy.mobile.domain.ZoneDraft
import ru.terentiy.mobile.domain.ZoneGeometry
import ru.terentiy.mobile.domain.ZoneObservation
import ru.terentiy.mobile.domain.ZonePoint
import timber.log.Timber

/** Зона, которую сейчас рисуют. [id] и [version] — `null` у новой зоны. */
data class EditedZone(
    val id: String? = null,
    val version: Int? = null,
    val name: String = "",
    val points: List<ZonePoint> = emptyList(),
)

/**
 * Проверка зоны на экране. [state] — `null`, пока сервер не ответил на запуск.
 * [errorRes] — сбой запроса (сеть, сервер), а не результат проверки.
 * [frame] — последний кадр, на котором сервер искал человека; маркер [observation]
 * рисуется поверх него.
 */
data class ZoneCheck(
    val zoneId: String,
    val zoneName: String,
    val polygon: List<ZonePoint>,
    val sessionId: String? = null,
    val state: ZoneBindingState? = null,
    val observation: ZoneObservation? = null,
    val failure: ZoneBindingFailure? = null,
    @param:StringRes val errorRes: Int? = null,
    val frame: ByteArray? = null,
    val frameMediaId: String? = null,
) {
    val running: Boolean get() = errorRes == null && state?.terminal != true
    val bound: Boolean get() = state == ZoneBindingState.BOUND
    val failed: Boolean get() = !running && !bound

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ZoneCheck) return false
        return zoneId == other.zoneId && zoneName == other.zoneName && polygon == other.polygon &&
            sessionId == other.sessionId && state == other.state && observation == other.observation &&
            failure == other.failure && errorRes == other.errorRes && frameMediaId == other.frameMediaId &&
            (frame?.contentEquals(other.frame) ?: (other.frame == null))
    }

    override fun hashCode(): Int {
        var result = zoneId.hashCode()
        result = 31 * result + (state?.hashCode() ?: 0)
        result = 31 * result + (observation?.hashCode() ?: 0)
        result = 31 * result + (frameMediaId?.hashCode() ?: 0)
        result = 31 * result + (errorRes ?: 0)
        return result
    }
}

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
    /** Идёт или закончилась проверка зоны; `null` — экран зон в обычном виде. */
    val check: ZoneCheck? = null,
) {
    val editing: Boolean get() = edited != null
    val canAdd: Boolean get() = !loading && !noAgent && !editing && check == null && zones.size < ZoneGeometry.MAX_ZONES

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
            errorRes == other.errorRes && problemRes == other.problemRes && check == other.check
    }

    override fun hashCode(): Int {
        var result = zones.hashCode()
        result = 31 * result + (edited?.hashCode() ?: 0)
        result = 31 * result + (frameId?.hashCode() ?: 0)
        result = 31 * result + (frame?.contentHashCode() ?: 0)
        result = 31 * result + loading.hashCode() + frameLoading.hashCode() + saving.hashCode()
        result = 31 * result + (check?.hashCode() ?: 0)
        return result
    }
}

/**
 * Критические зоны — один экран (mobile-server 2.0.0-draft).
 *
 * Сверху вид с камеры — по кнопке «Получить вид», не автообновлением. На виде
 * все зоны камеры, под ним — их список. Нажатие на зону или «Добавить зону»
 * включает рисование. Зоны хранятся на сервере. Непроверенную зону можно
 * проверить: человек встаёт в неё, сервер по свежим кадрам подтверждает, что
 * видит его внутри (см. [startCheck]).
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
    private var checkJob: Job? = null

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
     * вершины легли туда же. «Обновить вид» заменит кадр на свежий; при
     * сохранении свежий кадр берётся в любом случае (см. [save]).
     */
    fun edit(zoneId: String) {
        if (_state.value.check != null) return
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
        _state.update { it.copy(saving = true, errorRes = null) }
        viewModelScope.launch {
            try {
                // Сервер принимает `reference_frame_id` только от текущего кадра
                // камеры (не старше 30 с) — и при создании, и при правке. Кадр,
                // на котором рисовали, к этому моменту обычно уже старый, а у
                // правки это вообще кадр, на котором зону создали. Камера
                // неподвижна, а вершины заданы в долях кадра, поэтому контур
                // переносится на свежий кадр без пересчёта.
                val fresh = zonesRepository.currentFrame(agent, camera)
                val draft = ZoneDraft(name = edited.name, polygon = edited.points, referenceFrameId = fresh.frameId)
                if (edited.id == null || edited.version == null) {
                    zonesRepository.create(agent, camera, draft)
                } else {
                    zonesRepository.update(agent, camera, edited.id, edited.version, draft)
                }
                val zones = zonesRepository.zones(agent, camera)
                _state.update { it.copy(saving = false, edited = null, zones = zones) }
                showFrame(fresh)
            } catch (error: AppError) {
                _state.update { it.copy(saving = false, errorRes = error.messageRes()) }
            }
        }
    }

    /**
     * Показать кадр, к которому сервер привязал зону. Не обязательно для
     * сохранения: если картинка не загрузилась, остаётся прежний вид.
     */
    private suspend fun showFrame(frame: CurrentFrame) {
        if (_state.value.frameId == frame.frameId) return
        try {
            val bytes = image(frame.mediaId)
            _state.update { it.copy(frame = bytes, frameId = frame.frameId, frameCapturedAt = frame.capturedAt) }
        } catch (error: AppError) {
            Timber.w("Свежий кадр зоны не загрузился | ошибка=%s", error)
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

    /**
     * Проверка зоны (сессия привязки, mobile-server 2.0.0-draft).
     *
     * Запуск, затем опрос раз в [POLL_INTERVAL_MS], пока сервер не вернёт
     * конечное состояние. Сервер сам завершает сессию по таймауту (60 с), так
     * что опрос не бесконечен. Кратковременная потеря сети опрос не обрывает.
     */
    fun startCheck(zoneId: String) {
        val agent = agentId ?: return
        val camera = cameraId ?: return
        val state = _state.value
        if (state.editing || state.check?.running == true) return
        val zone = state.zones.firstOrNull { it.id == zoneId } ?: return
        val frame = state.frame?.takeIf { state.frameId == zone.referenceFrameId }
        _state.update { it.copy(check = ZoneCheck(zone.id, zone.name, zone.polygon, frame = frame), errorRes = null) }
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            if (frame == null) showCheckFrame(zone.referenceMediaId)
            val job = coroutineContext.job
            // Запуск доводится до конца даже при отмене: иначе сессия на сервере
            // осталась бы без хозяина и держала зону «проверяется» до таймаута.
            var session = try {
                withContext(NonCancellable) {
                    zonesRepository.startBinding(agent, camera, zone.id, zone.version).also { started ->
                        if (job.isCancelled) cancelOnServer(agent, camera, zone.id, started.id)
                    }
                }
            } catch (error: AppError) {
                updateCheck(zone.id) { it.copy(errorRes = error.messageRes()) }
                refreshZones()
                return@launch
            }
            coroutineContext.ensureActive()
            applySession(zone.id, session)
            var networkFailures = 0
            while (!session.state.terminal) {
                delay(POLL_INTERVAL_MS)
                session = try {
                    zonesRepository.binding(agent, camera, zone.id, session.id).also { networkFailures = 0 }
                } catch (error: AppError) {
                    if (error == AppError.Network && ++networkFailures < MAX_NETWORK_FAILURES) continue
                    Timber.w("Опрос проверки зоны прерван | ошибка=%s", error)
                    updateCheck(zone.id) { it.copy(errorRes = error.messageRes()) }
                    refreshZones()
                    return@launch
                }
                applySession(zone.id, session)
            }
            Timber.i("Проверка зоны завершена | state=%s | failure=%s", session.state.wire, session.failure)
            refreshZones()
        }
    }

    /** Отменить идущую проверку и вернуться к зонам. */
    fun cancelCheck() {
        val check = _state.value.check ?: return
        checkJob?.cancel()
        _state.update { it.copy(check = null) }
        val agent = agentId ?: return
        val camera = cameraId ?: return
        val sessionId = check.sessionId
        viewModelScope.launch {
            if (sessionId != null && check.running) cancelOnServer(agent, camera, check.zoneId, sessionId)
            refreshZones()
        }
    }

    /** Закрыть экран проверки после результата. */
    fun closeCheck() {
        if (_state.value.check?.running == true) return cancelCheck()
        _state.update { it.copy(check = null) }
    }

    /** Повторить проверку той же зоны: версия берётся из обновлённого списка. */
    fun retryCheck() {
        val check = _state.value.check ?: return
        if (check.running) return
        startCheck(check.zoneId)
    }

    private suspend fun applySession(zoneId: String, session: ZoneBindingSession) {
        updateCheck(zoneId) {
            it.copy(
                sessionId = session.id,
                state = session.state,
                observation = session.observation ?: it.observation,
                failure = session.failure,
            )
        }
        val mediaId = session.observation?.mediaId ?: return
        if (_state.value.check?.frameMediaId == mediaId) return
        showCheckFrame(mediaId)
    }

    /** Кадр для проверки. Не загрузился — остаётся прежний: камера неподвижна, маркер ляжет верно. */
    private suspend fun showCheckFrame(mediaId: String) {
        val zoneId = _state.value.check?.zoneId ?: return
        try {
            val bytes = image(mediaId)
            updateCheck(zoneId) { it.copy(frame = bytes, frameMediaId = mediaId) }
        } catch (error: AppError) {
            Timber.w("Кадр проверки зоны не загрузился | ошибка=%s", error)
        }
    }

    private suspend fun cancelOnServer(agent: String, camera: String, zoneId: String, sessionId: String) {
        try {
            zonesRepository.cancelBinding(agent, camera, zoneId, sessionId)
        } catch (error: AppError) {
            // Не страшно: сервер сам завершит сессию по таймауту.
            Timber.w("Проверку зоны не удалось отменить | ошибка=%s", error)
        }
    }

    private suspend fun refreshZones() {
        val agent = agentId ?: return
        val camera = cameraId ?: return
        try {
            val zones = zonesRepository.zones(agent, camera)
            _state.update { it.copy(zones = zones) }
        } catch (error: AppError) {
            Timber.w("Список зон не обновился | ошибка=%s", error)
        }
    }

    /** Меняет проверку, только если на экране всё ещё она: после отмены поздний ответ не воскресит экран. */
    private fun updateCheck(zoneId: String, transform: (ZoneCheck) -> ZoneCheck) {
        _state.update { state ->
            val check = state.check?.takeIf { it.zoneId == zoneId } ?: return@update state
            state.copy(check = transform(check))
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

    private companion object {
        const val POLL_INTERVAL_MS = 1_000L
        const val MAX_NETWORK_FAILURES = 5
    }
}
