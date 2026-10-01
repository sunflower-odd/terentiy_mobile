package ru.terentiy.mobile.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.terentiy.mobile.domain.FallEvent

/**
 * Какие события пользователь уже видел.
 *
 * Признака `reviewed` сервер не хранит — «просмотрено» это понятие **этого
 * устройства**, а не домохозяйства.
 *
 * [watermark] обязателен, и вот почему. Переустановка приложения стирает
 * настройки и выдаёт новый `device_id`; после активации сервер отдаёт всю историю
 * домохозяйства, и без водяного знака все события окажутся «новыми» — приложение
 * при первом запуске закричит о падении трёхдневной давности. Доверие теряется
 * сразу. Поэтому при первом же успешном чтении событий, когда отметок ещё нет,
 * запоминается время самого свежего события: всё, что не новее знака, считается
 * просмотренным. Дальше знак не двигается, двигаются только отметки.
 */
data class SeenState(
    val ids: Set<String> = emptySet(),
    val watermark: Instant? = null,
) {
    fun isSeen(event: FallEvent): Boolean {
        if (event.id in ids) return true
        val mark = watermark ?: return false
        val occurred = event.capturedAt ?: return false
        return !occurred.isAfter(mark)
    }

    companion object {
        val Empty = SeenState()
    }
}

/**
 * Интерфейс нужен не для красоты: в модуле включён
 * `unitTests.isReturnDefaultValues`, DataStore в unit-тесте не поднимается, и
 * фейковая реализация — единственный способ проверить логику знака.
 */
interface SeenEventsStore {

    val state: Flow<SeenState>

    suspend fun markSeen(id: String)

    /** Ставит знак, только если его ещё нет. Повторные вызовы безвредны. */
    suspend fun initialiseWatermark(latest: Instant?)

    /** Убирает отметки событий, которых больше нет в выдаче. */
    suspend fun prune(knownIds: Set<String>)

    suspend fun clear()
}

private val Context.seenDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "terentiy_seen"
)

@Singleton
class DataStoreSeenEventsStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SeenEventsStore {

    private val store get() = context.seenDataStore

    override val state: Flow<SeenState> = store.data.map { preferences ->
        SeenState(
            ids = preferences[KEY_IDS].orEmpty(),
            watermark = preferences[KEY_WATERMARK]?.let {
                runCatching { Instant.parse(it) }.getOrNull()
            },
        )
    }

    override suspend fun markSeen(id: String) {
        store.edit { preferences ->
            preferences[KEY_IDS] = preferences[KEY_IDS].orEmpty() + id
        }
    }

    override suspend fun initialiseWatermark(latest: Instant?) {
        if (latest == null) return
        store.edit { preferences ->
            if (preferences[KEY_WATERMARK] == null && preferences[KEY_IDS].isNullOrEmpty()) {
                preferences[KEY_WATERMARK] = latest.toString()
            }
        }
    }

    override suspend fun prune(knownIds: Set<String>) {
        store.edit { preferences ->
            val kept = preferences[KEY_IDS].orEmpty().intersect(knownIds)
            // Страховка на случай, если выдача внезапно станет огромной.
            preferences[KEY_IDS] = if (kept.size > MAX_IDS) kept.take(MAX_IDS).toSet() else kept
        }
    }

    override suspend fun clear() {
        store.edit { preferences ->
            preferences.remove(KEY_IDS)
            preferences.remove(KEY_WATERMARK)
        }
    }

    private companion object {
        val KEY_IDS = stringSetPreferencesKey("seen_event_ids")
        val KEY_WATERMARK = stringPreferencesKey("seen_watermark")
        const val MAX_IDS = 500
    }
}
