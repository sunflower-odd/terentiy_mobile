package ru.terentiy.mobile.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Мобильная часть API по контракту 1.0.0.
 *
 * Пять маршрутов плюс `/health` — это всё, что сервер отдаёт мобильному клиенту.
 * Операции агента (`agents/activate`, `frames`, `commands`, `clips`) описаны
 * отдельным контрактом `terentiy-api.openapi.yaml` и в этот интерфейс не входят
 * намеренно: мобильный клиент их не вызывает и знать о них не должен.
 */
interface TerentiyApi {

    /**
     * Единственный маршрут без авторизации.
     *
     * Нужен на экране подключения: он отличает «сервер не отвечает» от «код
     * установки не подошёл». На стенде, где адрес ноутбука меняется от сети к
     * сети, это экономит больше времени, чем стоит.
     */
    @GET("health")
    suspend fun health(): Response<HealthDto>

    @POST("api/v1/mobile/activate")
    suspend fun activate(@Body body: MobileActivationRequestDto): MobileTokenDto

    @POST("api/v1/mobile/login")
    suspend fun login(@Body body: MobileLoginRequestDto): MobileTokenDto

    @GET("api/v1/agents")
    suspend fun agents(): AgentListDto

    /**
     * Вся история домохозяйства, от новых к старым.
     *
     * Ни курсора, ни `limit`, ни `since` в контракте нет — фильтр по агенту
     * единственный. Просьба добавить ограничение записана в
     * docs/backlog-mobile.md: вместе с каждым событием приходит его ML-диагностика.
     */
    @GET("api/v1/events")
    suspend fun events(@Query("agent_id") agentId: String? = null): FallEventListDto

    /**
     * Кадр или клип: различаются только по `Content-Type` ответа.
     *
     * `@Streaming` обязателен. Клип — до 128 МБ, сервер отдаёт его одним телом
     * без поддержки Range, и поднимать это в память нельзя.
     */
    @Streaming
    @GET("api/v1/media/{mediaId}")
    suspend fun media(@Path("mediaId") mediaId: String): Response<ResponseBody>

    // --- Критические зоны (mobile-server 2.0.0-draft) ---

    /** Последний кадр камеры для разметки. Сам JPEG — через [media]. */
    @GET("api/v1/agents/{agentId}/cameras/{cameraId}/current-frame")
    suspend fun currentFrame(@Path("agentId") agentId: String, @Path("cameraId") cameraId: String): CurrentFrameDto

    @GET("api/v1/agents/{agentId}/cameras/{cameraId}/zones")
    suspend fun zones(@Path("agentId") agentId: String, @Path("cameraId") cameraId: String): ZoneListDto

    @POST("api/v1/agents/{agentId}/cameras/{cameraId}/zones")
    suspend fun createZone(
        @Path("agentId") agentId: String,
        @Path("cameraId") cameraId: String,
        @Body body: ZoneRequestDto,
    ): ZoneDto

    @PUT("api/v1/agents/{agentId}/cameras/{cameraId}/zones/{zoneId}")
    suspend fun updateZone(
        @Path("agentId") agentId: String,
        @Path("cameraId") cameraId: String,
        @Path("zoneId") zoneId: String,
        @Body body: ZoneUpdateRequestDto,
    ): ZoneDto

    /** `204` без тела: `Response<Unit>`, иначе конвертер попытается разобрать пустоту. */
    @DELETE("api/v1/agents/{agentId}/cameras/{cameraId}/zones/{zoneId}")
    suspend fun deleteZone(
        @Path("agentId") agentId: String,
        @Path("cameraId") cameraId: String,
        @Path("zoneId") zoneId: String,
    ): Response<Unit>

    /** Начать проверку зоны: человек встаёт в неё, сервер подтверждает по свежим кадрам. */
    @POST("api/v1/agents/{agentId}/cameras/{cameraId}/zones/{zoneId}/binding-sessions")
    suspend fun startZoneBinding(
        @Path("agentId") agentId: String,
        @Path("cameraId") cameraId: String,
        @Path("zoneId") zoneId: String,
        @Body body: StartZoneBindingRequestDto,
    ): ZoneBindingSessionDto

    @GET("api/v1/agents/{agentId}/cameras/{cameraId}/zones/{zoneId}/binding-sessions/{sessionId}")
    suspend fun zoneBinding(
        @Path("agentId") agentId: String,
        @Path("cameraId") cameraId: String,
        @Path("zoneId") zoneId: String,
        @Path("sessionId") sessionId: String,
    ): ZoneBindingSessionDto

    /** `204` без тела, в том числе если сессия уже завершилась. */
    @DELETE("api/v1/agents/{agentId}/cameras/{cameraId}/zones/{zoneId}/binding-sessions/{sessionId}")
    suspend fun cancelZoneBinding(
        @Path("agentId") agentId: String,
        @Path("cameraId") cameraId: String,
        @Path("zoneId") zoneId: String,
        @Path("sessionId") sessionId: String,
    ): Response<Unit>
}
