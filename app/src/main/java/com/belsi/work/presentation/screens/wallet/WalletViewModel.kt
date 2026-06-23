package com.belsi.work.presentation.screens.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.ActiveRoleManager
import com.belsi.work.data.models.AccrualInfoResponse
import com.belsi.work.data.models.AccrueBody
import com.belsi.work.data.models.CuratorWithdrawalDto
import com.belsi.work.data.models.ForemanTeamResponse
import com.belsi.work.data.models.PaymentMethodDto
import com.belsi.work.data.models.TaxProfileDto
import com.belsi.work.data.models.UserRole
import com.belsi.work.data.models.WalletActDto
import com.belsi.work.data.models.WalletDto
import com.belsi.work.data.models.WalletRatesResponse
import com.belsi.work.data.models.WalletTxDto
import com.belsi.work.data.models.WithdrawalDto
import com.belsi.work.data.repositories.DocumentDownload
import com.belsi.work.data.repositories.WalletRepository
import java.io.File
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WalletUiState(
    val loading: Boolean = false,
    val error: String? = null,
    // монтажник / персональный
    val wallet: WalletDto? = null,
    val transactions: List<WalletTxDto> = emptyList(),
    val paymentMethods: List<PaymentMethodDto> = emptyList(),
    val withdrawals: List<WithdrawalDto> = emptyList(),
    // Ф2.5 — НПД/ИНН-профиль + акты монтажника
    val taxProfile: TaxProfileDto? = null,
    val taxSaving: Boolean = false,
    val acts: List<WalletActDto> = emptyList(),
    // бригадир — команда
    val team: ForemanTeamResponse? = null,
    val teamLoading: Boolean = false,
    val teamFailed: Boolean = false,
    val teamError: String? = null,
    // куратор
    val curatorQueue: List<CuratorWithdrawalDto> = emptyList(),
    val rates: WalletRatesResponse? = null,
    val actionError: String? = null,
) {
    val accruals: List<WalletTxDto> get() = transactions.filter { it.isCabinetAccrual }
    val otherOps: List<WalletTxDto> get() = transactions.filter { !it.isCabinetAccrual }
    val available: Double get() = wallet?.available ?: 0.0
    // Ф2.5 гейтинг (§3): employed → выплаты мимо приложения; incomplete → блок выплат
    val isEmployed: Boolean get() = taxProfile?.isEmployed == true
    val payoutReady: Boolean get() = taxProfile?.isPayoutReady ?: true   // до загрузки профиля не блокируем
    val taxIncomplete: Boolean get() = taxProfile?.isSelfEmployed == true && taxProfile?.isComplete == false
    /** Самозанятый не принял Договор → выплата заблокирована (bool на бэке внутри is_payout_ready). */
    val needsContract: Boolean get() = taxProfile?.needsContract == true
    val canWithdraw: Boolean get() = !isEmployed && payoutReady && available > 0
    val pendingWithdraw: WithdrawalDto? get() = withdrawals.firstOrNull { it.status == "pending" }
    val curatorPending: List<CuratorWithdrawalDto> get() = curatorQueue.filter { it.status == "pending" }
    val curatorQueueActive: List<CuratorWithdrawalDto> get() =
        curatorQueue.filter { it.status == "pending" || it.status == "approved" }
    val toPayoutSum: Double get() = curatorQueueActive.sumOf { it.amount }
    /** уникальные получатели из очереди — для штрафа/бонуса */
    val queuePersons: List<Triple<String, String, String?>> get() {
        val seen = HashSet<String>(); val out = ArrayList<Triple<String, String, String?>>()
        curatorQueue.forEach { w ->
            val uid = w.userId
            if (uid != null && seen.add(uid)) out.add(Triple(uid, w.userName, w.userPhone))
        }
        return out
    }
}

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val repo: WalletRepository,
    activeRoleManager: ActiveRoleManager,
) : ViewModel() {

    val role: StateFlow<UserRole?> =
        activeRoleManager.activeRole.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _ui = MutableStateFlow(WalletUiState())
    val ui: StateFlow<WalletUiState> = _ui.asStateFlow()

    // ---------- Персональный (монтажник / координатор / бригадир-Мой) ----------

    fun loadPersonal() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            val w = async { repo.getWallet() }
            val t = async { repo.getTransactions(1, 100) }
            val pm = async { repo.getPaymentMethods() }
            val wd = async { repo.getWithdrawals() }
            val tp = async { repo.getTaxProfile() }
            val ac = async { repo.getActs() }
            val wallet = w.await().getOrNull()
            val txs = t.await().getOrDefault(emptyList())
            val methods = pm.await().getOrDefault(emptyList())
            val wds = wd.await().getOrDefault(emptyList())
            val tax = tp.await().getOrNull()
            val acts = ac.await().getOrDefault(emptyList())
            val err = if (wallet == null) (w.await().exceptionOrNull()?.message ?: "Не удалось загрузить кошелёк") else null
            _ui.value = _ui.value.copy(
                loading = false, error = err,
                wallet = wallet ?: _ui.value.wallet,
                transactions = txs, paymentMethods = methods, withdrawals = wds,
                taxProfile = tax ?: _ui.value.taxProfile,
                acts = acts,
            )
        }
    }

    fun withdraw(amount: Double, paymentMethodId: String?, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.withdraw(amount, paymentMethodId)
                .onSuccess { onDone(true, null); loadPersonal() }
                .onFailure { onDone(false, it.message) }
        }
    }

    // ---------- Ф2.5: НПД/ИНН-профиль (монтажник) ----------

    /** Сохранить налоговые данные (ИНН / № справки / дата + приём договора). contractType — опц. */
    fun saveTaxProfile(
        contractType: String?, inn: String?, certNumber: String?, certDate: String?,
        contractAccepted: Boolean? = null,
        onDone: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(taxSaving = true)
            repo.updateTaxProfile(contractType, inn, certNumber, certDate, contractAccepted)
                .onSuccess {
                    _ui.value = _ui.value.copy(taxSaving = false, taxProfile = it)
                    onDone(true, null); loadPersonal()
                }
                .onFailure {
                    _ui.value = _ui.value.copy(taxSaving = false)
                    onDone(false, it.message)
                }
        }
    }

    /** Принять Договор самозанятого, не трогая остальные поля профиля. */
    fun setContractAccepted(onDone: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(taxSaving = true)
            repo.updateTaxProfile(contractAccepted = true)
                .onSuccess {
                    _ui.value = _ui.value.copy(taxSaving = false, taxProfile = it)
                    onDone(true, null); loadPersonal()
                }
                .onFailure {
                    _ui.value = _ui.value.copy(taxSaving = false)
                    onDone(false, it.message)
                }
        }
    }

    /** Переключить тип договора (Самозанятый/Штат) — отдельный быстрый upsert. */
    fun setContractType(contractType: String) {
        viewModelScope.launch {
            // оптимистично отражаем
            _ui.value.taxProfile?.let { _ui.value = _ui.value.copy(taxProfile = it.copy(contractType = contractType)) }
            repo.updateTaxProfile(contractType = contractType)
                .onSuccess { _ui.value = _ui.value.copy(taxProfile = it) }
                .onFailure { /* откатим перезагрузкой */ loadPersonal() }
        }
    }

    /** Загрузить файл справки НПД (File уже скопирован из URI). */
    fun uploadCertificate(file: File, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(taxSaving = true)
            repo.uploadCertificate(file)
                .onSuccess {
                    _ui.value = _ui.value.copy(taxSaving = false, taxProfile = it)
                    onDone(true, null); loadPersonal()
                }
                .onFailure {
                    _ui.value = _ui.value.copy(taxSaving = false)
                    onDone(false, it.message)
                }
        }
    }

    // ---------- Ф2.5: Акты — скачивание (обе роли) ----------

    /** Скачать документ Акта. onResult получает байты+имя+mime или ошибку. */
    fun downloadAct(actId: String, curator: Boolean, format: String = "docx", onResult: (DocumentDownload?, String?) -> Unit) {
        viewModelScope.launch {
            val r = if (curator) repo.getCuratorActDocument(actId, format) else repo.getActDocument(actId, format)
            r.onSuccess { onResult(it, null) }.onFailure { onResult(null, it.message) }
        }
    }

    // ---------- Бригадир: команда ----------

    fun loadTeam() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(teamLoading = true, teamFailed = false, teamError = null)
            repo.getForemanTeam()
                .onSuccess { _ui.value = _ui.value.copy(team = it, teamLoading = false) }
                .onFailure { _ui.value = _ui.value.copy(teamLoading = false, teamFailed = true, teamError = it.message) }
        }
    }

    // ---------- Куратор ----------

    fun loadCurator() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            val q = async { repo.getCuratorWithdrawals(null) }
            val r = async { repo.getRates() }
            val queue = q.await().getOrDefault(emptyList())
            val rates = r.await().getOrNull()
            val err = q.await().exceptionOrNull()?.message
            _ui.value = _ui.value.copy(loading = false, curatorQueue = queue, rates = rates ?: _ui.value.rates, error = err)
        }
    }

    fun approve(id: String) = curatorAction { repo.approveWithdrawal(id) }
    fun reject(id: String) = curatorAction { repo.rejectWithdrawal(id, null) }
    fun markPaid(id: String) = curatorAction { repo.markPaid(id) }

    fun adjust(userId: String, isBonus: Boolean, amount: Double, reason: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.createAdjustment(userId, if (isBonus) "bonus" else "penalty", amount, reason)
                .onSuccess { onDone(true, null); loadCurator() }
                .onFailure { onDone(false, it.message) }
        }
    }

    fun setGlobalRate(rate: Double, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            repo.setGlobalRate(rate)
                .onSuccess { onDone(true, null); loadCurator() }
                .onFailure { onDone(false, it.message) }
        }
    }

    private fun curatorAction(block: suspend () -> Result<WithdrawalDto>) {
        viewModelScope.launch {
            block()
                .onSuccess { loadCurator() }
                .onFailure { _ui.value = _ui.value.copy(actionError = it.message) }
        }
    }

    fun clearActionError() { _ui.value = _ui.value.copy(actionError = null) }

    // ---------- Куратор: начисление за кабинет (отдельное состояние) ----------

    private val _accrual = MutableStateFlow(AccrualUiState())
    val accrual: StateFlow<AccrualUiState> = _accrual.asStateFlow()

    fun loadAccrual(cabinetId: String) {
        viewModelScope.launch {
            _accrual.value = AccrualUiState(loading = true)
            repo.getAccrualInfo(cabinetId)
                .onSuccess { _accrual.value = AccrualUiState(info = it) }
                .onFailure { _accrual.value = AccrualUiState(error = it.message ?: "Не удалось загрузить кабинет") }
        }
    }

    fun accrue(cabinetId: String, meters: Double?, userIds: List<String>?, force: Boolean, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _accrual.value = _accrual.value.copy(submitting = true, error = null)
            repo.createAccruals(AccrueBody(cabinetId, meters, userIds, force))
                .onSuccess { _accrual.value = _accrual.value.copy(submitting = false, done = true); onResult(true, null) }
                .onFailure {
                    _accrual.value = _accrual.value.copy(submitting = false, error = it.message)
                    onResult(false, it.message)
                }
        }
    }

    // ---------- Ф2.5: Куратор — месячные Акты (отдельное состояние с периодом) ----------

    private val _acts = MutableStateFlow(CuratorActsUiState())
    val curatorActs: StateFlow<CuratorActsUiState> = _acts.asStateFlow()

    fun loadCuratorActs(year: Int, month: Int) {
        viewModelScope.launch {
            _acts.value = _acts.value.copy(loading = true, error = null, year = year, month = month)
            repo.getCuratorActs(year, month)
                .onSuccess { _acts.value = _acts.value.copy(loading = false, acts = it) }
                .onFailure { _acts.value = _acts.value.copy(loading = false, error = it.message, acts = emptyList()) }
        }
    }

    /** Собрать/пересобрать черновик Акта (assemble) для юзера за текущий период. */
    fun assembleAct(userId: String, onDone: (Boolean, String?) -> Unit) {
        val st = _acts.value
        viewModelScope.launch {
            _acts.value = _acts.value.copy(busyUserId = userId, error = null)
            repo.assembleAct(userId, st.year, st.month)
                .onSuccess { _acts.value = _acts.value.copy(busyUserId = null); onDone(true, null); loadCuratorActs(st.year, st.month) }
                .onFailure { _acts.value = _acts.value.copy(busyUserId = null, error = it.message); onDone(false, it.message) }
        }
    }

    fun signAct(actId: String, onDone: (Boolean, String?) -> Unit) = actAction(actId, onDone) { repo.signAct(actId) }
    fun markActPaid(actId: String, onDone: (Boolean, String?) -> Unit) = actAction(actId, onDone) { repo.markActPaid(actId) }

    private fun actAction(actId: String, onDone: (Boolean, String?) -> Unit, block: suspend () -> Result<WalletActDto>) {
        val st = _acts.value
        viewModelScope.launch {
            _acts.value = _acts.value.copy(busyActId = actId, error = null)
            block()
                .onSuccess { _acts.value = _acts.value.copy(busyActId = null); onDone(true, null); loadCuratorActs(st.year, st.month) }
                .onFailure { _acts.value = _acts.value.copy(busyActId = null, error = it.message); onDone(false, it.message) }
        }
    }
}

data class CuratorActsUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val year: Int = 0,
    val month: Int = 0,
    val acts: List<WalletActDto> = emptyList(),
    val busyUserId: String? = null,   // идёт assemble
    val busyActId: String? = null,    // идёт sign/mark-paid
)

data class AccrualUiState(
    val loading: Boolean = false,
    val submitting: Boolean = false,
    val done: Boolean = false,
    val info: AccrualInfoResponse? = null,
    val error: String? = null,
)
