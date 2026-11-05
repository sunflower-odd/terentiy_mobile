package ru.terentiy.mobile.ui.zones

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration.Companion.milliseconds
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
import ru.terentiy.mobile.domain.ZonePatch
import ru.terentiy.mobile.domain.ZoneRedraw
import ru.terentiy.mobile.domain.ZoneRule
import ru.terentiy.mobile.domain.ZoneBindingFailure
import ru.terentiy.mobile.domain.ZoneBindingSession
import ru.terentiy.mobile.domain.ZoneBindingState
import ru.terentiy.mobile.domain.ZoneDraft
import ru.terentiy.mobile.domain.ZoneGeometry
import ru.terentiy.mobile.domain.ZoneObservation
import ru.terentiy.mobile.domain.ZonePoint
import ru.terentiy.mobile.domain.ZoneSchedule
import ru.terentiy.mobile.domain.ZoneTrigger
import timber.log.Timber

/**
 * Зона, которую сейчас рисуют. [id] и [revision] — `null` у новой зоны.
 * [original] — зона, какой её открыли: по ней видно, что изменилось.
 */
data class EditedZone(
    val id: String? = null,
    val revision: Int? = null,
    val name: String = "",
    val points: List<ZonePoint> = emptyList(),
    val rule: ZoneRule = ZoneRule(),
    val original: Zone? = null,
) {
    /** Контур изменён: при сохранении нужен свежий кадр, проверка зоны сбросится. */
    val redrawn: Boolean get() = original == null || points != original.polygon
}

/**
 * Проверка зоны на экране. [state] — `null`, пока сервер не ответил на запуск.
 * [errorRes] — сбой запроса (сеть, сервер), а не результат проверки.
 * [frame] — последний кадр, на котором сервер искал человека; маркер [observation]
 * рисуется поверх него.
 */
data class ZoneCheck(
    /** Номер попытки: у повторной проверки той же зоны — новый. */
    val attempt: Long = 0,
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ZoneCheck) return false
        return attempt == other.attempt && zoneId == other.zoneId && zoneName == other.zoneName && polygon == other.polygon &&
            sessionId == other.sessionId && state == other.state && observation == other.observation &&
            failure == other.failure && errorRes == other.errorRes && frameMediaId == other.frameMediaId &&
            (frame?.contentEquals(other.frame) ?: (other.frame == null))
    }

    override fun hashCode(): Int {
        var result = attempt.hashCode()
        result = 31 * result + zoneId.hashCode()
        result = 31 * result + (state?.hashCode() ?: 0)
        result = 31 * result + (observation?.hashCode() ?: 0)
        result = 31 * result + (frameMediaId?.hashCode() ?: 0)
        result = 31 * result + (errorRes ?: 0)
        return result
    }
}

/**
 * Окно «Режим тревоги»: что считать тревогой и в какое время. [zoneId] — зона из
 * списка, сохраняется сразу; `null` — зона, которую сейчас рисуют, правило
 * уходит вместе с ней. [schedule] хранится и при «Всегда» — чтобы при повторном
 * выборе «В заданное время» вернулось введённое. [errorRes] — сбой сохранения.
 */
data class AlertEditor(
    val zoneId: String?,
    val zoneName: String,
    val trigger: ZoneTrigger,
    val scheduled: Boolean,
    val schedule: ZoneSchedule,
    val saving: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
) {
    /** Начало и конец совпадают — промежуток пустой, сохранять нечего. */
    val problemRes: Int? get() = if (scheduled && schedule.empty) R.string.zone_alert_problem_empty else null
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
    /** Открытое окно настроек тревоги; `null` — закрыто. */
    val alertEditor: AlertEditor? = null,
    /** Зоны, у которых переключатель сейчас сохраняется на сервере. */
    val switchingZoneIds: Set<String> = emptySet(),
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
            errorRes == other.errorRes && problemRes == other.problemRes && check == other.check &&
            alertEditor == other.alertEditor && switchingZoneIds == other.switchingZoneIds
    }

    override fun hashCode(): Int {
        var result = zones.hashCode()
        result = 31 * result + (edited?.hashCode() ?: 0)
        result = 31 * result + (frameId?.hashCode() ?: 0)
        result = 31 * result + (frame?.contentHashCode() ?: 0)
        result = 31 * result + loading.hashCode() + frameLoading.hashCode() + saving.hashCode()
        result = 31 * result + (check?.hashCode() ?: 0)
        result = 31 * result + (alertEditor?.hashCode() ?: 0) + switchingZoneIds.hashCode()
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
 * У каждой зоны есть переключатель и условие тревоги: вход или выход, всегда
 * или в заданный промежуток (см. [openAlert], [setZoneEnabled]). Новая зона
 * уходит на сервер вместе с условием; правка — частичная (PATCH): проверку
 * зоны сбрасывает только изменённый контур.
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
    private var lastAttempt = 0L

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
            it.copy(
                edited = EditedZone(zone.id, zone.revision, zone.name, zone.polygon, zone.rule, original = zone),
                problemRes = null,
                errorRes = null,
            )
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
            edited.redrawn && state.frameId == null -> R.string.zone_no_frame
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
        val original = edited.original
        val revision = edited.revision
        val patch = if (original != null) {
            ZonePatch(
                name = edited.name.trim().takeIf { it != original.name },
                rules = if (edited.rule != original.rule) listOf(edited.rule) + original.rules.drop(1) else null,
            )
        } else {
            null
        }
        if (patch != null && patch.empty && !edited.redrawn) {
            _state.update { it.copy(edited = null, problemRes = null) }
            return
        }
        _state.update { it.copy(saving = true, errorRes = null) }
        viewModelScope.launch {
            try {
                // Сервер принимает `reference_frame_id` только от текущего кадра
                // камеры (не старше 30 с). Кадр, на котором рисовали, к этому
                // моменту обычно уже старый, а у правки это вообще кадр, на котором
                // зону создали. Камера неподвижна, а вершины заданы в долях кадра,
                // поэтому контур переносится на свежий кадр без пересчёта. Без
                // нового контура кадр не нужен: имя и тревогу можно поменять и
                // при выключенной камере.
                val fresh = if (edited.redrawn) zonesRepository.currentFrame(agent, camera) else null
                if (original == null || revision == null || patch == null) {
                    val draft = ZoneDraft(
                        name = edited.name,
                        polygon = edited.points,
                        referenceFrameId = checkNotNull(fresh).frameId,
                        rules = listOf(edited.rule),
                    )
                    zonesRepository.create(agent, camera, draft)
                } else {
                    val redraw = fresh?.let { ZoneRedraw(edited.points, it.frameId) }
                    zonesRepository.patch(agent, camera, original.id, revision, patch.copy(redraw = redraw))
                }
                val zones = zonesRepository.zones(agent, camera)
                _state.update { it.copy(saving = false, edited = null, zones = zones) }
                fresh?.let { showFrame(it) }
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
            Timber.w(error, "Свежий кадр зоны не загрузился")
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
     * Включить или выключить зону. Переключатель меняется сразу; если сервер
     * не сохранил — возвращается назад и показывается ошибка.
     */
    fun setZoneEnabled(zoneId: String, enabled: Boolean) {
        val agent = agentId ?: return
        val camera = cameraId ?: return
        val state = _state.value
        if (zoneId in state.switchingZoneIds) return
        val zone = state.zones.firstOrNull { it.id == zoneId } ?: return
        if (zone.enabled == enabled) return
        _state.update {
            it.copy(
                zones = it.zones.map { item -> if (item.id == zoneId) item.copy(enabled = enabled) else item },
                switchingZoneIds = it.switchingZoneIds + zoneId,
                errorRes = null,
            )
        }
        viewModelScope.launch {
            try {
                val saved = zonesRepository.patch(agent, camera, zoneId, zone.revision, ZonePatch(enabled = enabled))
                _state.update { it.copy(zones = it.zones.replaceZone(saved), switchingZoneIds = it.switchingZoneIds - zoneId) }
            } catch (error: AppError) {
                _state.update {
                    it.copy(
                        zones = it.zones.map { item -> if (item.id == zoneId) item.copy(enabled = zone.enabled) else item },
                        switchingZoneIds = it.switchingZoneIds - zoneId,
                        errorRes = error.messageRes(),
                    )
                }
                if (error == AppError.ZoneConflict) refreshZones()
            }
        }
    }

    /** Тревога зоны из списка: сохраняется сразу, проверку зоны не сбрасывает. */
    fun openAlert(zoneId: String) {
        val state = _state.value
        if (state.editing || state.check != null) return
        val zone = state.zones.firstOrNull { it.id == zoneId } ?: return
        _state.update { it.copy(alertEditor = alertEditor(zone.id, zone.name, zone.rule)) }
    }

    /** Тревога зоны, которую сейчас рисуют: уходит на сервер вместе с ней. */
    fun openDraftAlert() {
        val edited = _state.value.edited ?: return
        _state.update { it.copy(alertEditor = alertEditor(null, edited.name.trim(), edited.rule)) }
    }

    private fun alertEditor(zoneId: String?, name: String, rule: ZoneRule) = AlertEditor(
        zoneId = zoneId,
        zoneName = name,
        trigger = rule.trigger,
        scheduled = rule.schedule != null,
        schedule = rule.schedule ?: ZoneSchedule.DEFAULT_NIGHT,
    )

    fun closeAlert() {
        if (_state.value.alertEditor?.saving == true) return
        _state.update { it.copy(alertEditor = null) }
    }

    fun setAlertTrigger(trigger: ZoneTrigger) = changeAlert { it.copy(trigger = trigger) }

    fun setAlertScheduled(scheduled: Boolean) = changeAlert { it.copy(scheduled = scheduled) }

    fun setAlertFrom(time: LocalTime) = changeAlert { it.copy(schedule = it.schedule.copy(from = time)) }

    fun setAlertTo(time: LocalTime) = changeAlert { it.copy(schedule = it.schedule.copy(to = time)) }

    /**
     * Меняется первое правило зоны, остальные (если их задали не в приложении)
     * сохраняются как есть. Идентификатор правила сохраняется.
     */
    fun saveAlert() {
        val editor = _state.value.alertEditor ?: return
        if (editor.saving || editor.problemRes != null) return
        val zoneId = editor.zoneId
        if (zoneId == null) {
            _state.update { state ->
                val edited = state.edited ?: return@update state.copy(alertEditor = null)
                state.copy(edited = edited.copy(rule = editor.applyTo(edited.rule)), alertEditor = null)
            }
            return
        }
        val agent = agentId ?: return
        val camera = cameraId ?: return
        val zone = _state.value.zones.firstOrNull { it.id == zoneId } ?: return
        val rules = listOf(editor.applyTo(zone.rule)) + zone.rules.drop(1)
        _state.update { it.copy(alertEditor = editor.copy(saving = true, errorRes = null)) }
        viewModelScope.launch {
            try {
                val saved = zonesRepository.patch(agent, camera, zone.id, zone.revision, ZonePatch(rules = rules))
                _state.update { it.copy(zones = it.zones.replaceZone(saved), alertEditor = null) }
            } catch (error: AppError) {
                _state.update { state ->
                    state.copy(alertEditor = state.alertEditor?.copy(saving = false, errorRes = error.messageRes()))
                }
                if (error == AppError.ZoneConflict) refreshZones()
            }
        }
    }

    private fun AlertEditor.applyTo(rule: ZoneRule): ZoneRule =
        rule.copy(trigger = trigger, schedule = schedule.takeIf { scheduled })

    private fun changeAlert(transform: (AlertEditor) -> AlertEditor) {
        _state.update { state ->
            val editor = state.alertEditor?.takeUnless { it.saving } ?: return@update state
            state.copy(alertEditor = transform(editor).copy(errorRes = null))
        }
    }

    private fun List<Zone>.replaceZone(zone: Zone): List<Zone> = map { if (it.id == zone.id) zone else it }

    /**
     * Проверка зоны (сессия привязки, mobile-server 2.0.0-draft).
     *
     * Запуск, затем опрос раз в [POLL_INTERVAL_MS], пока сервер не вернёт
     * конечное состояние. Кратковременная потеря сети опрос не обрывает.
     * Собственный предел опроса — срок сессии от сервера плюс [DEADLINE_GRACE_MS]:
     * опрос не бесконечен, даже если сервер сессию не завершит.
     *
     * Кадры загружаются параллельно с опросом и не задерживают его. Каждая
     * попытка получает свой номер: ответы отменённой попытки в новую не попадают.
     */
    fun startCheck(zoneId: String) {
        val agent = agentId ?: return
        val camera = cameraId ?: return
        val state = _state.value
        if (state.editing || state.check?.running == true) return
        val zone = state.zones.firstOrNull { it.id == zoneId } ?: return
        val frame = state.frame?.takeIf { state.frameId == zone.referenceFrameId }
        val attempt = ++lastAttempt
        _state.update {
            it.copy(check = ZoneCheck(attempt, zone.id, zone.name, zone.polygon, frame = frame), errorRes = null)
        }
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            val frames = FrameLoader(this, attempt)
            if (frame == null) frames.load(zone.referenceMediaId)
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
                coroutineContext.ensureActive()
                updateCheck(attempt) { it.copy(errorRes = error.messageRes()) }
                refreshZones()
                return@launch
            }
            coroutineContext.ensureActive()
            applySession(attempt, session, frames)
            val maxPolls = maxPolls(session)
            var polls = 0
            var networkFailures = 0
            while (!session.state.terminal) {
                if (++polls > maxPolls) {
                    Timber.w("Проверка зоны не завершилась в срок | session=%s | state=%s", session.id, session.state.wire)
                    cancelOnServer(agent, camera, zone.id, session.id)
                    updateCheck(attempt) { it.copy(errorRes = R.string.zone_check_failure_unknown) }
                    refreshZones()
                    return@launch
                }
                delay(POLL_INTERVAL_MS.milliseconds)
                session = try {
                    zonesRepository.binding(agent, camera, zone.id, session.id).also { networkFailures = 0 }
                } catch (error: AppError) {
                    if (error == AppError.Network && ++networkFailures < MAX_NETWORK_FAILURES) continue
                    Timber.w(error, "Опрос проверки зоны прерван")
                    updateCheck(attempt) { it.copy(errorRes = error.messageRes()) }
                    refreshZones()
                    return@launch
                }
                applySession(attempt, session, frames)
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

    private fun applySession(attempt: Long, session: ZoneBindingSession, frames: FrameLoader) {
        updateCheck(attempt) {
            it.copy(
                sessionId = session.id,
                state = session.state,
                observation = session.observation ?: it.observation,
                failure = session.failure,
            )
        }
        session.observation?.mediaId?.let(frames::load)
    }

    /**
     * Загрузка кадров проверки рядом с опросом. Пока идёт одна загрузка, новые
     * кадры пропускаются: следующий наблюдённый кадр всё равно придёт через секунду.
     * Не загрузился — остаётся прежний: камера неподвижна, маркер ляжет верно.
     */
    private inner class FrameLoader(private val scope: CoroutineScope, private val attempt: Long) {
        private var job: Job? = null
        private var requested: String? = null

        fun load(mediaId: String) {
            if (mediaId == requested || job?.isActive == true) return
            requested = mediaId
            job = scope.launch {
                try {
                    val bytes = image(mediaId)
                    updateCheck(attempt) { it.copy(frame = bytes, frameMediaId = mediaId) }
                } catch (error: AppError) {
                    Timber.w(error, "Кадр проверки зоны не загрузился")
                }
            }
        }
    }

    /** Предел опроса по сроку сессии от сервера: длительность берётся из его же часов. */
    private fun maxPolls(session: ZoneBindingSession): Int {
        val lifetime = if (session.startedAt != null && session.expiresAt != null) {
            Duration.between(session.startedAt, session.expiresAt).toMillis().coerceAtLeast(0)
        } else {
            FALLBACK_SESSION_MS
        }
        return ((lifetime + DEADLINE_GRACE_MS) / POLL_INTERVAL_MS).toInt().coerceAtLeast(1)
    }

    private suspend fun cancelOnServer(agent: String, camera: String, zoneId: String, sessionId: String) {
        try {
            zonesRepository.cancelBinding(agent, camera, zoneId, sessionId)
        } catch (error: AppError) {
            // Не страшно: сервер сам завершит сессию по таймауту.
            Timber.w(error, "Проверку зоны не удалось отменить")
        }
    }

    private suspend fun refreshZones() {
        val agent = agentId ?: return
        val camera = cameraId ?: return
        try {
            val zones = zonesRepository.zones(agent, camera)
            _state.update { it.copy(zones = zones) }
        } catch (error: AppError) {
            Timber.w(error, "Список зон не обновился")
        }
    }

    /** Меняет проверку, только если на экране всё ещё эта попытка: поздний ответ прежней её не тронет. */
    private fun updateCheck(attempt: Long, transform: (ZoneCheck) -> ZoneCheck) {
        _state.update { state ->
            val check = state.check?.takeIf { it.attempt == attempt } ?: return@update state
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
        const val DEADLINE_GRACE_MS = 15_000L

        /** Если сервер не прислал срок сессии: с запасом больше его таймаута по умолчанию (60 с). */
        const val FALLBACK_SESSION_MS = 300_000L
    }
}
