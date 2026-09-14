package ru.terentiy.mobile.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.terentiy.mobile.core.error.ErrorMapper
import ru.terentiy.mobile.core.network.ServerUrlHolder
import ru.terentiy.mobile.core.network.apiCall
import ru.terentiy.mobile.data.local.AppSettings
import ru.terentiy.mobile.data.local.SecureStorage
import ru.terentiy.mobile.data.remote.MobileActivationRequestDto
import ru.terentiy.mobile.data.remote.MobileLoginRequestDto
import ru.terentiy.mobile.data.remote.TerentiyApi
import ru.terentiy.mobile.domain.Session
import timber.log.Timber

/**
 * Активация устройства, вход по PIN и состояние локальной сессии.
 *
 * Токен backend бессрочный и не обновляется, поэтому «выход» — это стирание токена
 * на устройстве. Серверная строка в `access_tokens` при этом остаётся жить;
 * отзыв токена зафиксирован как запрос к backend (docs/backlog-mobile.md §3).
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: TerentiyApi,
    private val settings: AppSettings,
    private val storage: SecureStorage,
    private val serverUrlHolder: ServerUrlHolder,
    private val errorMapper: ErrorMapper,
) {

    val session: Flow<Session> =
        combine(settings.isActivated, settings.userName, settings.householdId) { activated, name, household ->
            when {
                !activated -> Session.NotActivated
                storage.token() == null -> Session.NeedsPin
                else -> Session.Active(userName = name, householdId = household)
            }
        }

    val serverUrl: Flow<String> = settings.serverUrl

    /** Подхватывает сохранённый адрес сервера при старте приложения. */
    suspend fun restoreServerUrl() {
        serverUrlHolder.update(settings.currentServerUrl())
    }

    suspend fun activate(serverUrl: String, installationCode: String, userName: String, pin: String) {
        settings.saveServerUrl(serverUrl)
        serverUrlHolder.update(serverUrl)

        val token = apiCall(errorMapper) {
            api.activate(
                MobileActivationRequestDto(
                    installationCode = installationCode,
                    deviceId = storage.deviceId(),
                    pin = pin,
                )
            )
        }
        storage.saveToken(token.accessToken)
        settings.saveActivation(userName = userName, householdId = token.householdId)
        Timber.i("Устройство активировано | household=%s", token.householdId)
    }

    suspend fun login(pin: String) {
        val token = apiCall(errorMapper) {
            api.login(MobileLoginRequestDto(deviceId = storage.deviceId(), pin = pin))
        }
        storage.saveToken(token.accessToken)
        settings.saveHouseholdId(token.householdId)
        Timber.i("Вход выполнен | household=%s", token.householdId)
    }

    /** Сбрасывает только токен: устройство остаётся активированным, нужен PIN. */
    fun dropToken() {
        storage.clearToken()
    }

    /** Полный сброс привязки. Следующий запуск начнётся с экрана активации. */
    suspend fun forgetActivation() {
        storage.clearToken()
        settings.clearActivation()
    }
}
