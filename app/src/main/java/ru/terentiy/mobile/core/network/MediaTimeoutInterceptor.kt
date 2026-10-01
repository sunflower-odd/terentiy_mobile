package ru.terentiy.mobile.core.network

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Поднимает таймаут чтения для медиа.
 *
 * Обычные ответы сервера — килобайты, и тридцати секунд им более чем достаточно.
 * Клип — до 128 МБ одним телом без поддержки Range: по домашнему Wi-Fi это
 * минуты, и общий таймаут порвал бы загрузку на середине, а докачать её нельзя.
 *
 * Отдельный [okhttp3.OkHttpClient] ради этого не нужен — таймаут меняется на
 * уровне цепочки.
 */
@Singleton
class MediaTimeoutInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.encodedPath.contains(MEDIA_PATH)) return chain.proceed(request)
        return chain
            .withReadTimeout(MEDIA_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .proceed(request)
    }

    private companion object {
        const val MEDIA_PATH = "/api/v1/media/"
        const val MEDIA_READ_TIMEOUT_MINUTES = 5
    }
}
