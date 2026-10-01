package ru.terentiy.mobile.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.terentiy.mobile.BuildConfig

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "terentiy_settings"
)

/** Откуда приложение берёт данные. */
enum class DataSourceMode { DEMO, NETWORK }

/**
 * Несекретные настройки приложения.
 *
 * Здесь же живёт выбор источника данных. Он вынесен в настройки, а не в тип
 * сборки, сознательно: демонстрацию удобно показывать без сервера, а на стенде —
 * переключиться на живой backend, не пересобирая приложение.
 */
@Singleton
class AppSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val store get() = context.settingsDataStore

    val userName: Flow<String> = store.data.map { it[KEY_USER_NAME].orEmpty() }

    val householdId: Flow<String> = store.data.map { it[KEY_HOUSEHOLD_ID].orEmpty() }

    val serverUrl: Flow<String> = store.data.map {
        it[KEY_SERVER_URL] ?: BuildConfig.DEFAULT_SERVER_URL
    }

    /** Привязано ли устройство к установке. Токен при этом может быть стёрт. */
    val isActivated: Flow<Boolean> = store.data.map { it[KEY_ACTIVATED] == true }

    val dataSourceMode: Flow<DataSourceMode> = store.data.map { preferences ->
        // По умолчанию живой сервер. Демо было значением по умолчанию и оказалось
        // ловушкой: свежеустановленное приложение активировалось «успешно» против
        // придуманных данных, и чтобы добраться до настоящего сервера, нужно было
        // зайти в Профиль, выключить демо, выйти и активироваться заново.
        when (preferences[KEY_DATA_SOURCE]) {
            DataSourceMode.DEMO.name -> DataSourceMode.DEMO
            else -> DataSourceMode.NETWORK
        }
    }

    suspend fun currentDataSourceMode(): DataSourceMode = dataSourceMode.first()

    suspend fun saveDataSourceMode(mode: DataSourceMode) {
        store.edit { it[KEY_DATA_SOURCE] = mode.name }
    }

    suspend fun currentServerUrl(): String = serverUrl.first()

    suspend fun saveServerUrl(value: String) {
        store.edit { it[KEY_SERVER_URL] = value }
    }

    /**
     * Имя пользователя на сервер не уходит: в `MobileActivationRequest` его нет.
     * Оно нужно только для приветствия на главном экране и живёт здесь.
     */
    suspend fun saveActivation(userName: String, householdId: String) {
        store.edit {
            it[KEY_USER_NAME] = userName
            it[KEY_HOUSEHOLD_ID] = householdId
            it[KEY_ACTIVATED] = true
        }
    }

    /** Сбрасывает привязку, сохраняя адрес сервера и режим: они всё ещё верны. */
    suspend fun clearActivation() {
        store.edit {
            it.remove(KEY_USER_NAME)
            it.remove(KEY_HOUSEHOLD_ID)
            it[KEY_ACTIVATED] = false
        }
    }

    private companion object {
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_HOUSEHOLD_ID = stringPreferencesKey("household_id")
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_ACTIVATED = booleanPreferencesKey("activated")
        val KEY_DATA_SOURCE = stringPreferencesKey("data_source_mode")
    }
}
