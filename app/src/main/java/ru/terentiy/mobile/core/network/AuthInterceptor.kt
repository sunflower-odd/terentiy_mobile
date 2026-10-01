package ru.terentiy.mobile.core.network

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response
import ru.terentiy.mobile.data.local.SessionHolder

/**
 * Подставляет токен в защищённые запросы.
 *
 * Значение берётся из [SessionHolder], то есть из памяти: расшифровывать
 * keystore на потоке OkHttp при каждом запросе незачем.
 *
 * Запросы без сохранённой сессии уходят как есть — `health`, `mobile/activate`
 * и `mobile/login` авторизации не требуют. Реакция на `401` живёт в
 * [ru.terentiy.mobile.core.network.AuthorizedCall], а не здесь: обновлять токен
 * нечем, обновления в контракте нет.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessions: SessionHolder,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(HEADER) != null) return chain.proceed(request)
        val token = sessions.current?.accessToken ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder().header(HEADER, "Bearer $token").build()
        )
    }

    private companion object {
        const val HEADER = "Authorization"
    }
}
