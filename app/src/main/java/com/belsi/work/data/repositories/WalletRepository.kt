package com.belsi.work.data.repositories

import com.belsi.work.data.remote.api.WalletApi
import com.belsi.work.presentation.screens.wallet.Transaction
import com.belsi.work.presentation.screens.wallet.TransactionStatus
import com.belsi.work.presentation.screens.wallet.TransactionType
import com.belsi.work.presentation.screens.wallet.WalletState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий для работы с кошельком
 */
interface WalletRepository {
    suspend fun getWalletData(): Result<WalletState>
    suspend fun requestWithdraw(amount: Double, cardNumber: String): Result<Unit>
}

@Singleton
class WalletRepositoryImpl @Inject constructor(
    private val walletApi: WalletApi
) : WalletRepository {

    override suspend fun getWalletData(): Result<WalletState> {
        return try {
            // Получаем данные кошелька
            val walletResponse = walletApi.getWallet()

            if (!walletResponse.isSuccessful || walletResponse.body() == null) {
                return Result.failure(Exception(parseErrorMessage(walletResponse.code())))
            }

            val wallet = walletResponse.body()!!

            // Получаем транзакции
            val transactionsResponse = walletApi.getTransactions(page = 1, limit = 50)

            val transactions = if (transactionsResponse.isSuccessful && transactionsResponse.body() != null) {
                transactionsResponse.body()!!.transactions.map { dto ->
                    com.belsi.work.presentation.screens.wallet.Transaction(
                        id = dto.id.toString(),
                        type = mapTransactionType(dto.type),
                        amount = dto.amount,
                        date = dto.createdAt,
                        description = dto.description,
                        status = com.belsi.work.presentation.screens.wallet.TransactionStatus.COMPLETED
                    )
                }
            } else {
                emptyList()
            }

            val walletState = WalletState(
                balance = wallet.balance,
                pendingAmount = wallet.pendingAmount ?: 0.0,
                totalEarned = wallet.totalEarned ?: 0.0,
                transactions = transactions
            )

            Result.success(walletState)
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка загрузки кошелька: ${e.message}", e))
        }
    }

    override suspend fun requestWithdraw(amount: Double, cardNumber: String): Result<Unit> {
        return try {
            // For now, using a temporary UUID for payment method
            // In production, you'd first create a payment method or select an existing one
            val request = com.belsi.work.data.remote.api.WithdrawRequest(
                amount = amount,
                paymentMethodId = java.util.UUID.randomUUID(),
                note = "Card: $cardNumber"
            )

            val response = walletApi.requestWithdrawal(request)

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка запроса на вывод: ${e.message}", e))
        }
    }

    private fun mapTransactionType(type: com.belsi.work.data.models.TransactionType): com.belsi.work.presentation.screens.wallet.TransactionType {
        return when (type) {
            com.belsi.work.data.models.TransactionType.EARNED -> com.belsi.work.presentation.screens.wallet.TransactionType.INCOME
            com.belsi.work.data.models.TransactionType.WITHDRAWN -> com.belsi.work.presentation.screens.wallet.TransactionType.WITHDRAWAL
            else -> com.belsi.work.presentation.screens.wallet.TransactionType.INCOME
        }
    }

    private fun parseErrorMessage(code: Int): String {
        return when (code) {
            400 -> "Неверный формат данных"
            401 -> "Требуется авторизация"
            403 -> "Доступ запрещен"
            404 -> "Ресурс не найден"
            422 -> "Недостаточно средств или неверные данные"
            500, 502, 503 -> "Ошибка сервера, попробуйте позже"
            else -> "Неизвестная ошибка"
        }
    }
}
