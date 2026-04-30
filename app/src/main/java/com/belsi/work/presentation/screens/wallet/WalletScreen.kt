package com.belsi.work.presentation.screens.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.presentation.theme.belsiColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    navController: NavController,
    viewModel: WalletViewModel = hiltViewModel()
) {
    val walletState by viewModel.walletState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Кошелёк") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Balance Card
                item {
                    BalanceCard(
                        balance = walletState.balance,
                        pendingAmount = walletState.pendingAmount,
                        totalEarned = walletState.totalEarned,
                        viewModel = viewModel,
                        onWithdrawClick = {
                            navController.navigate(com.belsi.work.presentation.navigation.AppRoute.Withdraw.route)
                        }
                    )
                }

                // Transactions Header
                item {
                    Text(
                        text = "Транзакции",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Transactions List
                if (walletState.transactions.isEmpty()) {
                    item {
                        EmptyTransactionsView()
                    }
                } else {
                    items(walletState.transactions) { transaction ->
                        TransactionCard(transaction = transaction, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(
    balance: Double,
    pendingAmount: Double,
    totalEarned: Double,
    viewModel: WalletViewModel,
    onWithdrawClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Доступно",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = viewModel.formatAmount(balance),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "На проверке",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                    Text(
                        text = viewModel.formatAmount(pendingAmount),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Всего заработано",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                    Text(
                        text = viewModel.formatAmount(totalEarned),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onWithdrawClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                enabled = balance > 0
            ) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Вывести средства")
            }
        }
    }
}

@Composable
private fun TransactionCard(
    transaction: Transaction,
    viewModel: WalletViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when (transaction.type) {
                    TransactionType.INCOME -> Icons.Default.TrendingUp
                    TransactionType.WITHDRAWAL -> Icons.Default.TrendingDown
                }

                val iconColor = when (transaction.type) {
                    TransactionType.INCOME -> MaterialTheme.belsiColors.success
                    TransactionType.WITHDRAWAL -> MaterialTheme.colorScheme.error
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = iconColor.copy(alpha = 0.1f)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp),
                        tint = iconColor
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = transaction.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = viewModel.formatDate(transaction.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val amountText = when (transaction.type) {
                    TransactionType.INCOME -> "+${viewModel.formatAmount(transaction.amount)}"
                    TransactionType.WITHDRAWAL -> "-${viewModel.formatAmount(transaction.amount)}"
                }

                val amountColor = when (transaction.type) {
                    TransactionType.INCOME -> MaterialTheme.belsiColors.success
                    TransactionType.WITHDRAWAL -> MaterialTheme.colorScheme.error
                }

                Text(
                    text = amountText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )

                TransactionStatusBadge(status = transaction.status)
            }
        }
    }
}

@Composable
private fun TransactionStatusBadge(status: TransactionStatus) {
    val (text, color) = when (status) {
        TransactionStatus.PENDING -> "В обработке" to MaterialTheme.belsiColors.warning
        TransactionStatus.COMPLETED -> "Выполнено" to MaterialTheme.belsiColors.success
        TransactionStatus.FAILED -> "Ошибка" to MaterialTheme.colorScheme.error
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

@Composable
private fun EmptyTransactionsView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Receipt,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Нет транзакций",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
