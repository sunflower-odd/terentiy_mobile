package ru.terentiy.mobile.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import ru.terentiy.mobile.core.error.AppError
import ru.terentiy.mobile.core.error.ErrorMapper
import ru.terentiy.mobile.core.network.apiCall
import ru.terentiy.mobile.data.remote.TerentiyApi
import ru.terentiy.mobile.data.remote.toDomain
import ru.terentiy.mobile.domain.Agent
import ru.terentiy.mobile.domain.LatestImage

/**
 * Агенты установки и кадры с их камер.
 *
 * Список агентов опрашивается периодически — это несколько килобайт на запрос.
 * Кадр запрашивается только по явному действию пользователя: ответ содержит два
 * JPEG в base64 и весит на три порядка больше.
 * См. docs/adr/0002-kadr-po-zaprosu.md.
 */
@Singleton
class AgentsRepository @Inject constructor(
    private val api: TerentiyApi,
    private val auth: AuthRepository,
    private val errorMapper: ErrorMapper,
) {

    suspend fun agents(): List<Agent> = authorized {
        api.agents().items.map { it.toDomain() }
    }

    suspend fun latestImage(agentId: String): LatestImage = authorized {
        api.latestImage(agentId).toDomain()
    }

    /**
     * Токен backend не обновляется, поэтому `401` означает только одно: войти заново.
     * Локальный токен стирается сразу, чтобы приложение не повторяло заведомо
     * безнадёжные запросы, а сессия честно перешла в состояние «нужен PIN».
     */
    private suspend fun <T> authorized(block: suspend () -> T): T =
        try {
            apiCall(errorMapper, block)
        } catch (error: AppError) {
            if (error is AppError.Unauthorized) auth.dropToken()
            throw error
        }
}
