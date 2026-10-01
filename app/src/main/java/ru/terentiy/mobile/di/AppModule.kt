package ru.terentiy.mobile.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import ru.terentiy.mobile.BuildConfig
import ru.terentiy.mobile.core.network.AuthInterceptor
import ru.terentiy.mobile.core.network.BaseUrlInterceptor
import ru.terentiy.mobile.core.network.MediaTimeoutInterceptor
import ru.terentiy.mobile.data.local.DataStoreSeenEventsStore
import ru.terentiy.mobile.data.local.KeystoreSecureStorage
import ru.terentiy.mobile.data.local.SecureStorage
import ru.terentiy.mobile.data.local.SeenEventsStore
import ru.terentiy.mobile.data.remote.TerentiyApi
import ru.terentiy.mobile.data.repository.AgentsRepository
import ru.terentiy.mobile.data.repository.AuthRepository
import ru.terentiy.mobile.data.repository.EventsRepository
import ru.terentiy.mobile.data.repository.SwitchingAgentsRepository
import ru.terentiy.mobile.data.repository.SwitchingAuthRepository
import ru.terentiy.mobile.data.repository.SwitchingEventsRepository

/**
 * Сборка графа зависимостей.
 *
 * Классы приложения используют конструкторное внедрение и не знают о Hilt
 * ничего, кроме аннотации на конструкторе. Если понадобится отказаться от
 * Hilt, замена затронет этот файл и точки входа, а не логику.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * `ignoreUnknownKeys` обязателен: контракт помечен draft, и новое поле
     * в ответе не должно ронять уже установленное приложение.
     */
    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun okHttpClient(
        baseUrlInterceptor: BaseUrlInterceptor,
        authInterceptor: AuthInterceptor,
        mediaTimeoutInterceptor: MediaTimeoutInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(baseUrlInterceptor)
        .addInterceptor(authInterceptor)
        .addInterceptor(mediaTimeoutInterceptor)
        .apply {
            if (BuildConfig.DEBUG) {
                // BASIC, а не BODY: ответ media — бинарный JPEG или 30-секундный
                // MP4, и печатать его в журнал бессмысленно и вредно.
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
                )
            }
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        // Реальный адрес подставляет BaseUrlInterceptor; здесь нужен лишь
        // синтаксически корректный базовый URL, без которого Retrofit не собирается.
        .baseUrl(BuildConfig.DEFAULT_SERVER_URL.ensureTrailingSlash())
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun terentiyApi(retrofit: Retrofit): TerentiyApi = retrofit.create(TerentiyApi::class.java)

    private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    @Singleton
    abstract fun secureStorage(impl: KeystoreSecureStorage): SecureStorage

    /**
     * Отметки просмотра за интерфейсом: в unit-тестах DataStore не поднимается,
     * и логика водяного знака проверяется на фейке.
     */
    @Binds
    @Singleton
    abstract fun seenEventsStore(impl: DataStoreSeenEventsStore): SeenEventsStore

    @Binds
    @Singleton
    abstract fun authRepository(impl: SwitchingAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun agentsRepository(impl: SwitchingAgentsRepository): AgentsRepository

    @Binds
    @Singleton
    abstract fun eventsRepository(impl: SwitchingEventsRepository): EventsRepository
}
