package com.belsi.work.data.remote.api

import com.belsi.work.data.models.ShiftReport
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.UUID

/**
 * API для работы с отчетами
 */
interface ReportApi {

    /**
     * Получить отчет по сменам за период
     *
     * @param startDate Начало периода (ISO date: 2026-01-01)
     * @param endDate Конец периода (ISO date: 2026-01-31)
     * @param userId Фильтр по конкретному пользователю (опционально)
     * @param foremanId Фильтр по бригадиру (опционально)
     * @param curatorId Фильтр по куратору (опционально)
     * @param status Фильтр по статусу смены: "active", "completed", "cancelled" (опционально)
     */
    @GET("reports/shifts")
    suspend fun getShiftReport(
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("user_id") userId: UUID? = null,
        @Query("foreman_id") foremanId: UUID? = null,
        @Query("curator_id") curatorId: UUID? = null,
        @Query("status") status: String? = null
    ): Response<ShiftReport>

    /**
     * Получить отчет по всем сотрудникам бригадира
     */
    @GET("reports/foreman/shifts")
    suspend fun getForemanShiftReport(
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String
    ): Response<ShiftReport>

    /**
     * Получить отчет по всем сотрудникам куратора
     */
    @GET("reports/curator/shifts")
    suspend fun getCuratorShiftReport(
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("foreman_id") foremanId: UUID? = null
    ): Response<ShiftReport>
}
