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

/**
 * Несекретные настройки приложения.
 *
 * Имя пользователя хранится локально: backend после входа отдаёт только
 * `household_id`, а название домовладения в API не возвращается. Дорабатывать
 * backend ради приветствия избыточно.
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

    val isActivated: Flow<Boolean> = store.data.map { it[KEY_ACTIVATED] == true }

    suspend fun currentServerUrl(): String = serverUrl.first()

    suspend fun saveServerUrl(value: String) {
        store.edit { it[KEY_SERVER_URL] = value }
    }

    suspend fun saveActivation(userName: String, householdId: String) {
        store.edit {
            it[KEY_USER_NAME] = userName
            it[KEY_HOUSEHOLD_ID] = householdId
            it[KEY_ACTIVATED] = true
        }
    }

    suspend fun saveHouseholdId(value: String) {
        store.edit { it[KEY_HOUSEHOLD_ID] = value }
    }

    /** Сбрасывает признак активации, сохраняя адрес сервера: он всё ещё верен. */
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
    }
}
