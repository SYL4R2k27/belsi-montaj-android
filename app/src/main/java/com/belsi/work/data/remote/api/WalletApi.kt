package com.belsi.work.data.remote.api

import com.belsi.work.data.models.PaymentMethod
import com.belsi.work.data.models.Transaction
import com.belsi.work.data.models.Wallet
import com.belsi.work.data.models.WithdrawalRequest
import retrofit2.Response
import retrofit2.http.*
import java.util.UUID

interface WalletApi {
    
    @GET("wallet")
    suspend fun getWallet(): Response<Wallet>
    
    @GET("wallet/transactions")
    suspend fun getTransactions(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<TransactionsResponse>
    
    @GET("wallet/transactions/{transactionId}")
    suspend fun getTransaction(
        @Path("transactionId") transactionId: UUID
    ): Response<Transaction>
    
    @POST("wallet/withdraw")
    suspend fun requestWithdrawal(
        @Body request: WithdrawRequest
    ): Response<WithdrawalRequest>
    
    @GET("wallet/withdrawals")
    suspend fun getWithdrawals(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<WithdrawalsResponse>
    
    @GET("wallet/withdrawals/{withdrawalId}")
    suspend fun getWithdrawal(
        @Path("withdrawalId") withdrawalId: UUID
    ): Response<WithdrawalRequest>
    
    @POST("wallet/payment-methods")
    suspend fun addPaymentMethod(
        @Body request: AddPaymentMethodRequest
    ): Response<PaymentMethod>
    
    @GET("wallet/payment-methods")
    suspend fun getPaymentMethods(): Response<List<PaymentMethod>>
    
    @DELETE("wallet/payment-methods/{methodId}")
    suspend fun deletePaymentMethod(
        @Path("methodId") methodId: UUID
    ): Response<Unit>
    
    @PUT("wallet/payment-methods/{methodId}/default")
    suspend fun setDefaultPaymentMethod(
        @Path("methodId") methodId: UUID
    ): Response<PaymentMethod>
}

// Request/Response DTOs
data class TransactionsResponse(
    val transactions: List<Transaction>,
    val page: Int,
    val totalPages: Int,
    val totalTransactions: Int
)

data class WithdrawRequest(
    val amount: Double,
    val paymentMethodId: UUID,
    val note: String? = null
)

data class WithdrawalsResponse(
    val withdrawals: List<WithdrawalRequest>,
    val page: Int,
    val totalPages: Int,
    val totalWithdrawals: Int
)

data class AddPaymentMethodRequest(
    val type: String,
    val displayName: String,
    val details: String,
    val isDefault: Boolean = false
)
