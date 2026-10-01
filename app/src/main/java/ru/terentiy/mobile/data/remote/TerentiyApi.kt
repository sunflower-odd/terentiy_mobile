package ru.terentiy.mobile.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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
}
