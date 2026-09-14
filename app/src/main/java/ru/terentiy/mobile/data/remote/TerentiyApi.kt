package ru.terentiy.mobile.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Мобильная часть API backend.
 *
 * Операции агента (`POST /api/v1/agents/activate`, `PUT .../status`,
 * `PUT .../latest-image`) в контракт мобильного клиента не входят намеренно.
 */
interface TerentiyApi {

    @POST("api/v1/mobile/activate")
    suspend fun activate(@Body body: MobileActivationRequestDto): MobileTokenDto

    @POST("api/v1/mobile/login")
    suspend fun login(@Body body: MobileLoginRequestDto): MobileTokenDto

    @GET("api/v1/agents")
    suspend fun agents(): AgentListDto

    @GET("api/v1/agents/{agentId}/latest-image")
    suspend fun latestImage(@Path("agentId") agentId: String): LatestImageDto
}
