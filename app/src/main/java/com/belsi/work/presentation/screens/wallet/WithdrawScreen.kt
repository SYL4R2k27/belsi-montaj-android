package com.belsi.work.presentation.screens.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.presentation.theme.belsiColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithdrawScreen(
    navController: NavController,
    viewModel: WalletViewModel = hiltViewModel()
) {
    val walletState by viewModel.walletState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var amount by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }

    // Handle error from ViewModel
    LaunchedEffect(error) {
        if (error != null) {
            errorMessage = error!!
            showError = true
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Вывод средств") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = {
            if (showError) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { showError = false }) {
                            Text("OK")
                        }
                    }
                ) {
                    Text(errorMessage)
                }
            }
            if (showSuccess) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = MaterialTheme.belsiColors.success
                ) {
                    Text("Заявка на вывод успешно отправлена")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Balance Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Доступно для вывода",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = viewModel.formatAmount(walletState.balance),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Amount Input
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Сумма вывода") },
                placeholder = { Text("Введите сумму") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                suffix = { Text("₽") }
            )

            // Card Number Input
            OutlinedTextField(
                value = cardNumber,
                onValueChange = {
                    // Allow only digits and limit to 16 characters
                    if (it.length <= 16 && it.all { char -> char.isDigit() }) {
                        cardNumber = it
                    }
                },
                label = { Text("Номер карты") },
                placeholder = { Text("0000 0000 0000 0000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text("Введите 16 цифр без пробелов")
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Withdraw Button
            Button(
                onClick = {
                    val amountValue = amount.toDoubleOrNull()

                    when {
                        amountValue == null || amountValue <= 0 -> {
                            errorMessage = "Введите корректную сумму"
                            showError = true
                        }
                        amountValue > walletState.balance -> {
                            errorMessage = "Недостаточно средств"
                            showError = true
                        }
                        cardNumber.length != 16 -> {
                            errorMessage = "Введите корректный номер карты (16 цифр)"
                            showError = true
                        }
                        else -> {
                            viewModel.requestWithdrawal(amountValue, cardNumber)
                            showSuccess = true
                            navController.popBackStack()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Отправить заявку")
                }
            }

            // Info Text
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Информация",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Минимальная сумма вывода: 500 ₽\n" +
                                "• Заявка обрабатывается в течение 1-3 рабочих дней\n" +
                                "• Средства поступят на указанную карту\n" +
                                "• Комиссия за вывод отсутствует",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
