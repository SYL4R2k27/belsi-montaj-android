package com.belsi.work.data.repositories

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
import com.belsi.work.data.models.F3ForecastDto
import com.belsi.work.data.models.F3GlobalHourRateResponse
import com.belsi.work.data.models.F3HourRateBody
import com.belsi.work.data.models.F3HourRatesResponse
import com.belsi.work.data.models.F3PayoutDto
import com.belsi.work.data.models.F3RecomputeBody
import com.belsi.work.data.models.F3RecomputeResponse
import com.belsi.work.data.models.F3UserHourRateBody
import com.belsi.work.data.remote.api.WalletF3Api
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Кошелёк BELSI — репозиторий Ф3 (расчёт по часам/метрам + штраф/бонус + выплаты Rocket Work).
 * Тонкие safeCall-обёртки + парсинг FastAPI-detail (строка / {code,message} / массив /
 * {code:"gates_failed", missing:[...]}). 503 от send/sync → дружелюбный Result.failure.
 */
interface WalletF3Repository {
    // ставки (часы)
    suspend fun getHourRates(): Result<F3HourRatesResponse>
    suspend fun setGlobalHourRate(rate: Double): Result<F3GlobalHourRateResponse>
    suspend fun setUserHourRate(userId: String, rate: Double?): Result<Unit>
    // формат дня + расчёт начислений
    suspend fun setDayFormat(userId: String, workDate: String, basis: String): Result<F3DayFormatResponse>
    suspend fun recomputeEarnings(userId: String, year: Int, month: Int): Result<F3RecomputeResponse>
    suspend fun getEarnings(userId: String, year: Int, month: Int): Result<List<F3EarningDto>>
    suspend fun editEarning(
        id: String, basis: String? = null, hours: Double? = null, meters: Double? = null,
        rate: Double? = null, amount: Double? = null, comment: String? = null,
    ): Result<F3EarningDto>
    suspend fun approveEarning(id: String): Result<F3EarningDto>
    suspend fun voidEarning(id: String): Result<F3EarningDto>
    // метры
    suspend fun acceptCabinetMeters(
        cabinetId: String, meters: Double? = null, userIds: List<String>? = null,
        workDate: String? = null, comment: String? = null, markDone: Boolean = true,
    ): Result<F3AcceptMetersResponse>
    suspend fun submitDayMeters(
        workDate: String, meters: Double, objectId: String? = null,
        cabinetId: String? = null, comment: String? = null,
    ): Result<F3EarningDto>
    // штраф / бонус
    suspend fun createAdjustmentBatch(
        userIds: List<String>, kind: String, amount: Double, reason: String, year: Int, month: Int,
    ): Result<F3AdjustmentBatchResponse>
    // выплаты
    suspend fun assemblePayout(userId: String, kind: String, year: Int?, month: Int?): Result<F3PayoutDto>
    suspend fun getPayouts(userId: String? = null, status: String? = null): Result<List<F3PayoutDto>>
    suspend fun getPayout(id: String): Result<F3PayoutDto>
    suspend fun approvePayout(id: String): Result<F3PayoutDto>
    suspend fun sendPayout(id: String): Result<F3PayoutDto>
    suspend fun markPayoutPaid(id: String): Result<F3PayoutDto>
    suspend fun cancelPayout(id: String): Result<F3PayoutDto>
    // баланс компании + прогноз
    suspend fun getCompanyBalance(): Result<F3CompanyBalanceDto>
    suspend fun setCompanyBalanceThreshold(lowThreshold: Double): Result<F3CompanyBalanceDto>
    suspend fun syncCompanyBalance(): Result<F3CompanyBalanceDto>
    suspend fun getForecast(year: Int, month: Int): Result<F3ForecastDto>
}

@Singleton
class WalletF3RepositoryImpl @Inject constructor(
    private val api: WalletF3Api,
    private val json: Json,
) : WalletF3Repository {

    // --- ставки ---
    override suspend fun getHourRates() = call { api.getHourRates() }
    override suspend fun setGlobalHourRate(rate: Double) = call { api.setGlobalHourRate(F3HourRateBody(rate)) }
    override suspend fun setUserHourRate(userId: String, rate: Double?) =
        callUnit { api.setUserHourRate(userId, F3UserHourRateBody(rate)) }

    // --- формат дня + расчёт ---
    override suspend fun setDayFormat(userId: String, workDate: String, basis: String) =
        call { api.setDayFormat(F3DayFormatBody(userId, workDate, basis)) }
    override suspend fun recomputeEarnings(userId: String, year: Int, month: Int) =
        call { api.recomputeEarnings(F3RecomputeBody(userId, year, month)) }
    override suspend fun getEarnings(userId: String, year: Int, month: Int) =
        call { api.getEarnings(userId, year, month) }.map { it.items }
    override suspend fun editEarning(
        id: String, basis: String?, hours: Double?, meters: Double?, rate: Double?, amount: Double?, comment: String?,
    ) = call { api.editEarning(id, F3EarningEditBody(basis, hours, meters, rate, amount, comment)) }
    override suspend fun approveEarning(id: String) = call { api.approveEarning(id) }
    override suspend fun voidEarning(id: String) = call { api.voidEarning(id) }

    // --- метры ---
    override suspend fun acceptCabinetMeters(
        cabinetId: String, meters: Double?, userIds: List<String>?, workDate: String?, comment: String?, markDone: Boolean,
    ) = call { api.acceptCabinetMeters(cabinetId, F3AcceptMetersBody(meters, userIds, workDate, comment, markDone)) }
    override suspend fun submitDayMeters(
        workDate: String, meters: Double, objectId: String?, cabinetId: String?, comment: String?,
    ) = call { api.submitDayMeters(F3DayMetersBody(workDate, meters, objectId, cabinetId, comment)) }

    // --- штраф / бонус ---
    override suspend fun createAdjustmentBatch(
        userIds: List<String>, kind: String, amount: Double, reason: String, year: Int, month: Int,
    ) = call { api.createAdjustmentBatch(F3AdjustmentBatchBody(userIds, kind, amount, reason, year, month)) }

    // --- выплаты ---
    override suspend fun assemblePayout(userId: String, kind: String, year: Int?, month: Int?) =
        call { api.assemblePayout(F3AssemblePayoutBody(userId, kind, year, month)) }
    override suspend fun getPayouts(userId: String?, status: String?) =
        call { api.getPayouts(userId, status) }.map { it.items }
    override suspend fun getPayout(id: String) = call { api.getPayout(id) }
    override suspend fun approvePayout(id: String) = call { api.approvePayout(id) }
    override suspend fun sendPayout(id: String) = call { api.sendPayout(id) }
    override suspend fun markPayoutPaid(id: String) = call { api.markPayoutPaid(id) }
    override suspend fun cancelPayout(id: String) = call { api.cancelPayout(id) }

    // --- баланс компании + прогноз ---
    override suspend fun getCompanyBalance() = call { api.getCompanyBalance() }
    override suspend fun setCompanyBalanceThreshold(lowThreshold: Double) =
        call { api.setCompanyBalanceThreshold(F3CompanyBalanceThresholdBody(lowThreshold)) }
    override suspend fun syncCompanyBalance() = call { api.syncCompanyBalance() }
    override suspend fun getForecast(year: Int, month: Int) = call { api.getPayoutsForecast(year, month) }

    // --- helpers ---

    private suspend fun <T> call(block: suspend () -> Response<T>): Result<T> = try {
        val resp = block()
        val body = resp.body()
        if (resp.isSuccessful && body != null) Result.success(body)
        else Result.failure(parseError(resp))
    } catch (e: Exception) {
        Result.failure(Exception(e.message ?: "Ошибка сети", e))
    }

    private suspend fun callUnit(block: suspend () -> Response<*>): Result<Unit> = try {
        val resp = block()
        if (resp.isSuccessful) Result.success(Unit) else Result.failure(parseError(resp))
    } catch (e: Exception) {
        Result.failure(Exception(e.message ?: "Ошибка сети", e))
    }

    /**
     * FastAPI шлёт {"detail": …} — строка / объект {code,message} / массив /
     * {code:"gates_failed", missing:[...]} (одобрение/отправка выплаты при непройденных гейтах).
     * 503 (PAYOUTS_ENABLED=0) у send/sync → дружелюбный текст-как-есть из detail.
     */
    private fun parseError(resp: Response<*>): Throwable {
        val raw = try { resp.errorBody()?.string() } catch (_: Exception) { null }
        val msg = raw?.let { extractDetail(it) } ?: httpMessage(resp.code())
        return Exception(msg)
    }

    private fun extractDetail(raw: String): String? {
        return try {
            val obj = json.parseToJsonElement(raw) as? JsonObject ?: return null
            val fromDetail = when (val d = obj["detail"]) {
                is JsonPrimitive -> d.contentOrNull
                is JsonObject -> {
                    // gates_failed → собрать список непройденных гейтов в строку
                    val missing = (d["missing"] as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    if (!missing.isNullOrEmpty()) missing.joinToString("\n• ", prefix = "Не пройдены проверки:\n• ")
                    else d["message"]?.jsonPrimitive?.contentOrNull ?: d["code"]?.jsonPrimitive?.contentOrNull
                }
                is JsonArray -> d.jsonArray.firstNotNullOfOrNull { el ->
                    ((el as? JsonObject)?.get("msg") as? JsonPrimitive)?.contentOrNull
                }
                else -> null
            }
            fromDetail ?: (obj["message"] as? JsonPrimitive)?.contentOrNull
        } catch (_: Exception) { null }
    }

    private fun httpMessage(code: Int): String = when (code) {
        400 -> "Неверные данные"
        401 -> "Требуется авторизация"
        403 -> "Доступ запрещён"
        404 -> "Не найдено"
        409 -> "Конфликт — действие уже выполнено"
        422 -> "Недостаточно средств или неверные данные"
        503 -> "Выплаты Rocket Work выключены. Доступна ручная отметка «выплачено»."
        in 500..599 -> "Ошибка сервера, попробуйте позже"
        else -> "Ошибка запроса ($code)"
    }
}
