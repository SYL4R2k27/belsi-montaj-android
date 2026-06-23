package com.belsi.work.data.remote.api

import com.belsi.work.data.models.F3AcceptMetersBody
import com.belsi.work.data.models.F3AcceptMetersResponse
import com.belsi.work.data.models.F3AdjustmentBatchBody
import com.belsi.work.data.models.F3AdjustmentBatchResponse
import com.belsi.work.data.models.F3AssemblePayoutBody
import com.belsi.work.data.models.F3CompanyBalanceDto
import com.belsi.work.data.models.F3CompanyBalanceThresholdBody
import com.belsi.work.data.models.F3DayFormatBody
import com.belsi.work.data.models.F3DayFormatResponse
import com.belsi.work.data.models.F3DayMetersBody
import com.belsi.work.data.models.F3EarningDto
import com.belsi.work.data.models.F3EarningEditBody
import com.belsi.work.data.models.F3EarningsPage
import com.belsi.work.data.models.F3ForecastDto
import com.belsi.work.data.models.F3GlobalHourRateResponse
import com.belsi.work.data.models.F3HourRateBody
import com.belsi.work.data.models.F3HourRatesResponse
import com.belsi.work.data.models.F3PayoutDto
import com.belsi.work.data.models.F3PayoutsPage
import com.belsi.work.data.models.F3RecomputeBody
import com.belsi.work.data.models.F3RecomputeResponse
import com.belsi.work.data.models.F3UserHourRateBody
import com.belsi.work.data.models.F3UserHourRateResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Кошелёк BELSI — контракт Ф3 (app/wallet_f3.py).
 * Расчёт по часам/метрам + штраф/бонус + выплаты Rocket Work + баланс компании.
 * Все пути под base "wallet/". send/sync могут вернуть 503 (PAYOUTS_ENABLED=0) — gracefully.
 */
interface WalletF3Api {

    // ========================================================
    // Куратор: часовые ставки
    // ========================================================
    @GET("wallet/curator/hour-rates")
    suspend fun getHourRates(): Response<F3HourRatesResponse>

    @PUT("wallet/curator/hour-rates")
    suspend fun setGlobalHourRate(@Body body: F3HourRateBody): Response<F3GlobalHourRateResponse>

    @PUT("wallet/curator/hour-rates/{userId}")
    suspend fun setUserHourRate(
        @Path("userId") userId: String,
        @Body body: F3UserHourRateBody,
    ): Response<F3UserHourRateResponse>

    // ========================================================
    // Куратор: формат дня + расчёт начислений
    // ========================================================
    @PUT("wallet/curator/day-format")
    suspend fun setDayFormat(@Body body: F3DayFormatBody): Response<F3DayFormatResponse>

    @POST("wallet/curator/earnings/recompute")
    suspend fun recomputeEarnings(@Body body: F3RecomputeBody): Response<F3RecomputeResponse>

    @GET("wallet/curator/earnings")
    suspend fun getEarnings(
        @Query("user_id") userId: String,
        @Query("year") year: Int,
        @Query("month") month: Int,
    ): Response<F3EarningsPage>

    @PUT("wallet/curator/earnings/{id}")
    suspend fun editEarning(
        @Path("id") id: String,
        @Body body: F3EarningEditBody,
    ): Response<F3EarningDto>

    @POST("wallet/curator/earnings/{id}/approve")
    suspend fun approveEarning(@Path("id") id: String): Response<F3EarningDto>

    @POST("wallet/curator/earnings/{id}/void")
    suspend fun voidEarning(@Path("id") id: String): Response<F3EarningDto>

    // ========================================================
    // Куратор: приёмка кабинета → метро-начисления
    // ========================================================
    @POST("wallet/curator/cabinets/{cabinetId}/accept-meters")
    suspend fun acceptCabinetMeters(
        @Path("cabinetId") cabinetId: String,
        @Body body: F3AcceptMetersBody,
    ): Response<F3AcceptMetersResponse>

    // ========================================================
    // Куратор: штраф / бонус (мультивыбор)
    // ========================================================
    @POST("wallet/curator/adjustments/batch")
    suspend fun createAdjustmentBatch(@Body body: F3AdjustmentBatchBody): Response<F3AdjustmentBatchResponse>

    // ========================================================
    // Куратор: выплаты (Rocket Work)
    // ========================================================
    @POST("wallet/curator/payouts/assemble")
    suspend fun assemblePayout(@Body body: F3AssemblePayoutBody): Response<F3PayoutDto>

    @GET("wallet/curator/payouts")
    suspend fun getPayouts(
        @Query("user_id") userId: String? = null,
        @Query("status") status: String? = null,
    ): Response<F3PayoutsPage>

    @GET("wallet/curator/payouts/{id}")
    suspend fun getPayout(@Path("id") id: String): Response<F3PayoutDto>

    @POST("wallet/curator/payouts/{id}/approve")
    suspend fun approvePayout(@Path("id") id: String): Response<F3PayoutDto>

    /** 503 если PAYOUTS_ENABLED off — репозиторий surface'ит дружелюбное сообщение. */
    @POST("wallet/curator/payouts/{id}/send")
    suspend fun sendPayout(@Path("id") id: String): Response<F3PayoutDto>

    @POST("wallet/curator/payouts/{id}/mark-paid")
    suspend fun markPayoutPaid(@Path("id") id: String): Response<F3PayoutDto>

    @POST("wallet/curator/payouts/{id}/cancel")
    suspend fun cancelPayout(@Path("id") id: String): Response<F3PayoutDto>

    // ========================================================
    // Куратор: баланс компании + прогноз
    // ========================================================
    @GET("wallet/curator/company-balance")
    suspend fun getCompanyBalance(): Response<F3CompanyBalanceDto>

    @PUT("wallet/curator/company-balance")
    suspend fun setCompanyBalanceThreshold(@Body body: F3CompanyBalanceThresholdBody): Response<F3CompanyBalanceDto>

    /** 503 если PAYOUTS_ENABLED off. */
    @POST("wallet/curator/company-balance/sync")
    suspend fun syncCompanyBalance(): Response<F3CompanyBalanceDto>

    @GET("wallet/curator/payouts/forecast")
    suspend fun getPayoutsForecast(
        @Query("year") year: Int,
        @Query("month") month: Int,
    ): Response<F3ForecastDto>

    // ========================================================
    // Монтажник: самоотчёт «за сегодня X п.м.»
    // ========================================================
    @POST("wallet/day-meters")
    suspend fun submitDayMeters(@Body body: F3DayMetersBody): Response<F3EarningDto>
}
