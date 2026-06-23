package com.belsi.work.presentation.screens.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.ActiveRoleManager
import com.belsi.work.data.models.F3CompanyBalanceDto
import com.belsi.work.data.models.F3EarningDto
import com.belsi.work.data.models.F3ForecastDto
import com.belsi.work.data.models.F3HourRatesResponse
import com.belsi.work.data.models.F3PayoutDto
import com.belsi.work.data.models.UserRole
import com.belsi.work.data.repositories.CuratorRepository
import com.belsi.work.data.repositories.WalletF3Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Лёгкий пункт выбора монтажника (без протечки curator-DTO в UI). */
data class WalletPickerUser(
    val id: String,
    val name: String,
    val phone: String,
    val role: String,
) {
    val initials: String get() = name.split(" ").filter { it.isNotBlank() }
        .take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
}

// ============================================================
// UI-состояния
// ============================================================

data class F3HourRatesUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val rates: F3HourRatesResponse? = null,
)

data class F3EarningsUiState(
    val loading: Boolean = false,
    val recomputing: Boolean = false,
    val error: String? = null,
    val year: Int = 0,
    val month: Int = 0,
    val userId: String? = null,
    val userName: String? = null,
    val earnings: List<F3EarningDto> = emptyList(),
    val unclassifiedDays: List<String> = emptyList(),
    val busyEarningId: String? = null,    // идёт edit/approve/void
) {
    val approvedSum: Double get() = earnings.filter { it.status == "approved" }.sumOf { it.amountValue }
    val draftSum: Double get() = earnings.filter { it.status == "draft" }.sumOf { it.amountValue }
    val draftCount: Int get() = earnings.count { it.status == "draft" }
}

data class F3PayoutsUiState(
    val loading: Boolean = false,
    val busy: Boolean = false,         // идёт assemble/approve/send/mark-paid/cancel
    val error: String? = null,
    val year: Int = 0,
    val month: Int = 0,
    val userId: String? = null,
    val userName: String? = null,
    val payouts: List<F3PayoutDto> = emptyList(),
    val draft: F3PayoutDto? = null,    // активный собранный/детализированный черновик
)

data class F3BalanceUiState(
    val loading: Boolean = false,
    val syncing: Boolean = false,
    val savingThreshold: Boolean = false,
    val error: String? = null,
    val balance: F3CompanyBalanceDto? = null,
    val forecast: F3ForecastDto? = null,
)

// ============================================================
// ViewModel
// ============================================================

@HiltViewModel
class WalletF3ViewModel @Inject constructor(
    private val repo: WalletF3Repository,
    private val curatorRepo: CuratorRepository,
    activeRoleManager: ActiveRoleManager,
) : ViewModel() {

    val role: StateFlow<UserRole?> =
        activeRoleManager.activeRole.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // монтажники для выбора (часовые ставки / начисления / выплаты / штраф-бонус)
    private val _users = MutableStateFlow<List<WalletPickerUser>>(emptyList())
    val users: StateFlow<List<WalletPickerUser>> = _users.asStateFlow()

    fun loadUsers() {
        if (_users.value.isNotEmpty()) return
        viewModelScope.launch {
            curatorRepo.getAllUsers(role = "installer", limit = 500)
                .onSuccess { list ->
                    _users.value = list.map { WalletPickerUser(it.id, it.displayName, it.phone, it.role) }
                }
        }
    }

    // ---------- Часовые ставки ----------

    private val _hourRates = MutableStateFlow(F3HourRatesUiState())
    val hourRates: StateFlow<F3HourRatesUiState> = _hourRates.asStateFlow()

    fun loadHourRates() {
        viewModelScope.launch {
            _hourRates.value = _hourRates.value.copy(loading = true, error = null)
            repo.getHourRates()
                .onSuccess { _hourRates.value = _hourRates.value.copy(loading = false, rates = it) }
                .onFailure { _hourRates.value = _hourRates.value.copy(loading = false, error = it.message) }
        }
    }

    fun setGlobalHourRate(rate: Double, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _hourRates.value = _hourRates.value.copy(saving = true)
            repo.setGlobalHourRate(rate)
                .onSuccess { _hourRates.value = _hourRates.value.copy(saving = false); onDone(true, null); loadHourRates() }
                .onFailure { _hourRates.value = _hourRates.value.copy(saving = false); onDone(false, it.message) }
        }
    }

    fun setUserHourRate(userId: String, rate: Double?, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _hourRates.value = _hourRates.value.copy(saving = true)
            repo.setUserHourRate(userId, rate)
                .onSuccess { _hourRates.value = _hourRates.value.copy(saving = false); onDone(true, null); loadHourRates() }
                .onFailure { _hourRates.value = _hourRates.value.copy(saving = false); onDone(false, it.message) }
        }
    }

    // ---------- Начисления (per user/period) ----------

    private val _earnings = MutableStateFlow(F3EarningsUiState())
    val earnings: StateFlow<F3EarningsUiState> = _earnings.asStateFlow()

    fun selectEarningsTarget(userId: String, userName: String, year: Int, month: Int) {
        _earnings.value = _earnings.value.copy(userId = userId, userName = userName, year = year, month = month)
        loadEarnings()
    }

    fun setEarningsPeriod(year: Int, month: Int) {
        _earnings.value = _earnings.value.copy(year = year, month = month)
        if (_earnings.value.userId != null) loadEarnings()
    }

    fun loadEarnings() {
        val st = _earnings.value
        val uid = st.userId ?: return
        viewModelScope.launch {
            _earnings.value = _earnings.value.copy(loading = true, error = null)
            repo.getEarnings(uid, st.year, st.month)
                .onSuccess { _earnings.value = _earnings.value.copy(loading = false, earnings = it) }
                .onFailure { _earnings.value = _earnings.value.copy(loading = false, error = it.message) }
        }
    }

    /** Пересчёт hours-начислений за период (только дни с форматом 'hours'). */
    fun recomputeEarnings(onDone: (Boolean, String?, List<String>) -> Unit) {
        val st = _earnings.value
        val uid = st.userId ?: return
        viewModelScope.launch {
            _earnings.value = _earnings.value.copy(recomputing = true, error = null)
            repo.recomputeEarnings(uid, st.year, st.month)
                .onSuccess { r ->
                    _earnings.value = _earnings.value.copy(recomputing = false, unclassifiedDays = r.unclassifiedDays)
                    onDone(true, null, r.unclassifiedDays); loadEarnings()
                }
                .onFailure {
                    _earnings.value = _earnings.value.copy(recomputing = false, error = it.message)
                    onDone(false, it.message, emptyList())
                }
        }
    }

    /** Назначить формат дня (hours/meters) для unclassified-дня. */
    fun setDayFormat(workDate: String, basis: String, onDone: (Boolean, String?) -> Unit = { _, _ -> }) {
        val uid = _earnings.value.userId ?: return
        viewModelScope.launch {
            repo.setDayFormat(uid, workDate, basis)
                .onSuccess { onDone(true, null); recomputeEarnings { _, _, _ -> } }
                .onFailure { onDone(false, it.message) }
        }
    }

    fun editEarning(
        id: String, basis: String?, hours: Double?, meters: Double?, rate: Double?, amount: Double?, comment: String?,
        onDone: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            _earnings.value = _earnings.value.copy(busyEarningId = id)
            repo.editEarning(id, basis, hours, meters, rate, amount, comment)
                .onSuccess { _earnings.value = _earnings.value.copy(busyEarningId = null); onDone(true, null); loadEarnings() }
                .onFailure { _earnings.value = _earnings.value.copy(busyEarningId = null); onDone(false, it.message) }
        }
    }

    fun approveEarning(id: String) = earningAction(id) { repo.approveEarning(id) }
    fun voidEarning(id: String) = earningAction(id) { repo.voidEarning(id) }

    private fun earningAction(id: String, block: suspend () -> Result<F3EarningDto>) {
        viewModelScope.launch {
            _earnings.value = _earnings.value.copy(busyEarningId = id, error = null)
            block()
                .onSuccess { _earnings.value = _earnings.value.copy(busyEarningId = null); loadEarnings() }
                .onFailure { _earnings.value = _earnings.value.copy(busyEarningId = null, error = it.message) }
        }
    }

    // ---------- Приёмка кабинета (метры) ----------

    fun acceptCabinetMeters(
        cabinetId: String, meters: Double?, userIds: List<String>?, workDate: String?, comment: String?, markDone: Boolean,
        onDone: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            repo.acceptCabinetMeters(cabinetId, meters, userIds, workDate, comment, markDone)
                .onSuccess { onDone(true, null) }
                .onFailure { onDone(false, it.message) }
        }
    }

    // ---------- Самоотчёт монтажника «за сегодня X п.м.» ----------

    fun submitDayMeters(
        workDate: String, meters: Double, objectId: String?, cabinetId: String?, comment: String?,
        onDone: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            repo.submitDayMeters(workDate, meters, objectId, cabinetId, comment)
                .onSuccess { onDone(true, null) }
                .onFailure { onDone(false, it.message) }
        }
    }

    // ---------- Штраф / бонус (мультивыбор) ----------

    fun createAdjustmentBatch(
        userIds: List<String>, isBonus: Boolean, amount: Double, reason: String, year: Int, month: Int,
        onDone: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            repo.createAdjustmentBatch(userIds, if (isBonus) "bonus" else "penalty", amount, reason, year, month)
                .onSuccess { onDone(true, null) }
                .onFailure { onDone(false, it.message) }
        }
    }

    // ---------- Выплаты ----------

    private val _payouts = MutableStateFlow(F3PayoutsUiState())
    val payouts: StateFlow<F3PayoutsUiState> = _payouts.asStateFlow()

    fun selectPayoutsTarget(userId: String, userName: String, year: Int, month: Int) {
        _payouts.value = _payouts.value.copy(userId = userId, userName = userName, year = year, month = month, draft = null)
        loadPayouts()
    }

    fun setPayoutsPeriod(year: Int, month: Int) {
        _payouts.value = _payouts.value.copy(year = year, month = month)
    }

    fun loadPayouts() {
        val uid = _payouts.value.userId ?: return
        viewModelScope.launch {
            _payouts.value = _payouts.value.copy(loading = true, error = null)
            repo.getPayouts(uid, null)
                .onSuccess { _payouts.value = _payouts.value.copy(loading = false, payouts = it) }
                .onFailure { _payouts.value = _payouts.value.copy(loading = false, error = it.message) }
        }
    }

    /** Собрать/пересобрать черновик (monthly за период / on_demand). */
    fun assemblePayout(kind: String, onDone: (Boolean, String?) -> Unit) {
        val st = _payouts.value
        val uid = st.userId ?: return
        viewModelScope.launch {
            _payouts.value = _payouts.value.copy(busy = true, error = null)
            val year = if (kind == "monthly") st.year else null
            val month = if (kind == "monthly") st.month else null
            repo.assemblePayout(uid, kind, year, month)
                .onSuccess { _payouts.value = _payouts.value.copy(busy = false, draft = it); onDone(true, null); loadPayouts() }
                .onFailure { _payouts.value = _payouts.value.copy(busy = false, error = it.message); onDone(false, it.message) }
        }
    }

    /** Открыть детали выплаты (earnings/adjustments/gates). */
    fun openPayout(id: String) {
        viewModelScope.launch {
            _payouts.value = _payouts.value.copy(busy = true, error = null)
            repo.getPayout(id)
                .onSuccess { _payouts.value = _payouts.value.copy(busy = false, draft = it) }
                .onFailure { _payouts.value = _payouts.value.copy(busy = false, error = it.message) }
        }
    }

    fun clearPayoutDraft() { _payouts.value = _payouts.value.copy(draft = null) }

    fun approvePayout(id: String, onDone: (Boolean, String?) -> Unit) = payoutAction(id, onDone) { repo.approvePayout(id) }
    fun sendPayout(id: String, onDone: (Boolean, String?) -> Unit) = payoutAction(id, onDone) { repo.sendPayout(id) }
    fun markPayoutPaid(id: String, onDone: (Boolean, String?) -> Unit) = payoutAction(id, onDone) { repo.markPayoutPaid(id) }
    fun cancelPayout(id: String, onDone: (Boolean, String?) -> Unit) = payoutAction(id, onDone) { repo.cancelPayout(id) }

    private fun payoutAction(id: String, onDone: (Boolean, String?) -> Unit, block: suspend () -> Result<F3PayoutDto>) {
        viewModelScope.launch {
            _payouts.value = _payouts.value.copy(busy = true, error = null)
            block()
                .onSuccess { p ->
                    // если открыт этот же черновик — обновим его, иначе просто перезагрузим список
                    val keepDraft = if (_payouts.value.draft?.id == id) p else _payouts.value.draft
                    _payouts.value = _payouts.value.copy(busy = false, draft = keepDraft)
                    onDone(true, null); loadPayouts()
                }
                .onFailure { _payouts.value = _payouts.value.copy(busy = false, error = it.message); onDone(false, it.message) }
        }
    }

    // ---------- Баланс компании + прогноз ----------

    private val _balance = MutableStateFlow(F3BalanceUiState())
    val balance: StateFlow<F3BalanceUiState> = _balance.asStateFlow()

    fun loadBalance(year: Int, month: Int) {
        viewModelScope.launch {
            _balance.value = _balance.value.copy(loading = true, error = null)
            val b = async { repo.getCompanyBalance() }
            val f = async { repo.getForecast(year, month) }
            val bal = b.await().getOrNull()
            val fc = f.await().getOrNull()
            val err = if (bal == null) b.await().exceptionOrNull()?.message else null
            _balance.value = _balance.value.copy(loading = false, balance = bal ?: _balance.value.balance, forecast = fc, error = err)
        }
    }

    fun syncBalance(year: Int, month: Int, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _balance.value = _balance.value.copy(syncing = true, error = null)
            repo.syncCompanyBalance()
                .onSuccess { _balance.value = _balance.value.copy(syncing = false, balance = it); onDone(true, null); loadBalance(year, month) }
                .onFailure { _balance.value = _balance.value.copy(syncing = false, error = it.message); onDone(false, it.message) }
        }
    }

    fun setLowThreshold(threshold: Double, year: Int, month: Int, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _balance.value = _balance.value.copy(savingThreshold = true)
            repo.setCompanyBalanceThreshold(threshold)
                .onSuccess { _balance.value = _balance.value.copy(savingThreshold = false, balance = it); onDone(true, null); loadBalance(year, month) }
                .onFailure { _balance.value = _balance.value.copy(savingThreshold = false); onDone(false, it.message) }
        }
    }
}
