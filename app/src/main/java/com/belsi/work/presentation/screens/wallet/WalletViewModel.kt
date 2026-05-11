package com.belsi.work.presentation.screens.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.repositories.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WalletState(
    val balance: Double = 0.0,
    val pendingAmount: Double = 0.0,
    val totalEarned: Double = 0.0,
    val transactions: List<Transaction> = emptyList()
)

data class Transaction(
    val id: String,
    val type: TransactionType,
    val amount: Double,
    val date: Long,
    val description: String,
    val status: TransactionStatus
)

enum class TransactionType {
    INCOME,
    WITHDRAWAL
}

enum class TransactionStatus {
    PENDING,
    COMPLETED,
    FAILED
}

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletRepository: WalletRepository
) : ViewModel() {

    private val _walletState = MutableStateFlow(WalletState())
    val walletState: StateFlow<WalletState> = _walletState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadWalletData()
    }

    private fun loadWalletData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            walletRepository.getWalletData()
                .onSuccess { walletState ->
                    _walletState.value = walletState
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Ошибка загрузки кошелька"
                }

            _isLoading.value = false
        }
    }

    fun requestWithdrawal(amount: Double, cardNumber: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            walletRepository.requestWithdraw(amount, cardNumber)
                .onSuccess {
                    // Обновляем данные кошелька после успешного запроса
                    loadWalletData()
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Ошибка запроса на вывод"
                    _isLoading.value = false
                }
        }
    }

    fun refresh() {
        loadWalletData()
    }

    fun clearError() {
        _error.value = null
    }

    fun formatAmount(amount: Double): String {
        return "%.2f ₽".format(amount)
    }

    fun formatDate(timestamp: Long): String {
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val year = calendar.get(java.util.Calendar.YEAR)
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        return "%02d.%02d.%04d %02d:%02d".format(day, month, year, hour, minute)
    }
}
