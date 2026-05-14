package com.belsi.work.presentation.screens.update_gate

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.belsi.work.BuildConfig

/**
 * FIX(2026-05-11) BELSI 2.0.0: Update Gate Compose-диалог (B5).
 *
 * Полноэкранный non-dismissible диалог, который показывается при старте,
 * если сервер сказал update_required=true. Юзер должен отметить все 6
 * чекбоксов, чтобы кнопка «Скачать APK» стала активной.
 *
 * Тап «Скачать» → POST /audit/update-consent. При успехе — открывается
 * Intent на загрузку APK по URL из VersionPolicyDto.downloadUrl.
 *
 * Реализация по утверждённому брендбуку (docs/brandbook-belsi-komanda
 * → секция 15 · Экран обновления).
 */
@Composable
fun UpdateGateDialog(
    viewModel: UpdateGateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    if (!state.shouldShowGate) return
    val policy = state.policy ?: return

    Dialog(
        onDismissRequest = { /* non-dismissible — закрытие только через «Назад»  */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                // FIX(2026-05-11) BELSI 2.0.0: max-width 720dp на широких экранах.
                // На phone (411dp×0.96=395) ограничение не активно. На Tab S Ultra
                // (1232dp) дополнительно ограничивает до 720dp — диалог не
                // растягивается на полэкрана, остаётся читаемым.
                .widthIn(max = 720.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                UpdateGateHeader(
                    fromVersion = state.fromVersion,
                    toVersion = policy.latestVersion,
                    isFirstLaunch = policy.downloadUrl.isBlank(),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    UpdateGateFeaturesList()
                    AiHelperInfoCard()
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    UpdateGateChecksList(
                        checks = state.checks,
                        onToggle = { id, v -> viewModel.setChecked(id, v) },
                    )
                }

                UpdateGateStateRow(canSubmit = state.canSubmit, error = state.error)

                UpdateGateActions(
                    canSubmit = state.canSubmit,
                    submitting = state.submitting,
                    // First-launch (downloadUrl пуст) — мы уже на этой версии,
                    // ничего скачивать не нужно, просто фиксируем согласие.
                    isFirstLaunch = policy.downloadUrl.isBlank(),
                    onBack = { viewModel.dismiss() },
                    onDownload = {
                        viewModel.submitConsentAndDownload(
                            appBuild = BuildConfig.VERSION_CODE,
                            deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}",
                            onReady = { url ->
                                // FIX(2026-05-11) BELSI 2.0.0 security: валидация URL.
                                // Раньше любой URL с сервера открывался через ACTION_VIEW
                                // (потенциальный вектор подмены при MITM/server-compromise).
                                // Сейчас разрешаем только HTTPS на доверенные хосты BELSI.
                                val parsed = runCatching { Uri.parse(url) }.getOrNull()
                                val scheme = parsed?.scheme?.lowercase()
                                val host = parsed?.host?.lowercase()
                                val allowedHosts = setOf(
                                    "api.belsi.ru",
                                    "bucket.api.belsi.ru",
                                    "belsi.ru",
                                )
                                if (parsed != null && scheme == "https" && host in allowedHosts) {
                                    val intent = Intent(Intent.ACTION_VIEW, parsed)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } else {
                                    android.util.Log.w(
                                        "UpdateGate",
                                        "Refused unsafe download URL: scheme=$scheme host=$host"
                                    )
                                }
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun UpdateGateHeader(fromVersion: String, toVersion: String, isFirstLaunch: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFF59E0B), Color(0xFFB45309)),
                )
            )
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "v$fromVersion",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    " → ",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
                Text(
                    "v$toVersion",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(99.dp),
                ) {
                    Text(
                        "MAJOR",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "BELSI.Команда $toVersion",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (isFirstLaunch)
                    "Подтвердите 9 пунктов согласия — один раз"
                else
                    "Большое обновление · 5 блоков изменений",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun UpdateGateFeaturesList() {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        UpdateGateContent.blocks.forEach { block ->
            Text(
                block.title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB45309),
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
            )
            block.features.forEach { f ->
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text(
                        f.emoji,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        f.text,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun AiHelperInfoCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(Color(0xFFF59E0B))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                UpdateGateContent.aiHelperNote,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UpdateGateChecksList(
    checks: Map<String, Boolean>,
    onToggle: (String, Boolean) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            "Для продолжения отметьте все ${UpdateGateContent.checkboxes.size} пунктов",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        UpdateGateContent.checkboxes.forEachIndexed { idx, item ->
            val checked = checks[item.id] == true
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (checked) Color(0xFFD97706)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (checked) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    item.text,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = { onToggle(item.id, !checked) }),
                )
            }
            if (idx < UpdateGateContent.checkboxes.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun UpdateGateStateRow(canSubmit: Boolean, error: String?) {
    val total = UpdateGateContent.checkboxes.size
    val (bg, fg, label) = when {
        error != null -> Triple(Color(0xFFFEE2E2), Color(0xFFB91C1C), "⚠ $error")
        canSubmit     -> Triple(Color(0xFFECFDF5), Color(0xFF065F46), "✓ Готово.")
        else          -> Triple(Color(0xFFFEF3C7), Color(0xFF92400E), "⚠ Отметьте все $total пунктов, чтобы продолжить")
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = fg,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun UpdateGateActions(
    canSubmit: Boolean,
    submitting: Boolean,
    isFirstLaunch: Boolean,
    onBack: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = onBack,
            enabled = !submitting,
            modifier = Modifier.weight(1f),
        ) {
            Text("Назад")
        }
        Button(
            onClick = onDownload,
            enabled = canSubmit,
            modifier = Modifier.weight(1.6f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFD97706),
                contentColor = Color.White,
            ),
        ) {
            if (submitting) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Регистрируем согласие…")
            } else if (isFirstLaunch) {
                Text("Подтвердить и продолжить")
            } else {
                Text("Скачать APK")
            }
        }
    }
}

