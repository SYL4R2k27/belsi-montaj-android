package com.belsi.work.data.repositories

import com.belsi.work.data.models.AccrualInfoResponse
import com.belsi.work.data.models.AccrueBody
import com.belsi.work.data.models.AccrueResponse
import com.belsi.work.data.models.AdjustmentBody
import com.belsi.work.data.models.AssembleActBody
import com.belsi.work.data.models.CuratorWithdrawalDto
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
import com.belsi.work.data.models.WalletDto
import com.belsi.work.data.models.WalletRatesResponse
import com.belsi.work.data.models.WalletTxDto
import com.belsi.work.data.models.WithdrawBody
import com.belsi.work.data.models.WithdrawalDto
import com.belsi.work.data.remote.api.WalletApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import retrofit2.Response
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Кошелёк BELSI — репозиторий (Ф2). Тонкие safeCall-обёртки + парсинг FastAPI-detail. */
interface WalletRepository {
    suspend fun getWallet(): Result<WalletDto>
    suspend fun getTransactions(page: Int = 1, limit: Int = 100): Result<List<WalletTxDto>>
    suspend fun getWithdrawals(): Result<List<WithdrawalDto>>
    suspend fun getPaymentMethods(): Result<List<PaymentMethodDto>>
    suspend fun withdraw(amount: Double, paymentMethodId: String?, note: String? = null): Result<WithdrawalDto>
    suspend fun proposeMeters(cabinetId: String, meters: Double, comment: String? = null): Result<MetersProposalDto>
    // куратор
    suspend fun getRates(): Result<WalletRatesResponse>
    suspend fun setGlobalRate(rate: Double): Result<Unit>
    suspend fun setUserRate(userId: String, rate: Double?): Result<Unit>
    suspend fun getAccrualInfo(cabinetId: String): Result<AccrualInfoResponse>
    suspend fun createAccruals(body: AccrueBody): Result<AccrueResponse>
    suspend fun getCuratorWithdrawals(status: String? = null): Result<List<CuratorWithdrawalDto>>
    suspend fun approveWithdrawal(id: String): Result<WithdrawalDto>
    suspend fun rejectWithdrawal(id: String, reason: String? = null): Result<WithdrawalDto>
    suspend fun markPaid(id: String): Result<WithdrawalDto>
    suspend fun createAdjustment(userId: String, type: String, amount: Double, reason: String): Result<Unit>
    // бригадир
    suspend fun getForemanTeam(): Result<ForemanTeamResponse>
    // Ф2.5 — НПД/ИНН-профиль (монтажник)
    suspend fun getTaxProfile(): Result<TaxProfileDto>
    suspend fun updateTaxProfile(
        contractType: String? = null,
        inn: String? = null,
        certificateNumber: String? = null,
        certificateDate: String? = null,
        contractAccepted: Boolean? = null,
    ): Result<TaxProfileDto>
    suspend fun uploadCertificate(file: File): Result<TaxProfileDto>
    // Ф2.5 — Акты (монтажник)
    suspend fun getActs(year: Int? = null, month: Int? = null): Result<List<WalletActDto>>
    suspend fun getAct(id: String): Result<WalletActDto>
    suspend fun getActDocument(id: String, format: String = "docx"): Result<DocumentDownload>
    // Ф2.5 — Акты (куратор)
    suspend fun getCuratorActs(year: Int, month: Int): Result<List<WalletActDto>>
    suspend fun assembleAct(userId: String, year: Int, month: Int): Result<WalletActDto>
    suspend fun signAct(id: String): Result<WalletActDto>
    suspend fun markActPaid(id: String): Result<WalletActDto>
    suspend fun getCuratorActDocument(id: String, format: String = "docx"): Result<DocumentDownload>
}

/** Скачанный документ Акта (байты + имя файла + mime) — для сохранения/шеринга на клиенте. */
data class DocumentDownload(
    val bytes: ByteArray,
    val fileName: String,
    val mime: String,
)

@Singleton
class WalletRepositoryImpl @Inject constructor(
    private val api: WalletApi,
    private val json: Json,
) : WalletRepository {

    override suspend fun getWallet() = call { api.getWallet() }
    override suspend fun getTransactions(page: Int, limit: Int) =
        call { api.getTransactions(page, limit) }.map { it.items }
    override suspend fun getWithdrawals() =
        call { api.getWithdrawals(1, 50) }.map { it.items }
    override suspend fun getPaymentMethods() = call { api.getPaymentMethods() }
    override suspend fun withdraw(amount: Double, paymentMethodId: String?, note: String?) =
        call { api.requestWithdrawal(WithdrawBody(amount, paymentMethodId, note)) }
    override suspend fun proposeMeters(cabinetId: String, meters: Double, comment: String?) =
        call { api.proposeMeters(cabinetId, MetersBody(meters, comment)) }

    override suspend fun getRates() = call { api.getRates() }
    override suspend fun setGlobalRate(rate: Double) =
        callUnit { api.setGlobalRate(GlobalRateBody(rate)) }
    override suspend fun setUserRate(userId: String, rate: Double?) =
        callUnit { api.setUserRate(userId, UserRateBody(rate)) }
    override suspend fun getAccrualInfo(cabinetId: String) = call { api.getAccrualInfo(cabinetId) }
    override suspend fun createAccruals(body: AccrueBody) = call { api.createAccruals(body) }

    override suspend fun getCuratorWithdrawals(status: String?) =
        call { api.getCuratorWithdrawals(status) }.map { it.items }
    override suspend fun approveWithdrawal(id: String) = call { api.approveWithdrawal(id) }
    override suspend fun rejectWithdrawal(id: String, reason: String?) =
        call { api.rejectWithdrawal(id, RejectBody(reason)) }
    override suspend fun markPaid(id: String) = call { api.markPaid(id) }
    override suspend fun createAdjustment(userId: String, type: String, amount: Double, reason: String) =
        callUnit { api.createAdjustment(AdjustmentBody(userId, type, amount, reason)) }

    override suspend fun getForemanTeam() = call { api.getForemanTeam() }

    // --- Ф2.5: НПД/ИНН-профиль ---

    override suspend fun getTaxProfile() = call { api.getTaxProfile() }

    override suspend fun updateTaxProfile(
        contractType: String?, inn: String?, certificateNumber: String?, certificateDate: String?,
        contractAccepted: Boolean?,
    ) = call {
        api.updateTaxProfile(
            TaxProfileBody(
                contractType = contractType,
                inn = inn?.takeIf { it.isNotBlank() },
                npdCertificateNumber = certificateNumber?.takeIf { it.isNotBlank() },
                npdCertificateDate = certificateDate?.takeIf { it.isNotBlank() },
                contractAccepted = contractAccepted,
            )
        )
    }

    override suspend fun uploadCertificate(file: File): Result<TaxProfileDto> = try {
        val mime = when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            else -> "image/jpeg"
        }
        val part = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody(mime.toMediaTypeOrNull()))
        val up = api.uploadCertificate(part)
        if (up.isSuccessful) {
            // профиль вернётся свежим вторым запросом (бэк отдаёт только url)
            getTaxProfile()
        } else {
            Result.failure(parseError(up))
        }
    } catch (e: Exception) {
        Result.failure(Exception(e.message ?: "Не удалось загрузить файл", e))
    }

    // --- Ф2.5: Акты (монтажник) ---

    override suspend fun getActs(year: Int?, month: Int?) =
        call { api.getActs(year, month) }.map { it.items }
    override suspend fun getAct(id: String) = call { api.getAct(id) }
    override suspend fun getActDocument(id: String, format: String) =
        download(id, format) { api.getActDocument(id, format) }

    // --- Ф2.5: Акты (куратор) ---

    override suspend fun getCuratorActs(year: Int, month: Int) =
        call { api.getCuratorActs(year, month) }.map { it.items }
    override suspend fun assembleAct(userId: String, year: Int, month: Int) =
        call { api.assembleAct(AssembleActBody(userId, year, month)) }
    override suspend fun signAct(id: String) = call { api.signAct(id) }
    override suspend fun markActPaid(id: String) = call { api.markActPaid(id) }
    override suspend fun getCuratorActDocument(id: String, format: String) =
        download(id, format) { api.getCuratorActDocument(id, format) }

    // --- helpers ---

    /** Скачивание файла Акта: ResponseBody → байты + имя из Content-Disposition + mime. */
    private suspend fun download(
        id: String, format: String, block: suspend () -> Response<okhttp3.ResponseBody>,
    ): Result<DocumentDownload> = try {
        val resp = block()
        val body = resp.body()
        if (resp.isSuccessful && body != null) {
            val mime = resp.headers()["Content-Type"]?.substringBefore(";")?.trim()
                ?: body.contentType()?.toString()
                ?: if (format == "pdf") "application/pdf"
                else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            val cd = resp.headers()["Content-Disposition"]
            val nameFromHeader = cd?.let { hdr ->
                Regex("""filename\*?=(?:UTF-8'')?\"?([^\";]+)""").find(hdr)?.groupValues?.getOrNull(1)
            }?.trim()
            val ext = if (mime.contains("pdf")) "pdf" else "docx"
            val fileName = nameFromHeader?.takeIf { it.isNotBlank() } ?: "Акт_${id.take(8)}.$ext"
            Result.success(DocumentDownload(body.bytes(), fileName, mime))
        } else {
            Result.failure(parseError(resp))
        }
    } catch (e: Exception) {
        Result.failure(Exception(e.message ?: "Не удалось скачать Акт", e))
    }

    private suspend fun <T> call(block: suspend () -> Response<T>): Result<T> = try {
        val resp = block()
        val body = resp.body()
        if (resp.isSuccessful && body != null) Result.success(body)
        else Result.failure(parseError(resp))
    } catch (e: Exception) {
        Result.failure(Exception(e.message ?: "Ошибка сети", e))
    }

    private suspend fun callUnit(block: suspend () -> Response<Unit>): Result<Unit> = try {
        val resp = block()
        if (resp.isSuccessful) Result.success(Unit) else Result.failure(parseError(resp))
    } catch (e: Exception) {
        Result.failure(Exception(e.message ?: "Ошибка сети", e))
    }

    /** FastAPI шлёт {"detail": …} (строка/объект {code,message}/массив), часть ручек — {"message": …}. */
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
                is JsonObject -> d["message"]?.jsonPrimitive?.contentOrNull
                    ?: d["code"]?.jsonPrimitive?.contentOrNull
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
        in 500..599 -> "Ошибка сервера, попробуйте позже"
        else -> "Ошибка запроса ($code)"
    }
}
