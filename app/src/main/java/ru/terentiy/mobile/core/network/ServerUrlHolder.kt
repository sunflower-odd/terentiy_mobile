package ru.terentiy.mobile.core.network

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Текущий адрес backend, доступный из интерцептора OkHttp без блокировок.
 *
 * Адрес живёт в DataStore, но интерцептор работает на потоке OkHttp, где ждать
 * корутину нельзя. Поэтому значение держится в памяти: репозиторий обновляет его
 * при старте приложения и при каждом сохранении нового адреса.
 */
@Singleton
class ServerUrlHolder @Inject constructor() {

    @Volatile
    private var url: HttpUrl? = null

    fun update(value: String?) {
        url = value?.trim()?.takeIf { it.isNotEmpty() }?.toHttpUrlOrNull()
    }

    fun current(): HttpUrl? = url
}
