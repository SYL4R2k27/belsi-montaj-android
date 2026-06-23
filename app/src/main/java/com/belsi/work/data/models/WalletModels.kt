package com.belsi.work.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Кошелёк BELSI — DTO под контракт Ф1-бэкенда (app/wallet.py).
 * Ф2 (2026-06-15): полностью переписано на @Serializable + @SerialName (snake_case),
 * String-id вместо UUID, ISO-строки дат, статусы pending/approved/rejected/paid,
 * metadata транзакции (фото→кабинет), куратор/начисление/ставки/команда.
 * Json = ignoreUnknownKeys + coerceInputValues + isLenient (NetworkModule) → дефолты безопасны.
 */

// ============================================================
// МОНТАЖНИК / общий
// ============================================================

/** GET /wallet — WalletOut */
@Serializable
data class WalletDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val balance: Double = 0.0,
    @SerialName("total_earned") val totalEarned: Double = 0.0,
    @SerialName("total_withdrawn") val totalWithdrawn: Double = 0.0,
    @SerialName("pending_amount") val pendingAmount: Double = 0.0,
    @SerialName("available_for_withdraw") val availableForWithdraw: Double? = null,
    val currency: String = "RUB",
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    /** Доступно к выводу. Фолбэк на balance. */
    val available: Double get() = availableForWithdraw ?: balance
}

/** GET /wallet/transactions — {items, page, ...} */
@Serializable
data class WalletTxPage(
    val items: List<WalletTxDto> = emptyList(),
    val page: Int = 1,
    @SerialName("total_pages") val totalPages: Int? = null,
    @SerialName("total_items") val totalItems: Int? = null,
)

@Serializable
data class WalletTxDto(
    val id: String,
    @SerialName("wallet_id") val walletId: String? = null,
    val type: String,   // earned, withdrawn, bonus, penalty, refund
    val amount: Double,
    @SerialName("balance_before") val balanceBefore: Double? = null,
    @SerialName("balance_after") val balanceAfter: Double? = null,
    val description: String? = null,
    @SerialName("shift_id") val shiftId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    @SerialName("created_at") val createdAt: String? = null,
) {
    val typeDisplay: String get() = when (type) {
        "earned" -> "Начислено"; "withdrawn" -> "Выведено"; "bonus" -> "Бонус"
        "penalty" -> "Штраф"; "refund" -> "Возврат"; else -> type
    }
    val isPositive: Boolean get() = type == "earned" || type == "bonus" || type == "refund"

    // фото→деньги (начисление за принятый кабинет)
    val isCabinetAccrual: Boolean get() = type == "earned" && !metadata["cabinet_id"].isNullOrEmpty()
    val cabinetId: String? get() = metadata["cabinet_id"]?.ifEmpty { null }
    val cabinetNumber: String? get() = metadata["cabinet_number"]?.ifEmpty { null }
    val objectName: String? get() = metadata["object_name"]?.ifEmpty { null }
    /** "11.4 п.м." из description (для карточки начисления). */
    val metersFromDescription: String? get() =
        description?.let { Regex("""[0-9]+(?:[.,][0-9]+)?\s*п\.м\.""").find(it)?.value }
}

/** WithdrawalOut. Статусы Ф1: pending → approved → paid (или rejected). */
@Serializable
data class WithdrawalDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val amount: Double,
    val status: String,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("payment_details") val paymentDetails: String? = null,
    val note: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("processed_at") val processedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
) {
    val statusDisplay: String get() = when (status) {
        "pending" -> "Ожидает одобрения"; "approved" -> "Одобрено · в обработке"
        "rejected" -> "Отклонено"; "paid" -> "Выплачено"; else -> status
    }
    val isActiveHold: Boolean get() = status == "pending" || status == "approved"
}

@Serializable
data class WithdrawalPage(
    val items: List<WithdrawalDto> = emptyList(),
    val page: Int = 1,
)

@Serializable
data class PaymentMethodDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val type: String,
    @SerialName("display_name") val displayName: String,
    val details: String? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

// --- request DTOs (монтажник) ---

@Serializable
data class WithdrawBody(
    val amount: Double,
    @SerialName("payment_method_id") val paymentMethodId: String? = null,
    val note: String? = null,
)

@Serializable
data class AddPaymentMethodBody(
    val type: String,
    @SerialName("display_name") val displayName: String,
    val details: String,
    @SerialName("is_default") val isDefault: Boolean = false,
)

@Serializable
data class MetersBody(
    val meters: Double,
    val comment: String? = null,
)

@Serializable
data class MetersProposalDto(
    val id: String,
    @SerialName("cabinet_id") val cabinetId: String,
    @SerialName("user_id") val userId: String,
    val meters: Double,
    val comment: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

// ============================================================
// КУРАТОР: ставки
// ============================================================

@Serializable
data class WalletRatesResponse(
    @SerialName("global_rate") val globalRate: Double = 1000.0,
    val overrides: List<RateOverrideDto> = emptyList(),
)

@Serializable
data class RateOverrideDto(
    @SerialName("user_id") val userId: String,
    @SerialName("user_name") val userName: String,
    @SerialName("user_phone") val userPhone: String? = null,
    val rate: Double,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class GlobalRateBody(@SerialName("global_rate") val globalRate: Double)

@Serializable
data class UserRateBody(val rate: Double? = null)   // null = снять override

// ============================================================
// КУРАТОР: начисление за кабинет
// ============================================================

@Serializable
data class AccrualInfoResponse(
    val cabinet: AccrualCabinetDto,
    val proposals: List<AccrualProposalDto> = emptyList(),
    @SerialName("resolved_meters") val resolvedMeters: Double? = null,
    @SerialName("meters_source") val metersSource: String? = null,
    @SerialName("global_rate") val globalRate: Double = 1000.0,
    val recipients: List<AccrualRecipientDto> = emptyList(),
)

@Serializable
data class AccrualCabinetDto(
    val id: String,
    @SerialName("cabinet_number") val cabinetNumber: String? = null,
    @SerialName("zone_code") val zoneCode: String? = null,
    @SerialName("floor_number") val floorNumber: Int? = null,
    @SerialName("object_id") val objectId: String,
    @SerialName("object_name") val objectName: String? = null,
    val status: String? = null,
    @SerialName("total_length_mm") val totalLengthMm: Int? = null,
)

@Serializable
data class AccrualProposalDto(
    @SerialName("user_id") val userId: String,
    @SerialName("user_name") val userName: String,
    val meters: Double,
    val comment: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class AccrualRecipientDto(
    @SerialName("user_id") val userId: String,
    @SerialName("user_name") val userName: String,
    @SerialName("user_phone") val userPhone: String? = null,
    val rate: Double,
    val amount: Double? = null,
    @SerialName("already_accrued") val alreadyAccrued: Boolean = false,
    @SerialName("last_accrual_at") val lastAccrualAt: String? = null,
)

@Serializable
data class AccrueBody(
    @SerialName("cabinet_id") val cabinetId: String,
    val meters: Double? = null,
    @SerialName("user_ids") val userIds: List<String>? = null,
    val force: Boolean = false,
    val comment: String? = null,
)

@Serializable
data class AccrueResponse(
    @SerialName("cabinet_id") val cabinetId: String,
    @SerialName("cabinet_number") val cabinetNumber: String? = null,
    @SerialName("object_name") val objectName: String? = null,
    val meters: Double = 0.0,
    @SerialName("meters_source") val metersSource: String? = null,
    val force: Boolean = false,
    val accrued: List<AccruedItemDto> = emptyList(),
)

@Serializable
data class AccruedItemDto(
    @SerialName("user_id") val userId: String,
    @SerialName("user_name") val userName: String,
    val rate: Double = 0.0,
    val amount: Double = 0.0,
    @SerialName("transaction_id") val transactionId: String? = null,
)

// ============================================================
// КУРАТОР: очередь выводов
// ============================================================

@Serializable
data class CuratorWithdrawalPage(
    val items: List<CuratorWithdrawalDto> = emptyList(),
    val page: Int = 1,
    @SerialName("total_items") val totalItems: Int? = null,
)

/** CuratorWithdrawalOut = WithdrawalOut + владелец. */
@Serializable
data class CuratorWithdrawalDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val amount: Double,
    val status: String,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("payment_details") val paymentDetails: String? = null,
    val note: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("user_name") val userName: String = "—",
    @SerialName("user_phone") val userPhone: String = "",
    @SerialName("user_balance") val userBalance: Double = 0.0,
) {
    val statusDisplay: String get() = when (status) {
        "pending" -> "Ожидает одобрения"; "approved" -> "Одобрено · в обработке"
        "rejected" -> "Отклонено"; "paid" -> "Выплачено"; else -> status
    }
    val initials: String get() = userName.split(" ").filter { it.isNotBlank() }
        .take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
}

@Serializable
data class RejectBody(val reason: String? = null)

@Serializable
data class AdjustmentBody(
    @SerialName("user_id") val userId: String,
    val type: String,     // bonus | penalty
    val amount: Double,
    val reason: String,
)

// ============================================================
// БРИГАДИР: кошелёк команды (GET /wallet/foreman/team)
// ============================================================

@Serializable
data class ForemanTeamResponse(
    @SerialName("team_earned") val teamEarned: Double = 0.0,
    @SerialName("team_pending") val teamPending: Double = 0.0,
    @SerialName("team_paid") val teamPaid: Double = 0.0,
    @SerialName("cabinets_count") val cabinetsCount: Int = 0,
    val members: List<ForemanTeamMemberDto> = emptyList(),
    val cabinets: List<ForemanTeamCabinetDto> = emptyList(),
)

@Serializable
data class ForemanTeamMemberDto(
    @SerialName("user_id") val userId: String,
    @SerialName("user_name") val userName: String,
    val role: String? = null,
    @SerialName("cabinets_count") val cabinetsCount: Int = 0,
    @SerialName("meters_total") val metersTotal: Double = 0.0,
    val earned: Double = 0.0,
    val pending: Double = 0.0,
    val paid: Double = 0.0,
) {
    val statusLabel: String get() = when {
        pending > 0 -> "на одобрении"
        paid > 0 && paid >= earned - 0.01 -> "выплачено"
        else -> "начислено"
    }
    val initials: String get() = userName.split(" ").filter { it.isNotBlank() }
        .take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
}

@Serializable
data class ForemanTeamCabinetDto(
    @SerialName("cabinet_id") val cabinetId: String,
    @SerialName("cabinet_number") val cabinetNumber: String? = null,
    @SerialName("object_name") val objectName: String? = null,
    val meters: Double = 0.0,
    @SerialName("recipients_count") val recipientsCount: Int = 0,
    val total: Double = 0.0,
)

// ============================================================
// Ф2.5 — НПД/ИНН-профиль (wallet_tax_profiles, GET/PUT /wallet/tax-profile)
// ============================================================

/**
 * TaxProfileOut. `status`/`is_payout_ready` ВЫЧИСЛЯЮТСЯ бэком (не колонки).
 * contract_type: self_employed (кошелёк/выплаты) | employed (выплаты мимо приложения).
 */
@Serializable
data class TaxProfileDto(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("contract_type") val contractType: String = "self_employed",
    val inn: String? = null,
    @SerialName("npd_certificate_number") val npdCertificateNumber: String? = null,
    @SerialName("npd_certificate_date") val npdCertificateDate: String? = null,   // ISO date YYYY-MM-DD
    @SerialName("npd_certificate_file_url") val npdCertificateFileUrl: String? = null,
    @SerialName("verified_at") val verifiedAt: String? = null,
    val status: String = "incomplete",          // computed: complete | incomplete
    @SerialName("is_payout_ready") val isPayoutReady: Boolean = false,
    // Ф2.5 — приём Договора самозанятого (НПД). Все вычисляются бэком.
    @SerialName("contract_required") val contractRequired: Boolean = false,   // true ⟺ self_employed
    @SerialName("contract_accepted") val contractAccepted: Boolean = false,   // accepted_at && актуальная редакция
    @SerialName("contract_revision") val contractRevision: String? = null,
    @SerialName("contract_accepted_at") val contractAcceptedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val isEmployed: Boolean get() = contractType == "employed"
    val isSelfEmployed: Boolean get() = contractType == "self_employed"
    val isComplete: Boolean get() = status == "complete"
    val hasCertificateFile: Boolean get() = !npdCertificateFileUrl.isNullOrBlank()
    /** Самозанятый ещё не принял Договор → выплата заблокирована. */
    val needsContract: Boolean get() = contractRequired && !contractAccepted
}

/** Тело PUT /wallet/tax-profile (все поля опциональны — upsert). */
@Serializable
data class TaxProfileBody(
    @SerialName("contract_type") val contractType: String? = null,
    val inn: String? = null,
    @SerialName("npd_certificate_number") val npdCertificateNumber: String? = null,
    @SerialName("npd_certificate_date") val npdCertificateDate: String? = null,   // ISO date YYYY-MM-DD
    @SerialName("contract_accepted") val contractAccepted: Boolean? = null,
)

/** Ответ POST /wallet/tax-profile/certificate. */
@Serializable
data class CertificateUploadResponse(
    @SerialName("npd_certificate_file_url") val npdCertificateFileUrl: String? = null,
)

// ============================================================
// Ф2.5 — Месячный Акт (wallet_acts)
// ============================================================

/** ActItemOut — позиция Акта = одно начисление за принятый кабинет. */
@Serializable
data class ActItemDto(
    @SerialName("accrual_id") val accrualId: String? = null,
    @SerialName("cabinet_id") val cabinetId: String? = null,
    @SerialName("cabinet_number") val cabinetNumber: String? = null,
    @SerialName("object_name") val objectName: String? = null,
    val meters: String? = null,        // строкой как Ф1-деньги ("12.50")
    val amount: String? = null,        // строкой ("12500.00")
    @SerialName("accepted_at") val acceptedAt: String? = null,
) {
    val metersDouble: Double get() = meters?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
    val amountDouble: Double get() = amount?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
}

/** ActOut — месячный Акт об оказании услуг (гибрид-касса). */
@Serializable
data class WalletActDto(
    val id: String? = null,            // null = «виртуальный» черновик-кандидат (ещё не собран)
    @SerialName("user_id") val userId: String? = null,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("period_year") val periodYear: Int = 0,
    @SerialName("period_month") val periodMonth: Int = 0,
    @SerialName("period_label") val periodLabel: String? = null,
    @SerialName("total_amount") val totalAmount: String = "0",
    val currency: String = "RUB",
    val status: String = "draft",      // draft | signed | paid
    val items: List<ActItemDto> = emptyList(),
    @SerialName("items_count") val itemsCount: Int = 0,
    @SerialName("signed_at") val signedAt: String? = null,
    @SerialName("signed_by") val signedBy: String? = null,
    @SerialName("signed_by_name") val signedByName: String? = null,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("document_url") val documentUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val totalDouble: Double get() = totalAmount.replace(",", ".").toDoubleOrNull() ?: 0.0
    /** Виртуальный кандидат — у юзера есть одобренные начисления, но реального Акта ещё нет. */
    val isVirtualDraft: Boolean get() = id.isNullOrBlank()
    val statusDisplay: String get() = when (status) {
        "draft" -> "Черновик"; "signed" -> "Подписан"; "paid" -> "Выплачен"; else -> status
    }
    val label: String get() = periodLabel ?: run {
        val m = RU_MONTHS.getOrNull(periodMonth - 1) ?: periodMonth.toString()
        "$m $periodYear"
    }
    val initials: String get() = (userName ?: "").split(" ").filter { it.isNotBlank() }
        .take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }

    companion object {
        val RU_MONTHS = listOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
        )
    }
}

/** Обёртка списка актов ({items: [...]}). */
@Serializable
data class WalletActsPage(
    val items: List<WalletActDto> = emptyList(),
)

/** Тело POST /wallet/curator/acts/assemble. */
@Serializable
data class AssembleActBody(
    @SerialName("user_id") val userId: String,
    val year: Int,
    val month: Int,
)
