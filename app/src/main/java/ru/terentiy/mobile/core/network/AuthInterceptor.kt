package ru.terentiy.mobile.core.network

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response
import ru.terentiy.mobile.data.local.SecureStorage

/**
 * Подставляет mobile bearer token в защищённые запросы.
 *
 * Токен backend непрозрачный и бессрочный: механики обновления не существует,
 * поэтому здесь нет okhttp3.Authenticator. Ответ `401` обрабатывается выше —
 * локальный токен стирается, и приложение просит PIN заново.
 * Запрос на срок жизни и отзыв токена: docs/backlog-mobile.md §3.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val storage: SecureStorage,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(HEADER) != null) return chain.proceed(request)
        val token = storage.token() ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder().header(HEADER, "Bearer $token").build()
        )
    }

    private companion object {
        const val HEADER = "Authorization"
    }
}
