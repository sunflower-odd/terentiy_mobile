package ru.terentiy.mobile.data.repository

import kotlinx.coroutines.flow.Flow
import ru.terentiy.mobile.domain.Agent
import ru.terentiy.mobile.domain.CurrentFrame
import ru.terentiy.mobile.domain.FallEvent
import ru.terentiy.mobile.domain.MediaPayload
import ru.terentiy.mobile.domain.SessionState
import ru.terentiy.mobile.domain.Zone
import ru.terentiy.mobile.domain.ZoneBindingSession
import ru.terentiy.mobile.domain.ZoneDraft
import ru.terentiy.mobile.domain.ZonePatch

/**
 * Контракты слоя данных.
 *
 * Репозитории объявлены интерфейсами, потому что реализаций две: настоящий
 * сетевой клиент по контракту 1.0.0 и демо-источник. Демо нужен не для тестов, а
 * для показа: сервер требует PostgreSQL, ML-worker и живого агента с камерой, и
 * приложение должно быть показуемо без всего этого. Экраны о выборе не знают —
 * они работают с интерфейсом.
 */

interface AuthRepository {

    val sessionState: Flow<SessionState>

    val serverUrl: Flow<String>

    suspend fun restoreServerUrl()

    /**
     * Проверка адреса через `/health` — единственный маршрут без авторизации.
     * Отличает «сервер не отвечает» от «код установки не подошёл».
     */
    suspend fun checkServer(url: String)

    suspend fun activate(serverUrl: String, installationCode: String, userName: String, pin: String)

    suspend fun login(pin: String)

    /**
     * Выход. Маршрута выхода в контракте нет и токен не отзывается, поэтому это
     * чисто локальное действие: привязка устройства сохраняется, нужен только PIN.
     */
    suspend fun logout()

    /**
     * Полный сброс привязки с выдачей нового `device_id`.
     *
     * Единственный путь для пользователя, забывшего PIN: сброса на сервере нет, а
     * повторная активация того же устройства отклоняется. История домохозяйства
     * при этом не теряется — код установки ведёт в тот же household.
     */
    suspend fun forgetActivation()
}

interface AgentsRepository {
    /** Агенты дома; у агентов на связи заполнено [Agent.hasFreshFrame]. */
    suspend fun agents(): List<Agent>
}

interface EventsRepository {

    /** Вся история домохозяйства, от новых к старым. Пагинации в контракте нет. */
    suspend fun events(agentId: String? = null): List<FallEvent>

    /** Кадр в память, клип — в файл. Тип определяется по `Content-Type`. */
    suspend fun media(mediaId: String, onProgress: (Long, Long?) -> Unit = { _, _ -> }): MediaPayload
}

/**
 * Критические зоны камеры (mobile-server 2.0.0-draft).
 *
 * Зоны хранятся на сервере: вид с камеры, список, рисование, правка, удаление и
 * проверка зоны (сессия привязки). Решение «человек в зоне» принимает только сервер.
 */
interface ZonesRepository {

    /** Последний кадр камеры. JPEG — через [EventsRepository.media]. */
    suspend fun currentFrame(agentId: String, cameraId: String): CurrentFrame

    suspend fun zones(agentId: String, cameraId: String): List<Zone>

    suspend fun create(agentId: String, cameraId: String, draft: ZoneDraft): Zone

    /**
     * Изменить часть зоны. [revision] — номер правки, которую видел пользователь;
     * при расхождении — [ru.terentiy.mobile.core.error.AppError.ZoneConflict].
     * Проверку зоны сбрасывает только новый контур ([ZonePatch.redraw]).
     */
    suspend fun patch(agentId: String, cameraId: String, zoneId: String, revision: Int, patch: ZonePatch): Zone

    suspend fun delete(agentId: String, cameraId: String, zoneId: String)

    /** Начать проверку зоны версии [version]. Зона переходит в «проверяется». */
    suspend fun startBinding(agentId: String, cameraId: String, zoneId: String, version: Int): ZoneBindingSession

    suspend fun binding(agentId: String, cameraId: String, zoneId: String, sessionId: String): ZoneBindingSession

    /** Отменить проверку. Уже завершённая сессия — не ошибка. */
    suspend fun cancelBinding(agentId: String, cameraId: String, zoneId: String, sessionId: String)
}
