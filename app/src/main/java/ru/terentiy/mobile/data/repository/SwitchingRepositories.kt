package ru.terentiy.mobile.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import ru.terentiy.mobile.data.local.AppSettings
import ru.terentiy.mobile.data.local.DataSourceMode
import ru.terentiy.mobile.data.repository.demo.DemoAgentsRepository
import ru.terentiy.mobile.data.repository.demo.DemoAuthRepository
import ru.terentiy.mobile.data.repository.demo.DemoEventsRepository
import ru.terentiy.mobile.data.repository.network.NetworkAgentsRepository
import ru.terentiy.mobile.data.repository.network.NetworkAuthRepository
import ru.terentiy.mobile.data.repository.network.NetworkEventsRepository
import ru.terentiy.mobile.data.repository.network.NetworkZonesRepository
import ru.terentiy.mobile.data.repository.demo.DemoZonesRepository
import ru.terentiy.mobile.domain.ZoneDraft
import ru.terentiy.mobile.domain.CurrentFrame
import ru.terentiy.mobile.domain.Zone
import ru.terentiy.mobile.domain.Agent
import ru.terentiy.mobile.domain.FallEvent
import ru.terentiy.mobile.domain.MediaPayload
import ru.terentiy.mobile.domain.SessionState

/**
 * Выбор источника данных.
 *
 * Режим читается **при каждом вызове**, а не один раз при создании: переключатель
 * в Профиле должен действовать сразу, без перезапуска приложения. Оба источника
 * держат сессию и настройки в одних и тех же хранилищах, поэтому потоки состояния
 * можно брать у любой реализации — берём сетевую.
 *
 * Оговорка, которую стоит помнить: токен, выданный в демо-режиме, для настоящего
 * сервера недействителен. После переключения на живой backend первый же запрос
 * получит `401`, сессия сотрётся и приложение попросит PIN — это ожидаемо и
 * ведёт себя корректно.
 */
@Singleton
class SwitchingAuthRepository @Inject constructor(
    private val settings: AppSettings,
    private val network: NetworkAuthRepository,
    private val demo: DemoAuthRepository,
) : AuthRepository {

    override val sessionState: Flow<SessionState> = network.sessionState

    override val serverUrl: Flow<String> = network.serverUrl

    private suspend fun current(): AuthRepository =
        if (settings.currentDataSourceMode() == DataSourceMode.DEMO) demo else network

    override suspend fun restoreServerUrl() = current().restoreServerUrl()

    override suspend fun checkServer(url: String) = current().checkServer(url)

    override suspend fun activate(
        serverUrl: String,
        installationCode: String,
        userName: String,
        pin: String,
    ) = current().activate(serverUrl, installationCode, userName, pin)

    override suspend fun login(pin: String) = current().login(pin)

    override suspend fun logout() = current().logout()

    override suspend fun forgetActivation() = current().forgetActivation()
}

@Singleton
class SwitchingAgentsRepository @Inject constructor(
    private val settings: AppSettings,
    private val network: NetworkAgentsRepository,
    private val demo: DemoAgentsRepository,
) : AgentsRepository {

    override suspend fun agents(): List<Agent> = current().agents()

    private suspend fun current(): AgentsRepository =
        if (settings.currentDataSourceMode() == DataSourceMode.DEMO) demo else network
}

@Singleton
class SwitchingEventsRepository @Inject constructor(
    private val settings: AppSettings,
    private val network: NetworkEventsRepository,
    private val demo: DemoEventsRepository,
) : EventsRepository {

    override suspend fun events(agentId: String?): List<FallEvent> = current().events(agentId)

    override suspend fun media(mediaId: String, onProgress: (Long, Long?) -> Unit): MediaPayload =
        current().media(mediaId, onProgress)

    private suspend fun current(): EventsRepository =
        if (settings.currentDataSourceMode() == DataSourceMode.DEMO) demo else network
}

@Singleton
class SwitchingZonesRepository @Inject constructor(
    private val settings: AppSettings,
    private val network: NetworkZonesRepository,
    private val demo: DemoZonesRepository,
) : ZonesRepository {

    override suspend fun currentFrame(agentId: String, cameraId: String): CurrentFrame =
        current().currentFrame(agentId, cameraId)

    override suspend fun zones(agentId: String, cameraId: String): List<Zone> = current().zones(agentId, cameraId)

    override suspend fun create(agentId: String, cameraId: String, draft: ZoneDraft): Zone =
        current().create(agentId, cameraId, draft)

    override suspend fun update(agentId: String, cameraId: String, zoneId: String, version: Int, draft: ZoneDraft): Zone =
        current().update(agentId, cameraId, zoneId, version, draft)

    override suspend fun delete(agentId: String, cameraId: String, zoneId: String) =
        current().delete(agentId, cameraId, zoneId)

    private suspend fun current(): ZonesRepository =
        if (settings.currentDataSourceMode() == DataSourceMode.DEMO) demo else network
}
