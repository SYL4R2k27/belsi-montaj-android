package com.belsi.work.presentation.screens.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.models.F3CompanyBalanceDto
import com.belsi.work.data.models.F3EarningDto
import com.belsi.work.data.models.F3ForecastDto
import com.belsi.work.data.models.F3HourRateOverrideDto
import com.belsi.work.data.models.F3PayoutDto
import com.belsi.work.data.models.WalletActDto
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.theme.belsiColors
import java.util.Calendar

// ============================================================
// Ф3 — общие helpers (период, выбор монтажника)
// ============================================================

private fun monthName(month: Int): String = WalletActDto.RU_MONTHS.getOrNull(month - 1) ?: "$month"

private fun f3num(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)

@Composable
private fun F3PeriodSelector(year: Int, month: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, "Раньше") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(monthName(month), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("$year", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "Позже") }
        }
    }
}

/** Выпадающий выбор монтажника (single). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun F3UserDropdown(
    users: List<WalletPickerUser>, selected: WalletPickerUser?, label: String = "Монтажник",
    onSelect: (WalletPickerUser) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected?.name ?: "— выбрать —", onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (users.isEmpty()) DropdownMenuItem(text = { Text("Загрузка…") }, onClick = {})
            users.forEach { u ->
                DropdownMenuItem(text = { Text(u.name) }, onClick = { onSelect(u); open = false })
            }
        }
    }
}

@Composable
private fun F3StatCard(k: String, v: String, tint: Color, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column(Modifier.padding(13.dp)) {
            Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(v, color = tint, fontSize = 19.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun F3NavRow(icon: ImageVector, tint: Color, title: String, sub: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun F3Scaffold(title: String, navController: NavController, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title, fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } })
    }) { pad -> content(pad) }
}

// ============================================================
// Ф3 — ХАБ расчёта (куратор)
// ============================================================
@Composable
fun WalletF3HubScreen(navController: NavController) {
    val vm: WalletF3ViewModel = hiltViewModel()
    val bal by vm.balance.collectAsState()
    val now = remember { Calendar.getInstance() }
    val year = now.get(Calendar.YEAR)
    val month = now.get(Calendar.MONTH) + 1
    val bc = MaterialTheme.belsiColors

    LaunchedEffect(Unit) { vm.loadBalance(year, month) }

    F3Scaffold("Расчёт и выплаты", navController) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Баланс компании — карточка-обзор (жёсткий гейт выплат)
            CompanyBalanceMini(bal.balance, bal.forecast) { navController.navigate(AppRoute.WalletF3Balance.route) }
            bal.balance?.takeIf { it.isLow }?.let {
                Card(colors = CardDefaults.cardColors(containerColor = bc.warningContainer), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WarningAmber, null, tint = bc.onWarningContainer, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Низкий остаток на счёте компании — пополните перед выплатами", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = bc.onWarningContainer)
                    }
                }
            }

            SectionLabel("Начисления")
            F3NavRow(Icons.Default.Schedule, WalletBrand.accent, "Ставка ₽/час", "Глобальная + индивидуальные (расчёт по часам)") { navController.navigate(AppRoute.WalletF3HourRates.route) }
            F3NavRow(Icons.Default.Calculate, WalletBrand.accent, "Начисления по человеку", "Часы/метры за месяц: пересчёт, правка, одобрение") { navController.navigate(AppRoute.WalletF3Earnings.route) }

            SectionLabel("Выплаты")
            F3NavRow(Icons.AutoMirrored.Filled.Send, bc.success, "Выплаты Rocket Work", "Собрать → одобрить → выплата + чек ФНС (брутто − налог − комиссия)") { navController.navigate(AppRoute.WalletF3Payouts.route) }
            F3NavRow(Icons.Default.AccountBalanceWallet, WalletBrand.brandBottom, "Счёт компании · прогноз", "Остаток · «нужно X / на счету Y»") { navController.navigate(AppRoute.WalletF3Balance.route) }

            Text("Куратор выбирает формат дня (часы/метры). Часы — по сменам с одобренным фото. Метры — при приёмке кабинета. Бонус добавляется в выплату и чек, штраф вычитается до суммы. Налог НПД и комиссия посредника — за счёт монтажника.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun CompanyBalanceMini(balance: F3CompanyBalanceDto?, forecast: F3ForecastDto?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(WalletBrand.heroGradient).clickable { onClick() }.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AccountBalance, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Счёт компании (Rocket Work)", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (balance?.payoutsEnabled == false) {
                Surface(color = Color.White.copy(alpha = 0.18f), shape = RoundedCornerShape(99.dp)) {
                    Text("ручной режим", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
                }
            }
        }
        Text(WalletBrand.rub(balance?.balance ?: 0.0), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, maxLines = 1)
        forecast?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                f3HeroMetric("Нужно за месяц", WalletBrand.rub(it.needed), Modifier.weight(1f))
                f3HeroMetric(if (it.sufficient) "Хватает" else "Не хватает", if (it.sufficient) "—" else WalletBrand.rub(it.shortfall), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun f3HeroMetric(k: String, v: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 9.dp)) {
        Text(k, color = Color.White.copy(alpha = 0.82f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
        Text(v, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ============================================================
// Ф3 — ЧАСОВЫЕ СТАВКИ
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletF3HourRatesScreen(navController: NavController) {
    val vm: WalletF3ViewModel = hiltViewModel()
    val st by vm.hourRates.collectAsState()
    val users by vm.users.collectAsState()
    var globalText by remember(st.rates) { mutableStateOf(st.rates?.globalHourRate?.let { f3num(it) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var showUserRate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadHourRates(); vm.loadUsers() }

    F3Scaffold("Ставка ₽/час", navController) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ExplainCard("Часовая ставка", "Применяется для дней с форматом «по часам». Часы берутся из смены с одобренным фотоотчётом.", Icons.Default.Schedule)

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ГЛОБАЛЬНАЯ СТАВКА ₽/ЧАС", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = globalText, onValueChange = { globalText = it.filter { c -> c.isDigit() || c == '.' } }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                            placeholder = { Text("не задана") },
                            textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black))
                        Button(onClick = {
                            val r = globalText.replace(",", ".").toDoubleOrNull()
                            if (r != null && r > 0) { error = null; vm.setGlobalHourRate(r) { ok, msg -> if (!ok) error = msg } }
                        }, enabled = !st.saving && globalText.replace(",", ".").toDoubleOrNull() != null,
                            colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent), shape = RoundedCornerShape(99.dp)) {
                            if (st.saving) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Text("Сохранить", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (st.rates?.globalHourRate == null) Text("Ставка не задана — расчёт по часам даст 0, пока не укажете.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.belsiColors.warning)
                }
            }

            error?.let { ErrorLine(it) }
            st.error?.let { ErrorLine(it) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Индивидуальные · ${st.rates?.overrides?.size ?: 0}")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showUserRate = true }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Назначить")
                }
            }
            val ov = st.rates?.overrides.orEmpty()
            if (ov.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp)) {
                    ov.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                        HourRateOverrideRow(r, removing = st.saving) { vm.setUserHourRate(r.userId, null) { _, _ -> } }
                    }
                }
            } else {
                Text("Индивидуальных часовых ставок нет — у всех глобальная.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showUserRate) {
        F3UserHourRateSheet(vm, users) { showUserRate = false }
    }
}

@Composable
private fun HourRateOverrideRow(r: F3HourRateOverrideDto, removing: Boolean, onRemove: () -> Unit) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(r.userName, fontWeight = FontWeight.SemiBold)
            r.userPhone?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text("${f3num(r.rate)} ₽/ч", color = WalletBrand.accent, fontWeight = FontWeight.Bold)
        IconButton(onClick = onRemove, enabled = !removing) { Icon(Icons.Default.Close, "Снять ставку", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun F3UserHourRateSheet(vm: WalletF3ViewModel, users: List<WalletPickerUser>, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf<WalletPickerUser?>(null) }
    var rateText by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val rate = rateText.replace(",", ".").toDoubleOrNull()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Индивидуальная ставка ₽/час", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            F3UserDropdown(users, selected) { selected = it }
            OutlinedTextField(value = rateText, onValueChange = { rateText = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Ставка, ₽/час") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            error?.let { ErrorLine(it) }
            Button(onClick = {
                val u = selected ?: return@Button
                val r = rate ?: return@Button
                saving = true; error = null
                vm.setUserHourRate(u.id, r) { ok, msg -> saving = false; if (ok) onDismiss() else error = msg }
            }, enabled = selected != null && rate != null && rate > 0 && !saving, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent)) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("Сохранить", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================
// Ф3 — НАЧИСЛЕНИЯ (по человеку / периоду)
// ============================================================
@Composable
fun WalletF3EarningsScreen(navController: NavController) {
    val vm: WalletF3ViewModel = hiltViewModel()
    val st by vm.earnings.collectAsState()
    val users by vm.users.collectAsState()
    val bc = MaterialTheme.belsiColors

    val now = remember { Calendar.getInstance() }
    var year by remember { mutableStateOf(st.year.takeIf { it > 0 } ?: now.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(st.month.takeIf { it > 0 } ?: (now.get(Calendar.MONTH) + 1)) }
    var selectedUser by remember { mutableStateOf<WalletPickerUser?>(null) }
    var editTarget by remember { mutableStateOf<F3EarningDto?>(null) }

    LaunchedEffect(Unit) { vm.loadUsers() }
    LaunchedEffect(selectedUser, year, month) {
        selectedUser?.let { vm.selectEarningsTarget(it.id, it.name, year, month) }
    }

    F3Scaffold("Начисления по человеку", navController) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            F3UserDropdown(users, selectedUser) { selectedUser = it }
            F3PeriodSelector(year, month,
                onPrev = { if (month == 1) { month = 12; year -= 1 } else month -= 1 },
                onNext = { if (month == 12) { month = 1; year += 1 } else month += 1 })

            if (selectedUser == null) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    Text("Выберите монтажника", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                F3StatCard("Одобрено", WalletBrand.rub(st.approvedSum), bc.success, Modifier.weight(1f))
                F3StatCard("Черновики · ${st.draftCount}", WalletBrand.rub(st.draftSum), bc.warning, Modifier.weight(1f))
            }

            Button(onClick = {
                vm.recomputeEarnings { ok, _, unclassified -> /* список покажется ниже из st */ }
            }, enabled = !st.recomputing, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent)) {
                if (st.recomputing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Пересчитать по часам", fontWeight = FontWeight.Bold) }
            }

            st.error?.let { ErrorLine(it) }

            // дни без формата — куратор должен выбрать часы/метры
            if (st.unclassifiedDays.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = bc.warningContainer), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HelpOutline, null, tint = bc.onWarningContainer, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Дни без формата · ${st.unclassifiedDays.size}", fontWeight = FontWeight.Bold, color = bc.onWarningContainer)
                        }
                        Text("Есть смена + одобренное фото, но не выбран формат расчёта. Назначьте часы или метры.", style = MaterialTheme.typography.bodySmall, color = bc.onWarningContainer.copy(alpha = 0.85f))
                        st.unclassifiedDays.forEach { d ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(WalletBrand.day(d), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = bc.onWarningContainer)
                                TextButton(onClick = { vm.setDayFormat(d, "hours") }) { Text("Часы") }
                                TextButton(onClick = { vm.setDayFormat(d, "meters") }) { Text("Метры") }
                            }
                        }
                    }
                }
            }

            val visible = st.earnings.filter { it.status != "void" }
            if (visible.isEmpty() && !st.loading) {
                Text("Нет начислений за период. Нажмите «Пересчитать» или примите кабинет.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
            }
            SectionLabel("Начисления по дням · ${visible.size}")
            visible.forEach { e ->
                EarningRow(e, busy = st.busyEarningId == e.id,
                    onEdit = { editTarget = e },
                    onApprove = { vm.approveEarning(e.id) },
                    onVoid = { vm.voidEarning(e.id) })
            }
        }
    }

    editTarget?.let { e -> F3EarningEditSheet(e, vm) { editTarget = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EarningRow(e: F3EarningDto, busy: Boolean, onEdit: () -> Unit, onApprove: () -> Unit, onVoid: () -> Unit) {
    val bc = MaterialTheme.belsiColors
    val statusColor = when (e.status) {
        "approved" -> bc.success; "void" -> MaterialTheme.colorScheme.error; else -> bc.warning
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background((if (e.isHours) WalletBrand.accent else bc.success).copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                    Icon(if (e.isHours) Icons.Default.Schedule else Icons.Default.Straighten, null, tint = if (e.isHours) WalletBrand.accent else bc.success, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(WalletBrand.day(e.workDate) + " · " + e.qtyLabel, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${e.basisLabel} · ${e.sourceLabel}" + (e.rate?.let { " · ${f3num(it)} ₽" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(WalletBrand.rub(e.amountValue), fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = statusColor.copy(alpha = 0.13f), shape = RoundedCornerShape(99.dp)) {
                    Text(e.statusDisplay, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                }
                if (e.isInPayout) {
                    Spacer(Modifier.width(6.dp))
                    Surface(color = WalletBrand.accent.copy(alpha = 0.1f), shape = RoundedCornerShape(99.dp)) {
                        Text("в выплате", color = WalletBrand.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else if (e.status == "draft") {
                    TextButton(onClick = onEdit) { Text("Правка") }
                    TextButton(onClick = onVoid, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Отмена") }
                    Button(onClick = onApprove, colors = ButtonDefaults.buttonColors(containerColor = bc.success), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("Одобрить", fontWeight = FontWeight.Bold)
                    }
                } else if (e.status == "approved" && !e.isInPayout) {
                    TextButton(onClick = onVoid, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Сторно") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun F3EarningEditSheet(e: F3EarningDto, vm: WalletF3ViewModel, onDismiss: () -> Unit) {
    var basis by remember { mutableStateOf(e.basis) }
    var qtyText by remember { mutableStateOf((if (basis == "hours") e.hours else e.meters)?.let { f3num(it) } ?: "") }
    var rateText by remember { mutableStateOf(e.rate?.let { f3num(it) } ?: "") }
    var amountText by remember { mutableStateOf(e.amount?.let { f3num(it) } ?: "") }
    var comment by remember { mutableStateOf(e.comment ?: "") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Правка начисления · ${WalletBrand.day(e.workDate)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = basis == "hours", onClick = { basis = "hours" }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Часы") }
                SegmentedButton(selected = basis == "meters", onClick = { basis = "meters" }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Метры") }
            }
            OutlinedTextField(value = qtyText, onValueChange = { qtyText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, label = { Text(if (basis == "hours") "Часы" else "Погонные метры") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = rateText, onValueChange = { rateText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, label = { Text("Ставка, ₽") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = amountText, onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, label = { Text("Сумма (override, опц.)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Комментарий") }, minLines = 1, maxLines = 3, modifier = Modifier.fillMaxWidth())
            error?.let { ErrorLine(it) }
            Button(onClick = {
                val qty = qtyText.replace(",", ".").toDoubleOrNull()
                val rate = rateText.replace(",", ".").toDoubleOrNull()
                val amount = amountText.replace(",", ".").toDoubleOrNull()
                saving = true; error = null
                vm.editEarning(
                    e.id, basis,
                    hours = if (basis == "hours") qty else null,
                    meters = if (basis == "meters") qty else null,
                    rate = rate, amount = amount, comment = comment.ifBlank { null },
                ) { ok, msg -> saving = false; if (ok) onDismiss() else error = msg }
            }, enabled = !saving, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent)) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("Сохранить", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================
// Ф3 — ВЫПЛАТЫ (Rocket Work)
// ============================================================
@Composable
fun WalletF3PayoutsScreen(navController: NavController) {
    val vm: WalletF3ViewModel = hiltViewModel()
    val st by vm.payouts.collectAsState()
    val users by vm.users.collectAsState()
    val bc = MaterialTheme.belsiColors

    val now = remember { Calendar.getInstance() }
    var year by remember { mutableStateOf(now.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(now.get(Calendar.MONTH) + 1) }
    var selectedUser by remember { mutableStateOf<WalletPickerUser?>(null) }
    var showAssemble by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadUsers() }
    LaunchedEffect(selectedUser, year, month) {
        selectedUser?.let { vm.selectPayoutsTarget(it.id, it.name, year, month) }
        vm.setPayoutsPeriod(year, month)
    }

    F3Scaffold("Выплаты Rocket Work", navController) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            F3UserDropdown(users, selectedUser) { selectedUser = it }
            F3PeriodSelector(year, month,
                onPrev = { if (month == 1) { month = 12; year -= 1 } else month -= 1 },
                onNext = { if (month == 12) { month = 1; year += 1 } else month += 1 })

            if (selectedUser == null) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { Text("Выберите монтажника", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                return@Column
            }

            Button(onClick = { showAssemble = true }, enabled = !st.busy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent)) {
                Icon(Icons.Default.PlaylistAddCheck, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Собрать выплату", fontWeight = FontWeight.Bold)
            }
            st.error?.let { ErrorLine(it) }

            // активный собранный/детализированный черновик
            st.draft?.let { p -> PayoutDetailCard(p, st.busy, vm) }

            SectionLabel("Все выплаты · ${st.payouts.size}")
            if (st.payouts.isEmpty() && !st.loading) {
                Text("Выплат пока нет.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            st.payouts.forEach { p -> PayoutListRow(p) { vm.openPayout(p.id) } }
        }
    }

    if (showAssemble) {
        F3AssembleSheet(year, month, busy = st.busy,
            onAssemble = { kind -> vm.assemblePayout(kind) { ok, _ -> if (ok) showAssemble = false } },
            onDismiss = { showAssemble = false })
    }
}

@Composable
private fun PayoutListRow(p: F3PayoutDto, onTap: () -> Unit) {
    val bc = MaterialTheme.belsiColors
    val color = when (p.status) {
        "paid" -> bc.success; "failed", "canceled" -> MaterialTheme.colorScheme.error
        "draft" -> bc.warning; else -> WalletBrand.accent
    }
    Card(onClick = onTap, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text((p.period ?: p.kindLabel), fontWeight = FontWeight.Bold)
                Text(p.statusDisplay, style = MaterialTheme.typography.bodySmall, color = color)
            }
            Text(WalletBrand.rub(p.gross), fontWeight = FontWeight.Black, fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PayoutDetailCard(p: F3PayoutDto, busy: Boolean, vm: WalletF3ViewModel) {
    val bc = MaterialTheme.belsiColors
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.period ?: p.kindLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(p.statusDisplay, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = WalletBrand.accent.copy(alpha = 0.1f), shape = RoundedCornerShape(99.dp)) {
                    Text(p.kindLabel, color = WalletBrand.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
                }
            }

            // разбивка суммы: работа + бонус − штраф = брутто; − налог − комиссия = на карту
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    amountLine("Работа (часы + метры)", p.work, false)
                    if (p.bonus > 0) amountLine("Бонус", p.bonus, false, bc.success, prefix = "+")
                    if (p.penalty > 0) amountLine("Штраф", p.penalty, false, MaterialTheme.colorScheme.error, prefix = "−")
                    HorizontalDivider()
                    amountLine("Брутто (в чек ФНС)", p.gross, true)
                    if (p.tax > 0) amountLine("Налог НПД (за монтажника)", p.tax, false, MaterialTheme.colorScheme.onSurfaceVariant, prefix = "−")
                    if (p.commission > 0) amountLine("Комиссия посредника", p.commission, false, MaterialTheme.colorScheme.onSurfaceVariant, prefix = "−")
                    HorizontalDivider()
                    amountLine("На карту монтажнику", p.net, true, bc.success)
                }
            }

            // гейты (НПД validated / договор / ИНН / баланс)
            if (!p.gatesOk) {
                Card(colors = CardDefaults.cardColors(containerColor = bc.warningContainer), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Block, null, tint = bc.onWarningContainer, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp)); Text("Нельзя выплатить — проверки:", fontWeight = FontWeight.Bold, color = bc.onWarningContainer)
                        }
                        p.gates.forEach { g -> Text("• $g", style = MaterialTheme.typography.bodySmall, color = bc.onWarningContainer) }
                    }
                }
            }

            // чек ФНС, если есть
            if (p.hasReceipt) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, tint = bc.success, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp)); Text("Чек ФНС получен", color = bc.success, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            p.errorMessage?.let { ErrorLine(it) }

            // действия по статусу
            if (busy) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (p.status) {
                        "draft" -> {
                            Button(onClick = { vm.approvePayout(p.id) { _, _ -> } }, enabled = p.gatesOk, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = bc.success)) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Одобрить выплату", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(onClick = { vm.cancelPayout(p.id) { _, _ -> } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Отменить") }
                        }
                        "approved" -> {
                            Button(onClick = { vm.sendPayout(p.id) { _, _ -> } }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent)) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Отправить в Rocket Work", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(onClick = { vm.markPayoutPaid(p.id) { _, _ -> } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Отметить выплаченным (вручную)")
                            }
                            OutlinedButton(onClick = { vm.cancelPayout(p.id) { _, _ -> } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Отменить") }
                        }
                        "sent", "paying" -> {
                            if (p.rwTaskId.isNullOrBlank()) {
                                OutlinedButton(onClick = { vm.markPayoutPaid(p.id) { _, _ -> } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                    Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Отметить выплаченным")
                                }
                            } else {
                                Text("Ожидаем подтверждение из Rocket Work (статус придёт вебхуком).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        "paid" -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, null, tint = bc.success); Spacer(Modifier.width(8.dp)); Text("Выплачено", color = bc.success, fontWeight = FontWeight.Bold)
                        }
                        "failed" -> {
                            Button(onClick = { vm.cancelPayout(p.id) { _, _ -> } }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Отменить выплату", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun amountLine(label: String, value: Double, bold: Boolean, color: Color = Color.Unspecified, prefix: String = "") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = if (bold) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color)
        Text("$prefix${WalletBrand.rub(value)}", fontWeight = if (bold) FontWeight.Black else FontWeight.SemiBold,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color, fontSize = if (bold) 16.sp else 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun F3AssembleSheet(year: Int, month: Int, busy: Boolean, onAssemble: (String) -> Unit, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf("monthly") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Собрать выплату", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Соберём одобренные начисления + бонусы/штрафы в один черновик выплаты.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = kind == "monthly", onClick = { kind = "monthly" }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Месячная") }
                SegmentedButton(selected = kind == "on_demand", onClick = { kind = "on_demand" }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("По запросу") }
            }
            Text(if (kind == "monthly") "За ${monthName(month)} $year — все одобренные начисления периода." else "По запросу — все одобренные несвязанные начисления.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onAssemble(kind) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent)) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("Собрать", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================
// Ф3 — БАЛАНС КОМПАНИИ + ПРОГНОЗ
// ============================================================
@Composable
fun WalletF3BalanceScreen(navController: NavController) {
    val vm: WalletF3ViewModel = hiltViewModel()
    val st by vm.balance.collectAsState()
    val bc = MaterialTheme.belsiColors

    val now = remember { Calendar.getInstance() }
    var year by remember { mutableStateOf(now.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(now.get(Calendar.MONTH) + 1) }
    var showThreshold by remember { mutableStateOf(false) }

    LaunchedEffect(year, month) { vm.loadBalance(year, month) }

    F3Scaffold("Счёт компании", navController) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val b = st.balance
            CompanyBalanceMini(b, st.forecast) { }

            st.error?.let { ErrorLine(it) }

            if (b?.isLow == true) {
                Card(colors = CardDefaults.cardColors(containerColor = bc.warningContainer), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WarningAmber, null, tint = bc.onWarningContainer, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Низкий остаток", fontWeight = FontWeight.Bold, color = bc.onWarningContainer)
                            Text("Порог ${WalletBrand.rub(b.lowThreshold)}. Пополните счёт перед выплатами.", style = MaterialTheme.typography.bodySmall, color = bc.onWarningContainer.copy(alpha = 0.85f))
                        }
                    }
                }
            }

            F3PeriodSelector(year, month,
                onPrev = { if (month == 1) { month = 12; year -= 1 } else month -= 1 },
                onNext = { if (month == 12) { month = 1; year += 1 } else month += 1 })

            // прогноз «нужно X / на счету Y»
            st.forecast?.let { f ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ПРОГНОЗ ЗА ${monthName(f.month).uppercase()} ${f.year}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        amountLine("Нужно на выплаты", f.needed, false)
                        amountLine("На счету", f.balance, false)
                        HorizontalDivider()
                        if (f.sufficient) Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = bc.success, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text("Средств достаточно", color = bc.success, fontWeight = FontWeight.Bold)
                        } else amountLine("Не хватает", f.shortfall, true, MaterialTheme.colorScheme.error)
                    }
                }
            }

            // реквизиты счёта
            if (!b?.requisites.isNullOrEmpty()) {
                SectionLabel("Реквизиты")
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp)) {
                    Column {
                        b!!.requisites.entries.forEachIndexed { i, (k, v) ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(k, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(v, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { showThreshold = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Tune, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Порог")
                }
                Button(onClick = { vm.syncBalance(year, month) { _, _ -> } }, enabled = b?.payoutsEnabled == true && !st.syncing,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent)) {
                    if (st.syncing) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Sync, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Синхр.") }
                }
            }
            if (b?.payoutsEnabled == false) {
                Text("Rocket Work выключен (PAYOUTS_ENABLED=0). Синхронизация счёта и реальные выплаты недоступны — доступна ручная отметка «выплачено».",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showThreshold) {
        F3ThresholdSheet(current = st.balance?.lowThreshold ?: 0.0, saving = st.savingThreshold,
            onSave = { t -> vm.setLowThreshold(t, year, month) { ok, _ -> if (ok) showThreshold = false } },
            onDismiss = { showThreshold = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun F3ThresholdSheet(current: Double, saving: Boolean, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) f3num(current) else "") }
    val value = text.replace(",", ".").toDoubleOrNull()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Порог низкого остатка", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Если остаток на счёте упадёт ниже — покажем предупреждение перед выплатами.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = text, onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Порог, ₽") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Button(onClick = { value?.let(onSave) }, enabled = value != null && !saving, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent)) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("Сохранить", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================
// Ф3 — ШТРАФ / БОНУС (мультивыбор) — переиспользуемая шторка
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun F3AdjustmentBatchSheet(vm: WalletF3ViewModel, year: Int, month: Int, onDismiss: () -> Unit) {
    val users by vm.users.collectAsState()
    val selected = remember { mutableStateListOf<String>() }
    var isBonus by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val amount = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
    val canSave = selected.isNotEmpty() && amount > 0 && reason.trim().length >= 3

    LaunchedEffect(Unit) { vm.loadUsers() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Штраф / Бонус", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("За ${monthName(month)} $year. Можно выбрать сразу нескольких. Бонус добавится в выплату и чек, штраф — вычтется до суммы.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = isBonus, onClick = { isBonus = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Бонус") }
                SegmentedButton(selected = !isBonus, onClick = { isBonus = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Штраф") }
            }
            OutlinedTextField(value = amountText, onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Сумма, ₽") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Причина") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())

            SectionLabel("Кому · выбрано ${selected.size}")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp)) {
                Column {
                    users.forEachIndexed { i, u ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                        val on = u.id in selected
                        Row(Modifier.fillMaxWidth().clickable { if (on) selected.remove(u.id) else selected.add(u.id) }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (on) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (on) WalletBrand.accent else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            WalletAvatar(u.initials, 34.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(u.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(u.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            error?.let { ErrorLine(it) }
            Button(onClick = {
                saving = true; error = null
                vm.createAdjustmentBatch(selected.toList(), isBonus, amount, reason.trim(), year, month) { ok, msg -> saving = false; if (ok) onDismiss() else error = msg }
            }, enabled = canSave && !saving, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent)) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text(if (isBonus) "Начислить бонус" else "Начислить штраф", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================
// Ф3 — ПРИЁМКА КАБИНЕТА (метры) — переиспользуемая шторка
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun F3AcceptMetersSheet(
    vm: WalletF3ViewModel, cabinetId: String, suggestedMeters: Double? = null,
    recipients: List<WalletPickerUser> = emptyList(), onDone: () -> Unit, onDismiss: () -> Unit,
) {
    var metersText by remember { mutableStateOf(suggestedMeters?.let { f3num(it) } ?: "") }
    val excluded = remember { mutableStateListOf<String>() }
    var markDone by remember { mutableStateOf(true) }
    var comment by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val meters = metersText.replace(",", ".").toDoubleOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Принять кабинет → начислить", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Метро-начисление авторам фото (или выбранным). Полная ставка каждому.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = metersText, onValueChange = { metersText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, label = { Text("Погонные метры (опц. — иначе авто)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())

            if (recipients.isNotEmpty()) {
                SectionLabel("Получатели")
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp)) {
                    Column {
                        recipients.forEachIndexed { i, u ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                            val on = u.id !in excluded
                            Row(Modifier.fillMaxWidth().clickable { if (on) excluded.add(u.id) else excluded.remove(u.id) }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (on) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (on) MaterialTheme.belsiColors.success else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                Text(u.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Комментарий (опц.)") }, minLines = 1, maxLines = 3, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = markDone, onCheckedChange = { markDone = it })
                Text("Отметить кабинет как «готов»", style = MaterialTheme.typography.bodyMedium)
            }
            error?.let { ErrorLine(it) }
            Button(onClick = {
                val ids = recipients.map { it.id }.filter { it !in excluded }.ifEmpty { null }
                saving = true; error = null
                vm.acceptCabinetMeters(cabinetId, meters, ids, null, comment.ifBlank { null }, markDone) { ok, msg -> saving = false; if (ok) onDone() else error = msg }
            }, enabled = !saving, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.belsiColors.success)) {
                if (saving) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                else { Icon(Icons.Default.Check, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Принять и начислить", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ============================================================
// Ф3 — МОНТАЖНИК: «За сегодня сделал X п.м.» (плашка при завершении смены)
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayMetersSheet(
    objectId: String? = null, cabinetId: String? = null, onSubmitted: () -> Unit = {}, onDismiss: () -> Unit,
) {
    val vm: WalletF3ViewModel = hiltViewModel()
    var metersText by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    val meters = metersText.replace(",", ".").toDoubleOrNull() ?: 0.0
    val today = remember { isoToday() }
    val bc = MaterialTheme.belsiColors

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (done) {
                Icon(Icons.Default.VerifiedUser, null, tint = bc.success, modifier = Modifier.size(50.dp))
                Text("Записано", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Куратор подтвердит и начислит. Спасибо за отчёт!", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent)) { Text("Готово", fontWeight = FontWeight.Bold) }
            } else {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(WalletBrand.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Straighten, null, tint = WalletBrand.accent, modifier = Modifier.size(28.dp))
                }
                Text("За сегодня сделал…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Укажи погонные метры за смену — куратор подтвердит и начислит.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                OutlinedTextField(value = metersText, onValueChange = { metersText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    placeholder = { Text("0") }, singleLine = true, suffix = { Text("п.м.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Комментарий (опц.)") }, minLines = 1, maxLines = 3, modifier = Modifier.fillMaxWidth())
                error?.let { ErrorLine(it) }
                Button(onClick = {
                    saving = true; error = null
                    vm.submitDayMeters(today, meters, objectId, cabinetId, comment.ifBlank { null }) { ok, msg ->
                        saving = false; if (ok) { done = true; onSubmitted() } else error = msg
                    }
                }, enabled = !saving && meters > 0, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent)) {
                    if (saving) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp) else Text("Отправить отчёт", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun isoToday(): String {
    val c = Calendar.getInstance()
    return String.format(java.util.Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}
