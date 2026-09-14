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
import ru.terentiy.mobile.data.local.KeystoreSecureStorage
import ru.terentiy.mobile.data.local.SecureStorage
import ru.terentiy.mobile.data.remote.TerentiyApi

/**
 * Сборка графа зависимостей.
 *
 * Все классы приложения используют обычное конструкторное внедрение и не знают
 * о Hilt ничего, кроме аннотации на конструкторе. Если понадобится отказаться
 * от Hilt, замена затронет этот файл и точки входа, а не логику.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * `ignoreUnknownKeys` обязателен: backend развивается, и новое поле в ответе
     * не должно ронять уже установленное приложение.
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
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(baseUrlInterceptor)
        .addInterceptor(authInterceptor)
        .apply {
            if (BuildConfig.DEBUG) {
                // BASIC, а не BODY: ответ latest-image содержит два JPEG в base64,
                // и печатать его целиком в журнал бессмысленно и вредно.
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
        // Реальный адрес подставляет BaseUrlInterceptor; здесь нужен лишь синтаксически
        // корректный базовый URL, без которого Retrofit не собирается.
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
abstract class StorageModule {

    @Binds
    @Singleton
    abstract fun secureStorage(impl: KeystoreSecureStorage): SecureStorage
}
