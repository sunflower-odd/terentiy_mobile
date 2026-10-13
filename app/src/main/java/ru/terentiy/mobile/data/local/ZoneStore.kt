package ru.terentiy.mobile.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ru.terentiy.mobile.domain.Zone
import ru.terentiy.mobile.domain.ZonePoint
import timber.log.Timber

/**
 * Зоны на этом телефоне.
 *
 * Интерфейс — ради unit-тестов: DataStore в них не поднимается (см.
 * [SeenEventsStore]).
 */
interface ZoneStore {

    suspend fun all(): List<Zone>

    suspend fun replaceAll(zones: List<Zone>)
}

private val Context.zonesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "terentiy_zones"
)

/**
 * Все зоны одной строкой JSON. Зон не больше десятка на камеру — отдельная
 * база для них избыточна.
 */
@Singleton
class DataStoreZoneStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
) : ZoneStore {

    private val store get() = context.zonesDataStore

    override suspend fun all(): List<Zone> {
        val raw = store.data.first()[KEY_ZONES] ?: return emptyList()
        return runCatching { json.decodeFromString(SERIALIZER, raw) }
            .onFailure { Timber.w(it, "Сохранённые зоны не прочитались и пропущены") }
            .getOrDefault(emptyList())
            .map { it.toDomain() }
    }

    override suspend fun replaceAll(zones: List<Zone>) {
        val raw = json.encodeToString(SERIALIZER, zones.map { it.toStored() })
        store.edit { it[KEY_ZONES] = raw }
    }

    private companion object {
        val KEY_ZONES = stringPreferencesKey("zones")
        val SERIALIZER = ListSerializer(StoredZone.serializer())
    }
}

@Serializable
private data class StoredZone(
    val id: String,
    val agentId: String,
    val name: String,
    /** Вершины в долях кадра: `[[x, y], ...]`. */
    val polygon: List<List<Float>> = emptyList(),
)

private fun StoredZone.toDomain() = Zone(
    id = id,
    agentId = agentId,
    name = name,
    polygon = polygon.mapNotNull { pair -> if (pair.size == 2) ZonePoint(pair[0], pair[1]) else null },
)

private fun Zone.toStored() = StoredZone(
    id = id,
    agentId = agentId,
    name = name,
    polygon = polygon.map { listOf(it.x, it.y) },
)
