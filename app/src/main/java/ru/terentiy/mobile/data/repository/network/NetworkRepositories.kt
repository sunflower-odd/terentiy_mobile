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
import ru.terentiy.mobile.data.remote.TerentiyApi
import ru.terentiy.mobile.data.remote.toDomain
import ru.terentiy.mobile.data.repository.AgentsRepository
import ru.terentiy.mobile.data.repository.AuthRepository
import ru.terentiy.mobile.data.repository.EventsRepository
import ru.terentiy.mobile.domain.Agent
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

    override suspend fun agents(): List<Agent> = authorized { api.agents().toDomain() }
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
