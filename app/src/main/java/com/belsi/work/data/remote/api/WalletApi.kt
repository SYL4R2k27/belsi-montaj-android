package com.belsi.work.data.remote.api

import com.belsi.work.data.models.AccrualInfoResponse
import com.belsi.work.data.models.AccrueBody
import com.belsi.work.data.models.AccrueResponse
import com.belsi.work.data.models.AddPaymentMethodBody
import com.belsi.work.data.models.AdjustmentBody
import com.belsi.work.data.models.AssembleActBody
import com.belsi.work.data.models.CertificateUploadResponse
import com.belsi.work.data.models.CuratorWithdrawalPage
import com.belsi.work.data.models.ForemanTeamResponse
import com.belsi.work.data.models.GlobalRateBody
import com.belsi.work.data.models.MetersBody
import com.belsi.work.data.models.MetersProposalDto
import com.belsi.work.data.models.PaymentMethodDto
import com.belsi.work.data.models.RejectBody
import com.belsi.work.data.models.TaxProfileBody
import com.belsi.work.data.models.TaxProfileDto
import com.belsi.work.data.models.UserRateBody
import com.belsi.work.data.models.WalletActDto
import com.belsi.work.data.models.WalletActsPage
import com.belsi.work.data.models.WalletDto
import com.belsi.work.data.models.WalletRatesResponse
import com.belsi.work.data.models.WalletTxDto
import com.belsi.work.data.models.WalletTxPage
import com.belsi.work.data.models.WithdrawBody
import com.belsi.work.data.models.WithdrawalDto
import com.belsi.work.data.models.WithdrawalPage
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** Кошелёк BELSI — контракт Ф1 (app/wallet.py). Ф2: String-id, items-обёртки, куратор-домен. */
interface WalletApi {

    // --- Монтажник / общий ---
    @GET("wallet")
    suspend fun getWallet(): Response<WalletDto>

    @GET("wallet/transactions")
    suspend fun getTransactions(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
    ): Response<WalletTxPage>

    @GET("wallet/transactions/{id}")
    suspend fun getTransaction(@Path("id") id: String): Response<WalletTxDto>

    @POST("wallet/withdraw")
    suspend fun requestWithdrawal(@Body body: WithdrawBody): Response<WithdrawalDto>

    @GET("wallet/withdrawals")
    suspend fun getWithdrawals(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
    ): Response<WithdrawalPage>

    @GET("wallet/withdrawals/{id}")
    suspend fun getWithdrawal(@Path("id") id: String): Response<WithdrawalDto>

    @POST("wallet/payment-methods")
    suspend fun addPaymentMethod(@Body body: AddPaymentMethodBody): Response<PaymentMethodDto>

    @GET("wallet/payment-methods")
    suspend fun getPaymentMethods(): Response<List<PaymentMethodDto>>

    @DELETE("wallet/payment-methods/{id}")
    suspend fun deletePaymentMethod(@Path("id") id: String): Response<Unit>

    @PUT("wallet/payment-methods/{id}/default")
    suspend fun setDefaultPaymentMethod(@Path("id") id: String): Response<PaymentMethodDto>

    /** Монтажник предлагает свои п.м. по кабинету. */
    @POST("wallet/cabinets/{cabinetId}/meters")
    suspend fun proposeMeters(
        @Path("cabinetId") cabinetId: String,
        @Body body: MetersBody,
    ): Response<MetersProposalDto>

    // --- Куратор: ставки ---
    @GET("wallet/curator/rates")
    suspend fun getRates(): Response<WalletRatesResponse>

    @PUT("wallet/curator/rates")
    suspend fun setGlobalRate(@Body body: GlobalRateBody): Response<Unit>

    @PUT("wallet/curator/rates/{userId}")
    suspend fun setUserRate(@Path("userId") userId: String, @Body body: UserRateBody): Response<Unit>

    // --- Куратор: начисление ---
    @GET("wallet/curator/cabinets/{cabinetId}/accrual-info")
    suspend fun getAccrualInfo(@Path("cabinetId") cabinetId: String): Response<AccrualInfoResponse>

    @POST("wallet/curator/accruals")
    suspend fun createAccruals(@Body body: AccrueBody): Response<AccrueResponse>

    // --- Куратор: очередь выводов ---
    @GET("wallet/curator/withdrawals")
    suspend fun getCuratorWithdrawals(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100,
    ): Response<CuratorWithdrawalPage>

    @POST("wallet/curator/withdrawals/{id}/approve")
    suspend fun approveWithdrawal(@Path("id") id: String): Response<WithdrawalDto>

    @POST("wallet/curator/withdrawals/{id}/reject")
    suspend fun rejectWithdrawal(@Path("id") id: String, @Body body: RejectBody): Response<WithdrawalDto>

    @POST("wallet/curator/withdrawals/{id}/paid")
    suspend fun markPaid(@Path("id") id: String): Response<WithdrawalDto>

    // --- Куратор: штраф / бонус ---
    @POST("wallet/curator/adjustments")
    suspend fun createAdjustment(@Body body: AdjustmentBody): Response<Unit>

    // --- Бригадир: кошелёк команды (аддитивный Ф2-эндпоинт) ---
    @GET("wallet/foreman/team")
    suspend fun getForemanTeam(): Response<ForemanTeamResponse>

    // ========================================================
    // Ф2.5 — НПД/ИНН-профиль (монтажник, свои данные)
    // ========================================================
    @GET("wallet/tax-profile")
    suspend fun getTaxProfile(): Response<TaxProfileDto>

    @PUT("wallet/tax-profile")
    suspend fun updateTaxProfile(@Body body: TaxProfileBody): Response<TaxProfileDto>

    /** Загрузка файла справки НПД (multipart, pdf/jpg/png). */
    @Multipart
    @POST("wallet/tax-profile/certificate")
    suspend fun uploadCertificate(@Part file: MultipartBody.Part): Response<CertificateUploadResponse>

    // ========================================================
    // Ф2.5 — Месячные Акты (монтажник)
    // ========================================================
    @GET("wallet/acts")
    suspend fun getActs(
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null,
    ): Response<WalletActsPage>

    @GET("wallet/acts/{id}")
    suspend fun getAct(@Path("id") id: String): Response<WalletActDto>

    @Streaming
    @GET("wallet/acts/{id}/document")
    suspend fun getActDocument(
        @Path("id") id: String,
        @Query("format") format: String = "docx",
    ): Response<ResponseBody>

    // ========================================================
    // Ф2.5 — Месячные Акты (куратор)
    // ========================================================
    @GET("wallet/curator/acts")
    suspend fun getCuratorActs(
        @Query("year") year: Int,
        @Query("month") month: Int,
    ): Response<WalletActsPage>

    @POST("wallet/curator/acts/assemble")
    suspend fun assembleAct(@Body body: AssembleActBody): Response<WalletActDto>

    @POST("wallet/curator/acts/{id}/sign")
    suspend fun signAct(@Path("id") id: String): Response<WalletActDto>

    @POST("wallet/curator/acts/{id}/mark-paid")
    suspend fun markActPaid(@Path("id") id: String): Response<WalletActDto>

    @Streaming
    @GET("wallet/curator/acts/{id}/document")
    suspend fun getCuratorActDocument(
        @Path("id") id: String,
        @Query("format") format: String = "docx",
    ): Response<ResponseBody>
}
