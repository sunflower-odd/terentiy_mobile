package ru.terentiy.mobile.data.repository.network

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.terentiy.mobile.core.error.AppError
import ru.terentiy.mobile.core.error.ErrorMapper
import ru.terentiy.mobile.core.network.AuthorizedCall
import ru.terentiy.mobile.core.network.ServerUrlHolder
import ru.terentiy.mobile.core.network.apiCall
import ru.terentiy.mobile.data.local.AppSettings
import ru.terentiy.mobile.data.local.SecureStorage
import ru.terentiy.mobile.data.local.SessionHolder
import ru.terentiy.mobile.data.remote.MediaDownloader
import ru.terentiy.mobile.data.remote.MobileActivationRequestDto
import ru.terentiy.mobile.data.remote.MobileLoginRequestDto
import ru.terentiy.mobile.data.remote.StartZoneBindingRequestDto
import ru.terentiy.mobile.data.remote.TerentiyApi
import ru.terentiy.mobile.data.remote.toDomain
import ru.terentiy.mobile.data.repository.AgentsRepository
import ru.terentiy.mobile.data.repository.AuthRepository
import ru.terentiy.mobile.data.repository.ZonesRepository
import ru.terentiy.mobile.data.remote.toRequest
import ru.terentiy.mobile.data.repository.EventsRepository
import ru.terentiy.mobile.domain.Agent
import ru.terentiy.mobile.domain.CurrentFrame
import ru.terentiy.mobile.domain.Zone
import ru.terentiy.mobile.domain.ZonePatch
import ru.terentiy.mobile.domain.ZoneBindingSession
import ru.terentiy.mobile.domain.ZoneDraft
import ru.terentiy.mobile.domain.FallEvent
import ru.terentiy.mobile.domain.MediaPayload
import ru.terentiy.mobile.domain.SessionState
import timber.log.Timber

@Singleton
class NetworkAuthRepository @Inject constructor(
    private val api: TerentiyApi,
    private val storage: SecureStorage,
    private val sessions: SessionHolder,
    private val settings: AppSettings,
    private val serverUrlHolder: ServerUrlHolder,
    private val errorMapper: ErrorMapper,
) : AuthRepository {

    /**
     * Состояние сессии собрано из четырёх источников, и токен среди них —
     * наблюдаемое значение [SessionHolder], а не разовое чтение хранилища. Иначе
     * стирание токена при `401` не доходило бы до навигации: флаг привязки при
     * этом не меняется.
     */
    override val sessionState: Flow<SessionState> = combine(
        settings.isActivated,
        settings.userName,
        settings.householdId,
        sessions.session,
    ) { activated, userName, householdId, session ->
        when {
            !activated -> SessionState.NotActivated
            session == null -> SessionState.NeedsPin
            else -> SessionState.Active(userName, householdId)
        }
    }

    override val serverUrl: Flow<String> = settings.serverUrl

    override suspend fun restoreServerUrl() {
        serverUrlHolder.update(settings.currentServerUrl())
    }

    override suspend fun checkServer(url: String) {
        // Адрес запоминается до запроса: интерцептор берёт его из держателя, а
        // проверять пользователь собирается именно тот адрес, который ввёл.
        serverUrlHolder.update(url.trim())
        apiCall(errorMapper) {
            val response = api.health()
            if (!response.isSuccessful) {
                throw errorMapper.fromResponse(response.code(), null)
            }
            val body = response.body()
            if (body?.database == "unavailable") {
                Timber.w("Сервер отвечает, но база недоступна")
                throw AppError.Server("database_unavailable", response.code())
            }
        }
    }

    override suspend fun activate(
        serverUrl: String,
        installationCode: String,
        userName: String,
        pin: String,
    ) {
        val address = serverUrl.trim()
        settings.saveServerUrl(address)
        serverUrlHolder.update(address)

        val session = apiCall(errorMapper) {
            api.activate(
                MobileActivationRequestDto(
                    installationCode = installationCode.trim(),
                    deviceId = storage.deviceId(),
                    pin = pin,
                )
            ).toDomain()
        }
        sessions.save(session)
        settings.saveActivation(userName = userName.trim(), householdId = session.householdId)
    }

    override suspend fun login(pin: String) {
        val session = apiCall(errorMapper) {
            api.login(MobileLoginRequestDto(deviceId = storage.deviceId(), pin = pin)).toDomain()
        }
        sessions.save(session)
    }

    override suspend fun logout() {
        sessions.clear()
    }

    override suspend fun forgetActivation() {
        val fresh = storage.rotateDeviceId()
        settings.clearActivation()
        sessions.clear()
        Timber.i("Выдан новый идентификатор устройства | tail=%s", fresh.takeLast(6))
    }
}

@Singleton
class NetworkAgentsRepository @Inject constructor(
    private val api: TerentiyApi,
    private val authorized: AuthorizedCall,
) : AgentsRepository {

    override suspend fun agents(): List<Agent> = authorized {
        api.agents().toDomain().map { agent ->
            if (agent.online) agent.copy(hasFreshFrame = hasFreshFrame(agent)) else agent
        }
    }

    /**
     * Свежий кадр есть, если `current-frame` отдал кадр. `camera_unavailable` —
     * кадров нет; остальные ошибки уходят наверх как ошибка опроса.
     */
    private suspend fun hasFreshFrame(agent: Agent): Boolean = try {
        authorized { api.currentFrame(agent.id, agent.cameraId) }
        true
    } catch (error: AppError) {
        if (error != AppError.CameraUnavailable) throw error
        false
    }
}

@Singleton
class NetworkEventsRepository @Inject constructor(
    private val api: TerentiyApi,
    private val downloader: MediaDownloader,
    private val authorized: AuthorizedCall,
) : EventsRepository {

    override suspend fun events(agentId: String?): List<FallEvent> =
        authorized { api.events(agentId).toDomain() }

    override suspend fun media(mediaId: String, onProgress: (Long, Long?) -> Unit): MediaPayload =
        authorized { downloader.download(mediaId, onProgress) }
}

/**
 * Зоны по маршрутам `…/agents/{agent_id}/cameras/{camera_id}/…` (2.0.0-draft).
 *
 * Голый `404` без кода или `405` значит, что на сервере маршрутов ещё нет
 * (зоны делает автор backend отдельно). Пользователю это показывается как
 * «сервер пока не умеет хранить зоны», а не как «запись не найдена».
 */
@Singleton
class NetworkZonesRepository @Inject constructor(
    private val api: TerentiyApi,
    private val authorized: AuthorizedCall,
    private val errorMapper: ErrorMapper,
) : ZonesRepository {

    override suspend fun currentFrame(agentId: String, cameraId: String): CurrentFrame =
        unsupportedAs(AppError.CameraUnavailable) { api.currentFrame(agentId, cameraId).toDomain() }

    override suspend fun zones(agentId: String, cameraId: String): List<Zone> =
        unsupportedAs(AppError.ZonesUnsupported) { api.zones(agentId, cameraId).toDomain() }

    override suspend fun create(agentId: String, cameraId: String, draft: ZoneDraft): Zone =
        unsupportedAs(AppError.ZonesUnsupported) { api.createZone(agentId, cameraId, draft.toRequest()).toDomain() }

    override suspend fun patch(agentId: String, cameraId: String, zoneId: String, revision: Int, patch: ZonePatch): Zone =
        unsupportedAs(AppError.ZonesUnsupported) {
            api.patchZone(agentId, cameraId, zoneId, patch.toRequest(revision)).toDomain()
        }

    override suspend fun delete(agentId: String, cameraId: String, zoneId: String) {
        unsupportedAs(AppError.ZonesUnsupported) {
            val response = api.deleteZone(agentId, cameraId, zoneId)
            // Response<Unit> не бросает HttpException сам — проверяем вручную.
            if (!response.isSuccessful) throw errorMapper.fromResponse(response.code(), response.errorBody()?.string())
        }
    }

    override suspend fun startBinding(agentId: String, cameraId: String, zoneId: String, version: Int): ZoneBindingSession =
        unsupportedAs(AppError.ZonesUnsupported) {
            api.startZoneBinding(agentId, cameraId, zoneId, StartZoneBindingRequestDto(version)).toDomain()
        }

    override suspend fun binding(agentId: String, cameraId: String, zoneId: String, sessionId: String): ZoneBindingSession =
        authorized { api.zoneBinding(agentId, cameraId, zoneId, sessionId).toDomain() }

    override suspend fun cancelBinding(agentId: String, cameraId: String, zoneId: String, sessionId: String) {
        authorized {
            val response = api.cancelZoneBinding(agentId, cameraId, zoneId, sessionId)
            if (!response.isSuccessful) throw errorMapper.fromResponse(response.code(), response.errorBody()?.string())
        }
    }

    private suspend fun <T> unsupportedAs(replacement: AppError, block: suspend () -> T): T = try {
        authorized { block() }
    } catch (error: AppError) {
        val missingRoute = error == AppError.MediaNotFound || (error is AppError.Server && error.code == null && error.status == 405)
        if (!missingRoute) throw error
        Timber.w("Маршрута зон на сервере нет | ответ=%s", error)
        throw replacement
    }
}
