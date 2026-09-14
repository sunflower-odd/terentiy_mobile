package ru.terentiy.mobile.core.network

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Подменяет схему, хост и порт запроса на адрес, который ввёл пользователь.
 *
 * Retrofit требует базовый адрес в момент создания, но на Stage 01 backend живёт
 * на ноутбуке разработчика и его адрес заранее неизвестен: вшить его в сборку
 * означало бы пересобирать APK под каждую сеть. Путь запроса при подмене
 * сохраняется — сохранённый адрес это всегда `схема://хост:порт` без префикса.
 */
@Singleton
class BaseUrlInterceptor @Inject constructor(
    private val holder: ServerUrlHolder,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val target = holder.current() ?: return chain.proceed(chain.request())
        val request = chain.request()
        val rewritten = request.url.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .build()
        return chain.proceed(request.newBuilder().url(rewritten).build())
    }
}
