package com.belsi.work.presentation.screens.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.models.UserRole
import com.belsi.work.data.models.WalletActDto
import com.belsi.work.data.models.WalletTxDto
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.theme.belsiColors

// ============================================================
// Бренд-токены кошелька (мокап 2026-06-15, M3 primary #5B45D6)
// ============================================================
object WalletBrand {
    val brandTop = Color(0xFF797FE1)
    val brandBottom = Color(0xFF362F97)
    val accent = Color(0xFF5B45D6)
    val heroGradient = Brush.linearGradient(listOf(brandTop, brandBottom))

    fun rub(v: Double): String {
        val nf = java.text.NumberFormat.getNumberInstance(java.util.Locale("ru", "RU"))
        nf.maximumFractionDigits = if (v % 1.0 == 0.0) 0 else 2
        return nf.format(v) + " ₽"
    }
    fun day(iso: String?): String {
        if (iso == null) return ""
        val p = iso.take(10).split("-")
        return if (p.size == 3) "${p[2]}.${p[1]}" else iso.take(10)
    }
}

// ============================================================
// Диспетчер по роли
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(navController: NavController) {
    val vm: WalletViewModel = hiltViewModel()
    val role by vm.role.collectAsState()
    val ui by vm.ui.collectAsState()

    LaunchedEffect(role) {
        when (role) {
            UserRole.CURATOR -> vm.loadCurator()
            else -> vm.loadPersonal()
        }
    }

    val title = if (role == UserRole.CURATOR) "Кошелёк команды" else "Кошелёк"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (role) {
                UserRole.CURATOR -> CuratorWalletContent(vm, ui, navController)
                UserRole.FOREMAN -> ForemanWalletContent(vm, ui, navController)
                else -> InstallerWalletContent(vm, ui, navController)
            }
        }
    }
}

// ============================================================
// Монтажник / персональный — «фото→деньги»
// ============================================================
@Composable
fun InstallerWalletContent(vm: WalletViewModel, ui: WalletUiState, navController: NavController) {
    var showWithdraw by remember { mutableStateOf(false) }
    var detailTx by remember { mutableStateOf<WalletTxDto?>(null) }
    var showTaxForm by remember { mutableStateOf(false) }
    var detailAct by remember { mutableStateOf<WalletActDto?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        WalletHero(
            available = ui.available,
            earned = ui.wallet?.totalEarned ?: 0.0,
            withdrawn = ui.wallet?.totalWithdrawn ?: 0.0,
            pending = ui.wallet?.pendingAmount ?: 0.0,
            canWithdraw = ui.canWithdraw,
            // employed — выплаты идут зарплатой, кнопки вывода нет
            onWithdraw = if (ui.isEmployed) null else { { showWithdraw = true } }
        )

        // Ф2.5 гейтинг (§3). needsContract = самозанятый не принял Договор → отдельный
        // призыв (приоритетнее общего «заполните данные», если остальное уже заполнено).
        when {
            ui.isEmployed -> EmployedInfoBanner { showTaxForm = true }
            ui.needsContract -> ContractNeededCallout(
                onRead = { navController.navigate(AppRoute.LegalDocument.createRoute("self_employed_contract")) },
                onAccept = { showTaxForm = true },
            )
            ui.taxIncomplete -> TaxIncompleteCallout { showTaxForm = true }
        }

        ExplainCard(
            "Деньги — за принятые кабинеты",
            "Снял фотоотчёт → куратор принял кабинет → начислили. Ставка × погонные метры."
        )

        // Карточка «Налоговые данные» (статус) — всегда, если профиль загружен
        ui.taxProfile?.let { TaxProfileCard(it) { showTaxForm = true } }

        ui.pendingWithdraw?.let { PendingBanner(it.amount) }
        ui.error?.let { ErrorLine(it) }

        if (ui.accruals.isNotEmpty()) {
            SectionLabel("Начислено за кабинеты")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ui.accruals.forEach { tx -> AccrualCard(tx) { detailTx = tx } }
            }
        }

        // Ф2.5 — «Мои акты»
        if (ui.acts.isNotEmpty()) {
            SectionLabel("Мои акты · по месяцам")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ui.acts.forEach { act -> InstallerActCard(act, onTap = { detailAct = act }) }
            }
        }

        if (ui.otherOps.isNotEmpty()) {
            SectionLabel("Прочие операции")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(22.dp)) {
                ui.otherOps.forEachIndexed { i, tx ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 60.dp))
                    GenericTxRow(tx)
                }
            }
        }

        if (ui.transactions.isEmpty() && ui.acts.isEmpty() && !ui.loading) {
            Text("Пока нет операций", color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (showWithdraw) {
        WithdrawSheet(vm = vm, available = ui.available,
            methodId = ui.paymentMethods.firstOrNull { it.isDefault }?.id ?: ui.paymentMethods.firstOrNull()?.id,
            methodLabel = ui.paymentMethods.firstOrNull { it.isDefault }?.displayName ?: ui.paymentMethods.firstOrNull()?.displayName,
            onDismiss = { showWithdraw = false })
    }
    detailTx?.let { AccrualDetailSheet(it) { detailTx = null } }
    if (showTaxForm) TaxProfileSheet(vm, ui, navController) { showTaxForm = false }
    detailAct?.let { InstallerActDetailSheet(it, vm) { detailAct = null } }
}

// ============================================================
// Общие компоненты
// ============================================================
@Composable
fun WalletHero(
    available: Double, earned: Double, withdrawn: Double, pending: Double,
    canWithdraw: Boolean = true, teamBadge: String? = null,
    primaryLabel: String = "Доступно к выводу", onWithdraw: (() -> Unit)? = null
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(WalletBrand.heroGradient).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CreditCard, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(primaryLabel, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            teamBadge?.let {
                Surface(color = Color.White.copy(alpha = 0.18f), shape = RoundedCornerShape(99.dp)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(it, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text(WalletBrand.rub(available), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            heroMetric("Начислено", earned, Modifier.weight(1f))
            heroMetric("Выведено", withdrawn, Modifier.weight(1f))
            heroMetric("В ожидании", pending, Modifier.weight(1f))
        }
        if (onWithdraw != null) {
            Button(onClick = onWithdraw, enabled = canWithdraw, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = WalletBrand.brandBottom),
                shape = RoundedCornerShape(15.dp)) {
                Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Вывести деньги", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun heroMetric(k: String, v: Double, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 9.dp)) {
        Text(k, color = Color.White.copy(alpha = 0.82f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
        Text(WalletBrand.rub(v), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ExplainCard(title: String, text: String, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.CameraAlt) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(WalletBrand.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = WalletBrand.accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PendingBanner(amount: Double) {
    val bc = MaterialTheme.belsiColors
    Card(colors = CardDefaults.cardColors(containerColor = bc.warningContainer), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, null, tint = bc.onWarningContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Запрос на ${WalletBrand.rub(amount)} ждёт одобрения", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = bc.onWarningContainer)
                Text("Куратор рассмотрит в течение рабочего дня", style = MaterialTheme.typography.bodySmall, color = bc.onWarningContainer.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
fun ErrorLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}

/** Брендовая плитка-доказательство (реального фото в начислении нет — намёк на окно монтажа). */
@Composable
fun ProofTile(size: androidx.compose.ui.unit.Dp = 60.dp, showCheck: Boolean = true) {
    val bc = MaterialTheme.belsiColors
    Box(Modifier.size(size).clip(RoundedCornerShape(15.dp))
        .background(Brush.linearGradient(listOf(Color(0xFFAAB4C4), Color(0xFF7C8A9E))))) {
        if (showCheck) {
            Box(Modifier.padding(4.dp).size(19.dp).clip(RoundedCornerShape(99.dp)).background(bc.success), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(19.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(11.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccrualCard(tx: WalletTxDto, onClick: () -> Unit) {
    val bc = MaterialTheme.belsiColors
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            ProofTile()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val title = "Кабинет ${tx.cabinetNumber ?: "—"}" + (tx.metersFromDescription?.let { " · $it" } ?: "")
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(tx.objectName, tx.createdAt?.let { "принят ${WalletBrand.day(it)}" }).joinToString(" · ")
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Surface(color = bc.success.copy(alpha = 0.12f), shape = RoundedCornerShape(99.dp)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CameraAlt, null, tint = bc.success, modifier = Modifier.size(10.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("фотоотчёт принят", color = bc.success, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("+${WalletBrand.rub(tx.amount)}", color = bc.success, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
fun GenericTxRow(tx: WalletTxDto) {
    val bc = MaterialTheme.belsiColors
    val (icon, tint) = when (tx.type) {
        "withdrawn" -> Icons.Default.CreditCard to MaterialTheme.colorScheme.onSurfaceVariant
        "penalty" -> Icons.Default.Warning to MaterialTheme.colorScheme.error
        "bonus" -> Icons.Default.CardGiftcard to bc.success
        "refund" -> Icons.Default.Undo to bc.success
        else -> Icons.Default.Circle to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.typeDisplay, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            (tx.description ?: tx.createdAt)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val sign = if (tx.isPositive) "+" else "−"
        val c = if (tx.type == "penalty") MaterialTheme.colorScheme.error else if (tx.isPositive) bc.success else MaterialTheme.colorScheme.onSurface
        Text("$sign${WalletBrand.rub(kotlin.math.abs(tx.amount))}", color = c, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// ============================================================
// Деталь начисления (за что начислено)
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccrualDetailSheet(tx: WalletTxDto, onDismiss: () -> Unit) {
    val bc = MaterialTheme.belsiColors
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("+${WalletBrand.rub(tx.amount)}", color = bc.success, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Text("Кабинет ${tx.cabinetNumber ?: "—"} · принят ${WalletBrand.day(tx.createdAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFAAB4C4), Color(0xFF7C8A9E))))) {
                Surface(color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(99.dp), modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                    Text("Фотоотчёт принят куратором", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(22.dp)) {
                Column {
                    InfoRow(Icons.Default.MeetingRoom, "Кабинет", "№ ${tx.cabinetNumber ?: "—"}", tx.objectName)
                    HorizontalDivider(Modifier.padding(start = 52.dp))
                    InfoRow(Icons.Default.Straighten, "Погонные метры", tx.metersFromDescription ?: "—")
                    HorizontalDivider(Modifier.padding(start = 52.dp))
                    InfoRow(Icons.Default.CurrencyRuble, "Начисление", tx.description ?: "—")
                }
            }
            ExplainCard("Полная ставка — каждому в бригаде",
                "За принятый кабинет полная сумма начисляется каждому активному монтажнику бригады, не делится.", Icons.Default.Groups)
        }
    }
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, sub: String? = null) {
    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            sub?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

// ============================================================
// Шторка вывода
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithdrawSheet(vm: WalletViewModel, available: Double, methodId: String?, methodLabel: String?, onDismiss: () -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val amount = amountText.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
    val minAmount = 500.0

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Вывод средств", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Куратор подтвердит — деньги придут на карту", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = amountText, onValueChange = { v -> amountText = v.filter(Char::isDigit) },
                        placeholder = { Text("0") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                        modifier = Modifier.fillMaxWidth())
                    Text("Доступно: ${WalletBrand.rub(available)} · мин. ${WalletBrand.rub(minAmount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { amountText = (available / 2).toInt().toString() }, label = { Text("Половина") })
                        AssistChip(onClick = { amountText = available.toInt().toString() }, label = { Text("Вся сумма") })
                    }
                }
            }

            methodLabel?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CreditCard, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Column { Text(it, fontWeight = FontWeight.SemiBold); Text("Способ получения · справочно", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }

            error?.let { ErrorLine(it) }

            Button(
                onClick = {
                    submitting = true; error = null
                    vm.withdraw(amount, methodId) { ok, msg -> submitting = false; if (ok) onDismiss() else error = msg }
                },
                enabled = !submitting && amount >= minAmount && amount <= available,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent)
            ) {
                if (submitting) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Запросить вывод", fontWeight = FontWeight.Bold)
            }
            Text("Деньги перечисляются вне приложения после одобрения", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
