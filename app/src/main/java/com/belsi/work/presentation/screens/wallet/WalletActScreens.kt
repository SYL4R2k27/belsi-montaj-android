package com.belsi.work.presentation.screens.wallet

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.legal.LegalTexts
import com.belsi.work.data.models.WalletActDto
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.theme.belsiColors
import java.util.Calendar

// ============================================================
// Ф2.5 — Монтажник: НПД/ИНН-профиль (карточка статуса + форма)
// ============================================================

/** Инфо-плашка для оформленных по трудовому договору (employed). */
@Composable
fun EmployedInfoBanner(onEdit: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(WalletBrand.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Badge, null, tint = WalletBrand.accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Вы оформлены по трудовому договору", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("Выплаты идут зарплатой, мимо приложения. Здесь — только история начислений.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onEdit) { Text("Изменить") }
        }
    }
}

/** Призыв заполнить налоговые данные (self_employed && incomplete) — выплаты заблокированы. */
@Composable
fun TaxIncompleteCallout(onFill: () -> Unit) {
    val bc = MaterialTheme.belsiColors
    Card(colors = CardDefaults.cardColors(containerColor = bc.warningContainer), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, null, tint = bc.onWarningContainer, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Заполните налоговые данные", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = bc.onWarningContainer)
                    Text("Чтобы выводить деньги, укажите ИНН и справку самозанятого (НПД).", style = MaterialTheme.typography.bodySmall, color = bc.onWarningContainer.copy(alpha = 0.85f))
                }
            }
            Button(onClick = onFill, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent)) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text("Заполнить", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Карточка-статус налоговых данных (открывает форму). */
@Composable
fun TaxProfileCard(profile: com.belsi.work.data.models.TaxProfileDto, onEdit: () -> Unit) {
    val bc = MaterialTheme.belsiColors
    val (statusText, statusColor) = when {
        profile.isEmployed -> "Трудовой договор" to WalletBrand.accent
        profile.isComplete -> "Данные заполнены" to bc.success
        else -> "Не заполнено" to bc.warning
    }
    Card(onClick = onEdit, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(statusColor.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                Icon(if (profile.isComplete || profile.isEmployed) Icons.Default.VerifiedUser else Icons.Default.Description, null, tint = statusColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Налоговые данные", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                val sub = if (profile.isEmployed) "Выплаты зарплатой"
                else listOfNotNull(
                    profile.inn?.takeIf { it.isNotBlank() }?.let { "ИНН $it" },
                    if (profile.hasCertificateFile) "справка загружена" else null
                ).joinToString(" · ").ifEmpty { "ИНН и справка НПД" }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(color = statusColor.copy(alpha = 0.12f), shape = RoundedCornerShape(99.dp)) {
                Text(statusText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
            }
        }
    }
}

/** Форма налоговых данных: тип договора + ИНН + № справки + дата (date-picker) + загрузка файла + приём договора. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxProfileSheet(vm: WalletViewModel, ui: WalletUiState, navController: NavController, onDismiss: () -> Unit) {
    val bc = MaterialTheme.belsiColors
    val context = LocalContext.current
    val profile = ui.taxProfile

    var employed by remember(profile) { mutableStateOf(profile?.isEmployed == true) }
    var inn by remember(profile) { mutableStateOf(profile?.inn ?: "") }
    var certNumber by remember(profile) { mutableStateOf(profile?.npdCertificateNumber ?: "") }
    var certDate by remember(profile) { mutableStateOf(profile?.npdCertificateDate ?: "") }   // ISO YYYY-MM-DD
    var contractChecked by remember(profile) { mutableStateOf(profile?.contractAccepted == true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    // выбор файла справки (любой документ/изображение)
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val file = WalletFileUtils.copyUriToCache(context, uri)
            if (file != null) vm.uploadCertificate(file) { ok, msg -> if (!ok) error = msg ?: "Не удалось загрузить файл" }
            else error = "Не удалось прочитать файл"
        }
    }

    val innValid = inn.isBlank() || inn.length == 10 || inn.length == 12
    val saving = ui.taxSaving

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Налоговые данные", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            // Тип договора
            Text("ТИП ДОГОВОРА", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !employed, onClick = { if (employed) { employed = false; vm.setContractType("self_employed") } }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Самозанятый") }
                SegmentedButton(selected = employed, onClick = { if (!employed) { employed = true; vm.setContractType("employed") } }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Штат") }
            }

            if (employed) {
                Card(colors = CardDefaults.cardColors(containerColor = WalletBrand.accent.copy(alpha = 0.1f)), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Badge, null, tint = WalletBrand.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(11.dp))
                        Text("Выплаты идут зарплатой по трудовому договору, мимо приложения. Кошелёк показывает только начисления.", style = MaterialTheme.typography.bodySmall, color = WalletBrand.brandBottom)
                    }
                }
            } else {
                // ИНН
                OutlinedTextField(value = inn, onValueChange = { inn = it.filter(Char::isDigit).take(12) },
                    label = { Text("ИНН (12 цифр для физлица)") }, singleLine = true, isError = !innValid,
                    supportingText = { if (!innValid) Text("ИНН — 10 или 12 цифр") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

                // № справки НПД
                OutlinedTextField(value = certNumber, onValueChange = { certNumber = it }, label = { Text("№ справки самозанятого (НПД)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                // Дата справки — date-picker
                OutlinedTextField(value = certDate.toRuDate(), onValueChange = {}, readOnly = true, label = { Text("Дата справки") },
                    trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.CalendarToday, "Выбрать дату") } },
                    modifier = Modifier.fillMaxWidth())

                // Загрузка файла справки
                CertificateUploadRow(profile?.hasCertificateFile == true, saving) { pickFile.launch("*/*") }

                // Приём Договора об оказании услуг с самозанятым (НПД)
                ContractAcceptRow(
                    checked = contractChecked,
                    onToggle = { contractChecked = it },
                    onRead = {
                        onDismiss()
                        navController.navigate(AppRoute.LegalDocument.createRoute("self_employed_contract"))
                    },
                )
            }

            error?.let { ErrorLine(it) }

            val contractOk = employed || contractChecked
            Button(onClick = {
                if (!innValid) { error = "ИНН — 10 или 12 цифр"; return@Button }
                if (!contractOk) { error = "Примите условия Договора с самозанятым"; return@Button }
                error = null
                vm.saveTaxProfile(
                    contractType = if (employed) "employed" else "self_employed",
                    inn = inn.takeIf { it.isNotBlank() },
                    certNumber = certNumber.takeIf { it.isNotBlank() },
                    certDate = certDate.takeIf { it.isNotBlank() },
                    // принятие шлём только для самозанятого и только если отмечено
                    contractAccepted = if (!employed && contractChecked) true else null,
                ) { ok, msg -> if (ok) onDismiss() else error = msg }
            }, enabled = !saving && innValid && contractOk, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent)) {
                if (saving) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Сохранить", fontWeight = FontWeight.Bold)
            }
            Text("Данные нужны для Акта и выплат. Статус НПД проверит посредник перед переводом.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = certDate.toMillisOrNull() ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { certDate = it.toIsoDate() }
                    showDatePicker = false
                }) { Text("ОК") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Отмена") } }
        ) { DatePicker(state = dpState) }
    }
}

@Composable
private fun CertificateUploadRow(hasFile: Boolean, busy: Boolean, onPick: () -> Unit) {
    val bc = MaterialTheme.belsiColors
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background((if (hasFile) bc.success else WalletBrand.accent).copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                Icon(if (hasFile) Icons.Default.CheckCircle else Icons.Default.UploadFile, null, tint = if (hasFile) bc.success else WalletBrand.accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (hasFile) "Справка загружена" else "Файл справки НПД", fontWeight = FontWeight.SemiBold)
                Text("PDF, JPG или PNG", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else OutlinedButton(onClick = onPick, shape = RoundedCornerShape(12.dp)) { Text(if (hasFile) "Заменить" else "Загрузить") }
        }
    }
}

/**
 * Приём Договора об оказании услуг с самозанятым (НПД): чекбокс + ссылка «Читать договор».
 * Принимает любой самозанятый (монтажник И бригадир). Гейтит сохранение/выплату.
 */
@Composable
private fun ContractAcceptRow(checked: Boolean, onToggle: (Boolean) -> Unit, onRead: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { onToggle(!checked) }.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = { onToggle(it) }, colors = CheckboxDefaults.colors(checkedColor = WalletBrand.accent))
                Text(
                    LegalTexts.Checkboxes.SELF_EMPLOYED_CONTRACT,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
            }
            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Assignment, null, tint = WalletBrand.accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Читать договор",
                    color = WalletBrand.accent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onRead() }.padding(vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * Призыв принять Договор самозанятого (самозанятый не принял → выплата заблокирована).
 * «Читать договор» открывает текст; «Принять» открывает форму налог-данных с чекбоксом.
 */
@Composable
fun ContractNeededCallout(onRead: () -> Unit, onAccept: () -> Unit) {
    val bc = MaterialTheme.belsiColors
    Card(colors = CardDefaults.cardColors(containerColor = bc.warningContainer), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Assignment, null, tint = bc.onWarningContainer, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Примите Договор с самозанятым", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = bc.onWarningContainer)
                    Text("Чтобы выводить деньги, нужно принять условия Договора об оказании услуг (НПД).", style = MaterialTheme.typography.bodySmall, color = bc.onWarningContainer.copy(alpha = 0.85f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(onClick = onRead, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text("Читать договор")
                }
                Button(onClick = onAccept, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent)) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Принять", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================
// Ф2.5 — Монтажник: «Мои акты»
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallerActCard(act: WalletActDto, onTap: () -> Unit) {
    val (statusColor, statusBg) = actStatusColors(act.status)
    Card(onClick = onTap, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(WalletBrand.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.Assignment, null, tint = WalletBrand.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Акт · ${act.label}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${act.itemsCount} кабинетов · ${WalletBrand.rub(act.totalDouble)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(color = statusBg, shape = RoundedCornerShape(99.dp)) {
                Text(act.statusDisplay, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallerActDetailSheet(act: WalletActDto, vm: WalletViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val (statusColor, statusBg) = actStatusColors(act.status)
    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Акт · ${act.label}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(WalletBrand.rub(act.totalDouble), fontSize = 32.sp, fontWeight = FontWeight.Black, color = WalletBrand.brandBottom)
                Surface(color = statusBg, shape = RoundedCornerShape(99.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Text(act.statusDisplay, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp))
                }
            }

            if (act.items.isNotEmpty()) {
                SectionLabel("Позиции · ${act.items.size}")
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(22.dp)) {
                    Column {
                        act.items.forEachIndexed { i, it ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                            Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Кабинет ${it.cabinetNumber ?: "—"}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val sub = listOfNotNull(it.objectName, it.meters?.let { m -> "$m п.м." }).joinToString(" · ")
                                    if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(WalletBrand.rub(it.amountDouble), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            ExplainCard("Акт об оказании услуг", "Месячный документ для самозанятого: один Акт = одна выплата за месяц. После подписания куратором — оплата через Rocket Work + чек ФНС.", Icons.AutoMirrored.Filled.Assignment)

            error?.let { ErrorLine(it) }

            Button(onClick = {
                downloading = true; error = null
                vm.downloadAct(act.id ?: "", curator = false) { doc, msg ->
                    downloading = false
                    if (doc != null) WalletFileUtils.shareDocument(context, doc) else error = msg
                }
            }, enabled = !downloading && !act.id.isNullOrBlank(), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent)) {
                if (downloading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else { Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Скачать Акт", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ============================================================
// Ф2.5 — Куратор: Месячные акты (период + очередь по людям)
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratorMonthlyActsScreen(navController: NavController) {
    val vm: WalletViewModel = hiltViewModel()
    val st by vm.curatorActs.collectAsState()
    val context = LocalContext.current

    val now = remember { Calendar.getInstance() }
    var year by remember { mutableStateOf(now.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(now.get(Calendar.MONTH) + 1) }

    LaunchedEffect(year, month) { vm.loadCuratorActs(year, month) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Месячные акты", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Селектор периода
            PeriodSelector(year, month,
                onPrev = { if (month == 1) { month = 12; year -= 1 } else month -= 1 },
                onNext = { if (month == 12) { month = 1; year += 1 } else month += 1 })

            st.error?.let { ErrorLine(it) }

            val total = st.acts.sumOf { it.totalDouble }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ovActCard("Актов", "${st.acts.size}", WalletBrand.accent, Modifier.weight(1f))
                ovActCard("Сумма за месяц", WalletBrand.rub(total), MaterialTheme.belsiColors.success, Modifier.weight(1f))
            }

            when {
                st.loading -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                st.acts.isEmpty() -> Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Assignment, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                    Text("Нет начислений за этот период", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                else -> {
                    SectionLabel("Акты по людям · ${st.acts.size}")
                    st.acts.forEach { act ->
                        CuratorActCard(act, st.busyUserId == act.userId, st.busyActId == act.id,
                            onAssemble = { vm.assembleAct(act.userId ?: "") { _, _ -> } },
                            onSign = { act.id?.let { id -> vm.signAct(id) { _, _ -> } } },
                            onMarkPaid = { act.id?.let { id -> vm.markActPaid(id) { _, _ -> } } },
                            onDownload = {
                                act.id?.let { id ->
                                    vm.downloadAct(id, curator = true) { doc, _ -> if (doc != null) WalletFileUtils.shareDocument(context, doc) }
                                }
                            })
                    }

                    Text("Гибрид-касса: начисления за принятые кабинеты копятся весь месяц → собираешь в один Акт → подписываешь → одна выплата через Rocket Work + чек ФНС.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun PeriodSelector(year: Int, month: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, "Раньше") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(WalletActDto.RU_MONTHS.getOrNull(month - 1) ?: "$month", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("$year", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "Позже") }
        }
    }
}

@Composable
private fun ovActCard(k: String, v: String, tint: Color, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column(Modifier.padding(13.dp)) {
            Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(v, color = tint, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CuratorActCard(
    act: WalletActDto, assembling: Boolean, busy: Boolean,
    onAssemble: () -> Unit, onSign: () -> Unit, onMarkPaid: () -> Unit, onDownload: () -> Unit,
) {
    val bc = MaterialTheme.belsiColors
    val (statusColor, statusBg) = actStatusColors(act.status)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WalletAvatar(act.initials, 40.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(act.userName ?: "—", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${act.itemsCount} кабинетов · ${act.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(WalletBrand.rub(act.totalDouble), fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Surface(color = statusBg, shape = RoundedCornerShape(99.dp), modifier = Modifier.padding(top = 3.dp)) {
                        Text(if (act.isVirtualDraft) "Не собран" else act.statusDisplay, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }
            }

            // Rocket Work намёк для signed
            if (act.status == "signed") {
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = WalletBrand.accent, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Подписан → отметь выплату → Rocket Work → чек ФНС", color = WalletBrand.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(11.dp))
            // Действия по статусу
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                val working = assembling || busy
                when {
                    act.isVirtualDraft || act.status == "draft" -> {
                        if (act.isVirtualDraft) {
                            Button(onClick = onAssemble, enabled = !working, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = WalletBrand.accent), shape = RoundedCornerShape(12.dp)) {
                                if (working) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                else { Icon(Icons.Default.Build, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Собрать", fontWeight = FontWeight.Bold) }
                            }
                        } else {
                            OutlinedButton(onClick = onAssemble, enabled = !working, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Пересобрать")
                            }
                            Button(onClick = onSign, enabled = !working, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = bc.success), shape = RoundedCornerShape(12.dp)) {
                                if (working) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                else { Icon(Icons.Default.EditNote, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Подписать", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                    act.status == "signed" -> {
                        Button(onClick = onMarkPaid, enabled = !working, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = bc.success), shape = RoundedCornerShape(12.dp)) {
                            if (working) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            else { Icon(Icons.Default.VerifiedUser, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Отметить выплаченным", fontWeight = FontWeight.Bold) }
                        }
                    }
                    act.status == "paid" -> {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = bc.success, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Выплачено", color = bc.success, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                // Скачать Акт — для реальных (несобранный кандидат документа не имеет)
                if (!act.isVirtualDraft) {
                    OutlinedButton(onClick = onDownload, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Download, "Скачать Акт", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ============================================================
// helpers
// ============================================================

@Composable
private fun actStatusColors(status: String): Pair<Color, Color> {
    val bc = MaterialTheme.belsiColors
    return when (status) {
        "paid" -> bc.success to bc.success.copy(alpha = 0.13f)
        "signed" -> WalletBrand.accent to WalletBrand.accent.copy(alpha = 0.12f)
        else -> bc.warning to bc.warningContainer   // draft / прочее
    }
}

/** ISO YYYY-MM-DD → «15.01.2026». */
private fun String.toRuDate(): String {
    if (isBlank()) return ""
    val p = take(10).split("-")
    return if (p.size == 3) "${p[2]}.${p[1]}.${p[0]}" else this
}

private fun String.toMillisOrNull(): Long? {
    val p = take(10).split("-")
    if (p.size != 3) return null
    val y = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    val d = p[2].toIntOrNull() ?: return null
    return Calendar.getInstance().apply {
        clear(); set(y, m - 1, d, 12, 0, 0)
    }.timeInMillis
}

private fun Long.toIsoDate(): String {
    val c = Calendar.getInstance().apply { timeInMillis = this@toIsoDate }
    val y = c.get(Calendar.YEAR)
    val m = c.get(Calendar.MONTH) + 1
    val d = c.get(Calendar.DAY_OF_MONTH)
    return "%04d-%02d-%02d".format(y, m, d)
}
