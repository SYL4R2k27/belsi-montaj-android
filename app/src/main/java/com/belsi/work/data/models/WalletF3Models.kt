package com.belsi.work.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Кошелёк BELSI — Ф3 DTO под контракт app/wallet_f3.py.
 * Расчёт по часам/метрам-на-день + штраф/бонус периода + выплаты Rocket Work
 * (executor / сделка / чек ФНС) + баланс компании + прогноз.
 *
 * Конвенции (как Ф2 WalletModels): @Serializable + @SerialName(snake_case),
 * String-id (не UUID), Double-деньги-в-рублях, ISO/date-строки, дефолты безопасны
 * (Json = ignoreUnknownKeys + coerceInputValues + isLenient), list-ответы в обёртке {items}.
 *
 * Контракт (_earning_to_out / _payout_to_out / get_* в wallet_f3.py):
 *  EARNING basis = hours|meters ; status = draft|approved|void ; source = photo|cabinet|manual
 *  PAYOUT  kind  = monthly|on_demand ; status = draft|approved|sent|paying|paid|failed|canceled
 *  ADJ     kind  = bonus|penalty
 */

// ============================================================
// EARNING (дневное начисление)
// ============================================================

/** _earning_to_out — позиция начисления за день. */
@Serializable
data class F3EarningDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("work_date") val workDate: String? = null,    // ISO date YYYY-MM-DD
    val basis: String = "hours",                              // hours | meters
    @SerialName("object_id") val objectId: String? = null,
    @SerialName("cabinet_id") val cabinetId: String? = null,
    val hours: Double? = null,
    val meters: Double? = null,
    val rate: Double? = null,
    val amount: Double? = null,
    val status: String = "draft",                             // draft | approved | void
    val source: String = "manual",                            // photo | cabinet | manual
    @SerialName("payout_id") val payoutId: String? = null,
    val comment: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val isHours: Boolean get() = basis == "hours"
    val isMeters: Boolean get() = basis == "meters"
    val basisLabel: String get() = if (basis == "hours") "по часам" else "по метрам"
    val statusDisplay: String get() = when (status) {
        "draft" -> "Черновик"; "approved" -> "Одобрено"; "void" -> "Отменено"; else -> status
    }
    val sourceLabel: String get() = when (source) {
        "photo" -> "по фотоотчёту"; "cabinet" -> "приёмка кабинета"; "manual" -> "вручную"; else -> source
    }
    val amountValue: Double get() = amount ?: 0.0
    /** Краткая величина «11.5 ч» / «12.3 п.м.» для строки. */
    val qtyLabel: String get() = if (isHours) "${numShort(hours ?: 0.0)} ч" else "${numShort(meters ?: 0.0)} п.м."
    val isInPayout: Boolean get() = !payoutId.isNullOrBlank()

    private fun numShort(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
}

/** GET /wallet/curator/earnings → {items:[EARNING]} */
@Serializable
data class F3EarningsPage(
    val items: List<F3EarningDto> = emptyList(),
)

/** POST /wallet/curator/earnings/recompute → итог пересчёта. */
@Serializable
data class F3RecomputeResponse(
    @SerialName("user_id") val userId: String? = null,
    val year: Int = 0,
    val month: Int = 0,
    @SerialName("days_scanned") val daysScanned: Int = 0,
    val earnings: List<F3EarningDto> = emptyList(),
    @SerialName("unclassified_days") val unclassifiedDays: List<String> = emptyList(),
)

// --- request bodies (earnings) ---

@Serializable
data class F3HourRateBody(val rate: Double)

/** PUT /wallet/curator/hour-rates/{userId} — rate=null снимает override. */
@Serializable
data class F3UserHourRateBody(val rate: Double? = null)

@Serializable
data class F3DayFormatBody(
    @SerialName("user_id") val userId: String,
    @SerialName("work_date") val workDate: String,   // YYYY-MM-DD
    val basis: String,                               // hours | meters
)

@Serializable
data class F3RecomputeBody(
    @SerialName("user_id") val userId: String,
    val year: Int,
    val month: Int,
)

/** PUT /wallet/curator/earnings/{id} — все поля опциональны. */
@Serializable
data class F3EarningEditBody(
    val basis: String? = null,
    val hours: Double? = null,
    val meters: Double? = null,
    val rate: Double? = null,
    val amount: Double? = null,
    val comment: String? = null,
)

// ============================================================
// HOUR RATES (часовые ставки)
// ============================================================

/** GET /wallet/curator/hour-rates */
@Serializable
data class F3HourRatesResponse(
    @SerialName("global_hour_rate") val globalHourRate: Double? = null,
    val overrides: List<F3HourRateOverrideDto> = emptyList(),
)

@Serializable
data class F3HourRateOverrideDto(
    @SerialName("user_id") val userId: String,
    @SerialName("user_name") val userName: String = "—",
    @SerialName("user_phone") val userPhone: String? = null,
    val rate: Double = 0.0,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** Ответы PUT hour-rates (глобально / по юзеру). */
@Serializable
data class F3GlobalHourRateResponse(
    @SerialName("global_hour_rate") val globalHourRate: Double? = null,
)

@Serializable
data class F3UserHourRateResponse(
    @SerialName("user_id") val userId: String? = null,
    val rate: Double? = null,
)

@Serializable
data class F3DayFormatResponse(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("work_date") val workDate: String? = null,
    val basis: String = "hours",
)

// ============================================================
// METERS — приёмка кабинета + самоотчёт монтажника
// ============================================================

/** POST /wallet/curator/cabinets/{cabinetId}/accept-meters body */
@Serializable
data class F3AcceptMetersBody(
    val meters: Double? = null,
    @SerialName("user_ids") val userIds: List<String>? = null,
    @SerialName("work_date") val workDate: String? = null,    // YYYY-MM-DD
    val comment: String? = null,
    @SerialName("mark_done") val markDone: Boolean = true,
)

/** Ответ accept-meters. */
@Serializable
data class F3AcceptMetersResponse(
    @SerialName("cabinet_id") val cabinetId: String? = null,
    @SerialName("cabinet_number") val cabinetNumber: String? = null,
    val meters: Double = 0.0,
    @SerialName("work_date") val workDate: String? = null,
    val earnings: List<F3EarningDto> = emptyList(),
)

/** POST /wallet/day-meters (монтажник «за сегодня X п.м.»). */
@Serializable
data class F3DayMetersBody(
    @SerialName("work_date") val workDate: String,   // YYYY-MM-DD
    val meters: Double,
    @SerialName("object_id") val objectId: String? = null,
    @SerialName("cabinet_id") val cabinetId: String? = null,
    val comment: String? = null,
)

// ============================================================
// ADJUSTMENTS — штраф / бонус (мультивыбор)
// ============================================================

/** POST /wallet/curator/adjustments/batch body */
@Serializable
data class F3AdjustmentBatchBody(
    @SerialName("user_ids") val userIds: List<String>,
    val kind: String,            // bonus | penalty
    val amount: Double,
    val reason: String,
    val year: Int,
    val month: Int,
)

@Serializable
data class F3AdjustmentBatchResponse(
    @SerialName("batch_id") val batchId: String? = null,
    val kind: String = "bonus",
    val amount: Double = 0.0,
    @SerialName("user_ids") val userIds: List<String> = emptyList(),
    val year: Int = 0,
    val month: Int = 0,
)

// ============================================================
// PAYOUTS (выплаты — единица сделки/чека Rocket Work)
// ============================================================

/** _payout_to_out — выплата. */
@Serializable
data class F3PayoutDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val kind: String = "monthly",                             // monthly | on_demand
    @SerialName("act_id") val actId: String? = null,
    @SerialName("period_year") val periodYear: Int? = null,
    @SerialName("period_month") val periodMonth: Int? = null,
    @SerialName("work_amount") val workAmount: Double? = null,
    @SerialName("bonus_amount") val bonusAmount: Double? = null,
    @SerialName("penalty_amount") val penaltyAmount: Double? = null,
    @SerialName("gross_amount") val grossAmount: Double? = null,
    @SerialName("tax_amount") val taxAmount: Double? = null,
    @SerialName("commission_amount") val commissionAmount: Double? = null,
    @SerialName("net_amount") val netAmount: Double? = null,
    val status: String = "draft",
    @SerialName("rw_task_id") val rwTaskId: String? = null,
    @SerialName("rw_status") val rwStatus: String? = null,
    @SerialName("receipt_fns_url") val receiptFnsUrl: String? = null,
    @SerialName("receipt_income_url") val receiptIncomeUrl: String? = null,
    @SerialName("approved_at") val approvedAt: String? = null,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    // Доп. поля приходят в assemble / get-detail (GET {id}):
    val gates: List<String> = emptyList(),
    val earnings: List<F3EarningDto> = emptyList(),
    val adjustments: List<F3PayoutAdjustmentDto> = emptyList(),
) {
    val work: Double get() = workAmount ?: 0.0
    val bonus: Double get() = bonusAmount ?: 0.0
    val penalty: Double get() = penaltyAmount ?: 0.0
    val gross: Double get() = grossAmount ?: 0.0
    val tax: Double get() = taxAmount ?: 0.0
    val commission: Double get() = commissionAmount ?: 0.0
    /** Net на карту: бэк net_amount (вебхук RW) или gross − tax − commission. */
    val net: Double get() = netAmount ?: (gross - tax - commission)

    val kindLabel: String get() = when (kind) {
        "monthly" -> "Месячная"; "on_demand" -> "По запросу"; else -> kind
    }
    val statusDisplay: String get() = when (status) {
        "draft" -> "Черновик"
        "approved" -> "Одобрено · ждёт отправки"
        "sent" -> "Отправлено в Rocket Work"
        "paying" -> "Идёт выплата"
        "paid" -> "Выплачено"
        "failed" -> "Ошибка выплаты"
        "canceled" -> "Отменено"
        else -> status
    }
    val gatesOk: Boolean get() = gates.isEmpty()
    val canApprove: Boolean get() = status == "draft" && gatesOk
    val canSend: Boolean get() = status == "approved"
    val canMarkPaid: Boolean get() = status in listOf("approved", "sent", "paying") && rwTaskId.isNullOrBlank()
    val canCancel: Boolean get() = status in listOf("draft", "approved", "failed") && rwTaskId.isNullOrBlank()
    val isTerminal: Boolean get() = status == "paid" || status == "canceled"
    val hasReceipt: Boolean get() = !receiptFnsUrl.isNullOrBlank() || !receiptIncomeUrl.isNullOrBlank()

    val period: String? get() = if (periodYear != null && periodMonth != null) {
        val m = RU_MONTHS.getOrNull(periodMonth - 1) ?: periodMonth.toString()
        "$m $periodYear"
    } else null

    companion object {
        val RU_MONTHS = listOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
        )
    }
}

/** Корректировка, привязанная к выплате (в get-detail). */
@Serializable
data class F3PayoutAdjustmentDto(
    val id: String? = null,
    val kind: String = "bonus",       // bonus | penalty
    val amount: Double = 0.0,
    val reason: String? = null,
) {
    val isBonus: Boolean get() = kind == "bonus"
    val kindLabel: String get() = if (isBonus) "Бонус" else "Штраф"
}

/** GET /wallet/curator/payouts → {items:[PAYOUT]} */
@Serializable
data class F3PayoutsPage(
    val items: List<F3PayoutDto> = emptyList(),
)

@Serializable
data class F3AssemblePayoutBody(
    @SerialName("user_id") val userId: String,
    val kind: String = "monthly",                 // monthly | on_demand
    val year: Int? = null,
    val month: Int? = null,
)

// ============================================================
// COMPANY BALANCE + FORECAST
// ============================================================

/** GET/PUT/sync /wallet/curator/company-balance */
@Serializable
data class F3CompanyBalanceDto(
    val balance: Double = 0.0,
    val currency: String = "RUB",
    @SerialName("low_threshold") val lowThreshold: Double = 0.0,
    @SerialName("is_low") val isLow: Boolean = false,
    val requisites: Map<String, String> = emptyMap(),
    @SerialName("synced_at") val syncedAt: String? = null,
    @SerialName("payouts_enabled") val payoutsEnabled: Boolean = false,
)

@Serializable
data class F3CompanyBalanceThresholdBody(
    @SerialName("low_threshold") val lowThreshold: Double,
)

/** GET /wallet/curator/payouts/forecast */
@Serializable
data class F3ForecastDto(
    val year: Int = 0,
    val month: Int = 0,
    val needed: Double = 0.0,
    val balance: Double = 0.0,
    val shortfall: Double = 0.0,
    val sufficient: Boolean = false,
    val requisites: Map<String, String> = emptyMap(),
)
