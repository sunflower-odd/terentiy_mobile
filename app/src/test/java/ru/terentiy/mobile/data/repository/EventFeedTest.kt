package ru.terentiy.mobile.data.repository

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.terentiy.mobile.core.error.AppError
import ru.terentiy.mobile.data.local.SeenEventsStore
import ru.terentiy.mobile.data.local.SeenState
import ru.terentiy.mobile.domain.EventStatus
import ru.terentiy.mobile.domain.FallEvent
import ru.terentiy.mobile.domain.MediaPayload

/**
 * Лента событий — единственный источник событий в приложении: её читают баннер
 * тревоги, история и карточка события. Отдельного маршрута за карточкой нет,
 * поэтому поведение кеша здесь такое же важное, как разбор ответа.
 */
class EventFeedTest {

    private val noon: Instant = Instant.parse("2026-09-30T12:00:00Z")

    private fun event(id: String, at: Instant?) = FallEvent(
        id = id,
        agentId = "agent-1",
        frameId = "$id-frame",
        frameMediaId = "$id-frame-media",
        clipMediaId = "$id-clip",
        capturedAt = at,
        createdAt = at,
        status = EventStatus.CONFIRMED,
        analysis = null,
    )

    private class FakeEvents : EventsRepository {
        var calls = 0
        var result: List<FallEvent> = emptyList()
        var error: AppError? = null

        override suspend fun events(agentId: String?): List<FallEvent> {
            calls++
            error?.let { throw it }
            return result
        }

        override suspend fun media(mediaId: String, onProgress: (Long, Long?) -> Unit): MediaPayload =
            throw AppError.MediaNotFound
    }

    /** DataStore в unit-тесте не поднимается, поэтому логика знака проверяется на фейке. */
    private class FakeSeen : SeenEventsStore {
        private val flow = MutableStateFlow(SeenState())
        val watermarkCalls = mutableListOf<Instant?>()
        var pruned: Set<String>? = null

        override val state: Flow<SeenState> = flow

        override suspend fun markSeen(id: String) {
            flow.value = flow.value.copy(ids = flow.value.ids + id)
        }

        override suspend fun initialiseWatermark(latest: Instant?) {
            watermarkCalls += latest
            if (latest != null && flow.value.watermark == null && flow.value.ids.isEmpty()) {
                flow.value = flow.value.copy(watermark = latest)
            }
        }

        override suspend fun prune(knownIds: Set<String>) {
            pruned = knownIds
            flow.value = flow.value.copy(ids = flow.value.ids.intersect(knownIds))
        }

        override suspend fun clear() {
            flow.value = SeenState()
        }
    }

    @Test
    fun `первое чтение помечает существующую историю просмотренной`() = runTest {
        // Это сценарий переустановки: приходит вся история домохозяйства, и без
        // водяного знака приложение закричало бы о падении трёхдневной давности.
        val events = FakeEvents().apply {
            result = listOf(
                event("old-1", noon.minusSeconds(3600)),
                event("old-2", noon.minusSeconds(7200)),
            )
        }
        val seen = FakeSeen()
        val feed = EventFeed(events, seen)

        feed.refresh()

        assertEquals(listOf(noon.minusSeconds(3600)), seen.watermarkCalls)
        assertEquals(0, feed.state.value.unseenCount)
        assertNull(feed.state.value.latestUnseen)
    }

    @Test
    fun `событие новее знака считается новым`() = runTest {
        val events = FakeEvents().apply { result = listOf(event("old", noon.minusSeconds(3600))) }
        val seen = FakeSeen()
        val feed = EventFeed(events, seen)
        feed.refresh()

        events.result = listOf(event("fresh", noon), event("old", noon.minusSeconds(3600)))
        feed.refresh()

        assertEquals(1, feed.state.value.unseenCount)
        assertEquals("fresh", feed.state.value.latestUnseen?.id)
    }

    @Test
    fun `открытие события гасит отметку сразу, не дожидаясь опроса`() = runTest {
        val events = FakeEvents().apply { result = listOf(event("old", noon.minusSeconds(3600))) }
        val feed = EventFeed(events, FakeSeen())
        feed.refresh()
        events.result = listOf(event("fresh", noon), event("old", noon.minusSeconds(3600)))
        feed.refresh()

        feed.markSeen("fresh")

        assertEquals(0, feed.state.value.unseenCount)
    }

    @Test
    fun `карточка берёт событие из ленты, не обращаясь к сети`() = runTest {
        val events = FakeEvents().apply { result = listOf(event("e-1", noon)) }
        val feed = EventFeed(events, FakeSeen())
        feed.refresh()
        val callsAfterLoad = events.calls

        assertNotNull(feed.cached("e-1"))
        assertNull(feed.cached("нет такого"))
        assertEquals(callsAfterLoad, events.calls)
    }

    @Test
    fun `отметки исчезнувших событий вычищаются`() = runTest {
        val events = FakeEvents().apply { result = listOf(event("e-1", noon), event("e-2", noon.minusSeconds(60))) }
        val seen = FakeSeen()
        val feed = EventFeed(events, seen)
        feed.refresh()

        assertEquals(setOf("e-1", "e-2"), seen.pruned)
    }

    @Test
    fun `при потере связи последние данные остаются на экране`() = runTest {
        val events = FakeEvents().apply { result = listOf(event("e-1", noon)) }
        val feed = EventFeed(events, FakeSeen())
        feed.refresh()

        events.error = AppError.Network
        feed.refresh()

        val state = feed.state.value
        assertEquals(1, state.events.size)
        assertEquals(AppError.Network, state.error)
        assertTrue(state.offline)
    }

    @Test
    fun `ошибка на первом чтении снимает признак загрузки`() = runTest {
        val events = FakeEvents().apply { error = AppError.Unauthorized }
        val feed = EventFeed(events, FakeSeen())

        feed.refresh()

        val state = feed.state.value
        assertEquals(false, state.firstLoad)
        assertEquals(AppError.Unauthorized, state.error)
        // Показывать нечего, и «нет связи» тут неуместно: связь есть, а токен нет.
        assertEquals(false, state.offline)
    }
}
