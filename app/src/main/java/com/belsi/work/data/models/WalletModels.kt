package com.belsi.work.data.models

import java.util.UUID

enum class TransactionType {
    EARNED,         // Начислено за смену
    WITHDRAWN,      // Выведено
    BONUS,          // Бонус
    PENALTY,        // Штраф
    REFUND;         // Возврат
    
    val displayName: String
        get() = when (this) {
            EARNED -> "Начислено"
            WITHDRAWN -> "Выведено"
            BONUS -> "Бонус"
            PENALTY -> "Штраф"
            REFUND -> "Возврат"
        }
}

enum class WithdrawalStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED;
    
    val displayName: String
        get() = when (this) {
            PENDING -> "Ожидает"
            PROCESSING -> "Обработка"
            COMPLETED -> "Завершено"
            FAILED -> "Ошибка"
            CANCELLED -> "Отменено"
        }
}

data class Wallet(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    var balance: Double = 0.0,
    val totalEarned: Double = 0.0,
    val totalWithdrawn: Double = 0.0,
    val pendingAmount: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
)

data class Transaction(
    val id: UUID = UUID.randomUUID(),
    val walletId: UUID,
    val type: TransactionType,
    val amount: Double,
    val balanceBefore: Double,
    val balanceAfter: Double,
    val description: String,
    val createdAt: Long = System.currentTimeMillis(),
    val shiftId: UUID? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class WithdrawalRequest(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val amount: Double,
    val status: WithdrawalStatus = WithdrawalStatus.PENDING,
    val paymentMethod: String,
    val paymentDetails: String,
    val createdAt: Long = System.currentTimeMillis(),
    val processedAt: Long? = null,
    val completedAt: Long? = null,
    val note: String? = null,
    val errorMessage: String? = null
)

data class PaymentMethod(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val type: String, // "card", "bank_account", "phone"
    val displayName: String,
    val details: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
