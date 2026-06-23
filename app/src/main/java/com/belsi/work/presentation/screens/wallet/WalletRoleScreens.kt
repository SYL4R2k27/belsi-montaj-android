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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.models.AccrualRecipientDto
import com.belsi.work.data.models.CuratorWithdrawalDto
import com.belsi.work.data.models.ForemanTeamCabinetDto
import com.belsi.work.data.models.ForemanTeamMemberDto
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.theme.belsiColors

// ============================================================
// Аватар с инициалами
// ============================================================
@Composable
fun WalletAvatar(initials: String, size: androidx.compose.ui.unit.Dp = 38.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(99.dp)).background(WalletBrand.heroGradient), contentAlignment = Alignment.Center) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.34f).sp)
    }
}

// ============================================================
// БРИГАДИР: Мой / Команда
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForemanWalletContent(vm: WalletViewModel, ui: WalletUiState, navController: NavController) {
    var seg by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SegmentedButton(selected = seg == 0, onClick = { seg = 0 }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Мой") }
            SegmentedButton(selected = seg == 1, onClick = { seg = 1; if (ui.team == null) vm.loadTeam() }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Команда") }
        }
        // «Мой» = личный кошелёк бригадира с той же формой налог-данных и приёмом
        // Договора самозанятого, что у монтажника (привязка к contract_type, не к роли).
        if (seg == 0) InstallerWalletContent(vm, ui, navController) else ForemanTeamContent(vm, ui)
    }
}

@Composable
fun ForemanTeamContent(vm: WalletViewModel, ui: WalletUiState) {
    val bc = MaterialTheme.belsiColors
    val team = ui.team
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        when {
            team != null -> {
                // hero команды
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(WalletBrand.heroGradient).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Groups, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Начислено команде", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Surface(color = Color.White.copy(alpha = 0.18f), shape = RoundedCornerShape(99.dp)) {
                            Text("Бригада · ${team.members.size}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                    Text(WalletBrand.rub(team.teamEarned), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        teamMetric("Кабинетов", "${team.cabinetsCount}", Modifier.weight(1f))
                        teamMetric("На одобрении", WalletBrand.rub(team.teamPending), Modifier.weight(1f))
                        teamMetric("Выплачено", WalletBrand.rub(team.teamPaid), Modifier.weight(1f))
                    }
                }
                ExplainCard("Только просмотр", "Видишь начисления бригады — кто сколько и за что. Выплаты одобряет куратор.", Icons.Default.Visibility)

                if (team.members.isNotEmpty()) {
                    SectionLabel("Команда · кто сколько заработал")
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(22.dp)) {
                        team.members.forEachIndexed { i, m -> if (i > 0) HorizontalDivider(Modifier.padding(start = 62.dp)); ForemanMemberRow(m) }
                    }
                }
                if (team.cabinets.isNotEmpty()) {
                    SectionLabel("По кабинетам")
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(22.dp)) {
                        team.cabinets.forEachIndexed { i, c -> if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp)); cabinetRow(c, bc.success) }
                    }
                }
            }
            ui.teamFailed -> Column(Modifier.fillMaxWidth().padding(vertical = 50.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.GroupOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                Text("Кошелёк команды появится после обновления сервера", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = { vm.loadTeam() }) { Text("Обновить") }
            }
            else -> Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

@Composable
private fun teamMetric(k: String, v: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 9.dp)) {
        Text(k, color = Color.White.copy(alpha = 0.82f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
        Text(v, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ForemanMemberRow(m: ForemanTeamMemberDto) {
    val bc = MaterialTheme.belsiColors
    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        WalletAvatar(m.initials)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.userName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${m.cabinetsCount} каб. · ${numFmt(m.metersTotal)} п.м.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("+${WalletBrand.rub(m.earned)}", fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1)
            val (c, bg) = when (m.statusLabel) {
                "выплачено" -> bc.success to bc.success.copy(alpha = 0.13f)
                "на одобрении" -> bc.onWarningContainer to bc.warningContainer
                else -> WalletBrand.accent to WalletBrand.accent.copy(alpha = 0.1f)
            }
            Surface(color = bg, shape = RoundedCornerShape(99.dp), modifier = Modifier.padding(top = 3.dp)) {
                Text(m.statusLabel, color = c, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun cabinetRow(c: ForemanTeamCabinetDto, green: Color) {
    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(green.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MeetingRoom, null, tint = green, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Кабинет ${c.cabinetNumber ?: "—"} · ${numFmt(c.meters)} п.м.", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("${c.recipientsCount} монтажника", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(WalletBrand.rub(c.total), color = green, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
    }
}

private fun numFmt(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)

// ============================================================
// КУРАТОР: очередь + управление
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratorWalletContent(vm: WalletViewModel, ui: WalletUiState, navController: NavController) {
    var approveTarget by remember { mutableStateOf<CuratorWithdrawalDto?>(null) }
    var showRates by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ovCard("На одобрении", "${ui.curatorPending.size}", MaterialTheme.belsiColors.warning, Modifier.weight(1f))
            ovCard("К выплате", WalletBrand.rub(ui.toPayoutSum), WalletBrand.accent, Modifier.weight(1f))
        }
        ui.actionError?.let { ErrorLine(it) }
        ui.error?.let { ErrorLine(it) }

        SectionLabel("Выводы на одобрении · ${ui.curatorQueueActive.size}")
        if (ui.curatorQueueActive.isEmpty() && !ui.loading) {
            Text("Очередь пуста", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium)
        }
        ui.curatorQueueActive.forEach { w ->
            CuratorWithdrawalCard(w,
                onApprove = { vm.approve(w.id) },
                onPaid = { vm.markPaid(w.id) },
                onReject = { vm.reject(w.id) },
                onTap = { approveTarget = w })
        }

        SectionLabel("Управление")
        // Ф3 — единый хаб расчёта (часы/метры + выплаты Rocket Work + баланс компании)
        mgmtRow(Icons.Default.Calculate, WalletBrand.brandBottom, "Расчёт и выплаты", "Часы/метры · собрать → одобрить → Rocket Work + чек ФНС · счёт компании") { navController.navigate(AppRoute.WalletF3Hub.route) }
        mgmtRow(Icons.Default.ReceiptLong, WalletBrand.accent, "Месячные акты", "Собрать → подписать → выплата + чек ФНС") { navController.navigate(AppRoute.WalletActs.route) }
        mgmtRow(Icons.Default.CurrencyRuble, WalletBrand.accent, "Ставка ₽/п.м.", "Глобально + индивидуальные") { showRates = true }
        mgmtRow(Icons.Default.Warning, MaterialTheme.colorScheme.error, "Штраф / Бонус", "Начислить корректировку человеку") { showAdjust = true }

        Text("Начисление за принятый кабинет запускается из карточки кабинета: Объект → кабинет → «Принять → начислить».",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }

    approveTarget?.let { w -> CuratorApproveSheet(w, vm) { approveTarget = null } }
    if (showRates) CuratorRatesSheet(vm, ui) { showRates = false }
    if (showAdjust) CuratorAdjustmentSheet(vm, ui.queuePersons) { showAdjust = false }
}

@Composable
private fun ovCard(k: String, v: String, tint: Color, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column(Modifier.padding(13.dp)) {
            Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(v, color = tint, fontSize = 19.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun mgmtRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String, sub: String, onClick: () -> Unit) {
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

@Composable
private fun CuratorWithdrawalCard(w: CuratorWithdrawalDto, onApprove: () -> Unit, onPaid: () -> Unit, onReject: () -> Unit, onTap: () -> Unit) {
    val bc = MaterialTheme.belsiColors
    Card(onClick = onTap, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WalletAvatar(w.initials, 40.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(w.userName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOf("самозанятый", (w.paymentDetails ?: w.paymentMethod)?.let { "на $it" }).filterNotNull().joinToString(" · ")
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(WalletBrand.rub(w.amount), fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Send, null, tint = WalletBrand.accent, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
                Text("После одобрения → Rocket Work → карта + чек ФНС", color = WalletBrand.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                if (w.status == "pending") {
                    Button(onClick = onApprove, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = bc.success), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Одобрить", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(onClick = onPaid, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = bc.success), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.VerifiedUser, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Выплачено", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Отклонить", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================
// КУРАТОР: лист одобрения (посредник Rocket Work)
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratorApproveSheet(w: CuratorWithdrawalDto, vm: WalletViewModel, onDismiss: () -> Unit) {
    val bc = MaterialTheme.belsiColors
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (w.status == "approved") "Отметить выплаченным" else "Одобрить выплату", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("Деньги уйдут через посредника на карту самозанятого", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    WalletAvatar(w.initials, 54.dp)
                    Text(w.userName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("самозанятый · на ${w.paymentDetails ?: w.paymentMethod ?: "карту"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(WalletBrand.rub(w.amount), fontSize = 34.sp, fontWeight = FontWeight.Black)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    flowStep(Icons.Default.Check, WalletBrand.accent, "Ты одобряешь", "Подтверждаешь сумму и получателя", false)
                    flowStep(Icons.AutoMirrored.Filled.Send, WalletBrand.accent, "Rocket Work переводит на карту", "Посредник под ЦБ · СБП 24/7 · BELSI карты не хранит", false)
                    flowStep(Icons.Default.VerifiedUser, bc.success, "Чек ФНС — автоматически", "Налог самозанятого учитывается посредником", true)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = WalletBrand.accent.copy(alpha = 0.1f)), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = WalletBrand.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Text("Выплата через Rocket Work", fontWeight = FontWeight.Bold, color = WalletBrand.brandBottom, style = MaterialTheme.typography.labelLarge)
                        Text("Статус самозанятого проверяется автоматически перед переводом", style = MaterialTheme.typography.bodySmall, color = WalletBrand.accent)
                    }
                }
            }

            Button(onClick = {
                if (w.status == "approved") vm.markPaid(w.id) else vm.approve(w.id); onDismiss()
            }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = bc.success)) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(8.dp))
                Text(if (w.status == "approved") "Отметить выплаченным" else "Одобрить и отправить на выплату", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { vm.reject(w.id); onDismiss() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("Отклонить заявку", fontWeight = FontWeight.SemiBold)
            }
            Text("Реальные деньги двигает посредник. Отменить после одобрения нельзя.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun flowStep(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String, sub: String, last: Boolean) {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
            }
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Column(Modifier.padding(bottom = if (last) 0.dp else 12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============================================================
// КУРАТОР: ставки
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratorRatesSheet(vm: WalletViewModel, ui: WalletUiState, onDismiss: () -> Unit) {
    var globalText by remember(ui.rates) { mutableStateOf(ui.rates?.globalRate?.toInt()?.toString() ?: "1000") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Ставки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ГЛОБАЛЬНАЯ СТАВКА ₽/П.М.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = globalText, onValueChange = { globalText = it.filter(Char::isDigit) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black))
                        Button(onClick = {
                            val r = globalText.toDoubleOrNull()
                            if (r != null && r > 0) { saving = true; error = null; vm.setGlobalRate(r) { ok, msg -> saving = false; if (!ok) error = msg } }
                        }, enabled = !saving && globalText.toDoubleOrNull() != null, colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent), shape = RoundedCornerShape(99.dp)) {
                            if (saving) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Text("Сохранить", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            error?.let { ErrorLine(it) }
            val ov = ui.rates?.overrides.orEmpty()
            if (ov.isNotEmpty()) {
                Text("ИНДИВИДУАЛЬНЫЕ · ${ov.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp)) {
                    ov.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.userName, fontWeight = FontWeight.SemiBold)
                                r.userPhone?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            Text(WalletBrand.rub(r.rate), color = WalletBrand.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Text("Индивидуальных ставок нет — у всех глобальная.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ============================================================
// КУРАТОР: штраф / бонус
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratorAdjustmentSheet(vm: WalletViewModel, persons: List<Triple<String, String, String?>>, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf<Triple<String, String, String?>?>(null) }
    var isBonus by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val amount = amountText.toDoubleOrNull() ?: 0.0
    val canSave = selected != null && amount > 0 && reason.trim().length >= 3

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Штраф / Бонус", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (persons.isEmpty()) {
                Text("Нет получателей в очереди. Штраф/бонус можно начислить из карточки человека.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
                    OutlinedTextField(value = selected?.second ?: "— выбрать —", onValueChange = {}, readOnly = true, label = { Text("Кому") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                    ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        persons.forEach { p -> DropdownMenuItem(text = { Text(p.second) }, onClick = { selected = p; menuOpen = false }) }
                    }
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !isBonus, onClick = { isBonus = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Штраф") }
                SegmentedButton(selected = isBonus, onClick = { isBonus = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Бонус") }
            }
            OutlinedTextField(value = amountText, onValueChange = { amountText = it.filter(Char::isDigit) }, label = { Text("Сумма, ₽") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Причина") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
            error?.let { ErrorLine(it) }
            Button(onClick = {
                val p = selected ?: return@Button
                saving = true; error = null
                vm.adjust(p.first, isBonus, amount, reason.trim()) { ok, msg -> saving = false; if (ok) onDismiss() else error = msg }
            }, enabled = canSave && !saving, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent)) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("Начислить", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================
// КУРАТОР: Принять кабинет → начислить (отдельный экран по cabinetId)
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratorAccrueScreen(cabinetId: String, navController: NavController) {
    val vm: WalletViewModel = hiltViewModel()
    val st by vm.accrual.collectAsState()
    val bc = MaterialTheme.belsiColors

    var metersText by remember { mutableStateOf("") }
    val excluded = remember { mutableStateListOf<String>() }
    var showForce by remember { mutableStateOf(false) }

    LaunchedEffect(cabinetId) { vm.loadAccrual(cabinetId) }
    LaunchedEffect(st.info) { if (metersText.isEmpty()) st.info?.resolvedMeters?.let { metersText = numFmt(it) } }

    val info = st.info
    val rate = info?.globalRate ?: 1000.0
    val meters = metersText.replace(",", ".").toDoubleOrNull() ?: info?.resolvedMeters ?: 0.0
    val recipients = info?.recipients.orEmpty()
    val active = recipients.filter { it.userId !in excluded }
    val fund = active.sumOf { meters * it.rate }
    val anyAlready = active.any { it.alreadyAccrued }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Начислить за кабинет", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                st.done -> Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Default.VerifiedUser, null, tint = bc.success, modifier = Modifier.size(54.dp))
                    Text("Начислено", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Фонд ${WalletBrand.rub(fund)} распределён по ${active.size} монтажникам", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Button(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent)) { Text("Готово", fontWeight = FontWeight.Bold) }
                }
                info != null -> {
                    Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(22.dp)).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFAAB4C4), Color(0xFF7C8A9E))))) {
                        Surface(color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(99.dp), modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                            Text("Фотоотчёт монтажника", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(22.dp)) {
                        Column {
                            InfoRow(Icons.Default.MeetingRoom, "Кабинет", "№ ${info.cabinet.cabinetNumber ?: "—"}", info.cabinet.objectName)
                            HorizontalDivider(Modifier.padding(start = 52.dp))
                            Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Default.Straighten, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)) }
                                Spacer(Modifier.width(13.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(info.proposals.firstOrNull()?.let { "Монтажник указал ${numFmt(it.meters)}" } ?: "Погонные метры", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedTextField(value = metersText, onValueChange = { metersText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                                }
                                Text("п.м.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                            }
                            HorizontalDivider(Modifier.padding(start = 52.dp))
                            InfoRow(Icons.Default.CurrencyRuble, "Ставка", "${rate.toInt()} ₽/п.м.")
                        }
                    }

                    SectionLabel("Кому начислить · бригада на объекте")
                    if (recipients.isEmpty()) Text("Нет активных монтажников на объекте.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(22.dp)) {
                        recipients.forEachIndexed { i, r ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 62.dp))
                            recipientRow(r, r.userId !in excluded, meters) { on -> if (on) excluded.remove(r.userId) else excluded.add(r.userId) }
                        }
                    }
                    ExplainCard("Фонд кабинета — ${WalletBrand.rub(fund)}", "Полная ставка каждому из ${active.size} · сумма не делится. Сними галочку, чтобы исключить.", Icons.Default.Groups)
                    st.error?.let { ErrorLine(it) }

                    Button(onClick = {
                        if (anyAlready) showForce = true
                        else vm.accrue(cabinetId, if (meters > 0) meters else null, if (active.size == recipients.size) null else active.map { it.userId }, false) { _, _ -> }
                    }, enabled = !st.submitting && meters > 0 && active.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = bc.success)) {
                        if (st.submitting) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        else { Icon(Icons.Default.Check, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Принять кабинет и начислить", fontWeight = FontWeight.Bold) }
                    }
                    Text("Это начисление, не выплата. Монтажник выводит сам — ты одобряешь вывод.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                st.loading -> Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                st.error != null -> Column(Modifier.fillMaxWidth().padding(top = 50.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                    Text(st.error ?: "", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    TextButton(onClick = { vm.loadAccrual(cabinetId) }) { Text("Повторить") }
                }
            }
        }
    }

    if (showForce) {
        AlertDialog(onDismissRequest = { showForce = false },
            title = { Text("Начисление уже было") },
            text = { Text("Повторное начисление создаст новую запись (rework).") },
            confirmButton = { TextButton(onClick = { showForce = false; vm.accrue(cabinetId, if (meters > 0) meters else null, if (active.size == recipients.size) null else active.map { it.userId }, true) { _, _ -> } }) { Text("Начислить повторно") } },
            dismissButton = { TextButton(onClick = { showForce = false }) { Text("Отмена") } })
    }
}

@Composable
private fun recipientRow(r: AccrualRecipientDto, active: Boolean, meters: Double, onToggle: (Boolean) -> Unit) {
    val bc = MaterialTheme.belsiColors
    Row(Modifier.fillMaxWidth().clickable { onToggle(active) }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (active) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (active) bc.success else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        WalletAvatar(r.userName.split(" ").take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.userName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (r.alreadyAccrued) "уже начислялось" else "на смене", style = MaterialTheme.typography.bodySmall, color = if (r.alreadyAccrued) bc.warning else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("+${WalletBrand.rub(meters * r.rate)}", color = if (active) bc.success else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1)
    }
}
