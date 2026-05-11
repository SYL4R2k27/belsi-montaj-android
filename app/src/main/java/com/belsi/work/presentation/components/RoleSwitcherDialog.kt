package com.belsi.work.presentation.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.data.models.User
import com.belsi.work.data.models.UserDomain
import com.belsi.work.data.models.UserRole
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * FIX(2026-05-06): Тест-режим — переключатель ролей.
 *
 * Доступен ТОЛЬКО в debug-сборке (BELSI.Команда). В production build
 * (com.belsi.work) кнопка не отображается.
 *
 * Поведение:
 * 1. Юзер выбирает роль из списка 11 ролей.
 * 2. PrefsManager.user обновляется новой ролью.
 * 3. Activity рестартует через `recreate()`.
 * 4. MainActivity читает новую роль и открывает соответствующий экран.
 *
 * SQL/server не трогается — это чисто клиентская подмена для тестов.
 * После logout настоящая роль с сервера восстановится.
 */

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RoleSwitcherEntryPoint {
    fun prefsManager(): PrefsManager
}

private fun getPrefsManager(context: Context): PrefsManager {
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        RoleSwitcherEntryPoint::class.java,
    )
    return entryPoint.prefsManager()
}

@Composable
fun RoleSwitcherDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { getPrefsManager(context) }
    val currentUser = remember { prefs.getUser() }
    val currentRole = currentUser?.role

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF59E0B))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("ТЕСТ", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Сменить роль",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Только локально, без изменения на сервере. После logout настоящая роль восстановится.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                // Группируем по доменам
                val grouped = UserRole.entries.groupBy { it.domain }
                val domainOrder = listOf(
                    UserDomain.INSTALLATION,
                    UserDomain.PRODUCTION,
                    UserDomain.LOGISTICS,
                    UserDomain.OBSERVER,
                )

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    domainOrder.forEach { domain ->
                        val roles = grouped[domain] ?: return@forEach
                        item {
                            Row(
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(domain.emoji, fontSize = 14.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    domain.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(roles) { role ->
                            RoleRow(
                                role = role,
                                isSelected = role == currentRole,
                                onClick = {
                                    if (role != currentRole) {
                                        // Закрываем диалог СНАЧАЛА — иначе Compose
                                        // не успеет очистить slot table до killProcess.
                                        onDismiss()
                                        switchRoleAndRestart(context, prefs, role)
                                    } else {
                                        onDismiss()
                                    }
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            // Полный logout — escape hatch если застряли.
                            // FIX(2026-05-10): синхронный commit + отложенный restart.
                            // Иначе apply() не успевает флашнуть до killProcess
                            // и токен/юзер остаются в памяти после рестарта.
                            prefs.clearAll()
                            forceCommitClearAll(context)
                            onDismiss()
                            restartProcessDelayed(context)
                        },
                    ) {
                        Text("Выйти из аккаунта", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Отмена")
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleRow(role: UserRole, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Text(role.emoji, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(role.title, fontWeight = FontWeight.SemiBold)
                Text(
                    role.description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Текущая",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Меняет роль локально и **полностью рестартует процесс** через Intent.
 *
 * FIX(2026-05-10): раньше использовался `Activity.recreate()` напрямую из
 * Composable callback — это вызывало `IntStack.peek2 index=-2` в Compose
 * runtime потому что recreate уничтожает Activity ПОКА Compose ещё мерит layout.
 *
 * Решение:
 * 1. Сохраняем новую роль в PrefsManager.
 * 2. Закрываем диалог (onDismiss).
 * 3. Через Handler.post(MAIN) — то есть в СЛЕДУЮЩЕМ frame после того как Compose
 *    закончит текущий ремеасур — стартуем новый Activity через launch intent
 *    с FLAG_ACTIVITY_CLEAR_TASK + NEW_TASK и убиваем процесс.
 *
 * Это гарантирует чистый старт: новый Application, новый Hilt-контейнер,
 * новый Compose, никаких остатков от предыдущей роли.
 */
private fun switchRoleAndRestart(context: Context, prefs: PrefsManager, newRole: UserRole) {
    val current = prefs.getUser() ?: return
    val updated = current.copy(role = newRole)
    // FIX(2026-05-10): saveUser использует .apply() — асинхронная запись.
    // killProcess убивает процесс ДО того как .apply() флашит на диск,
    // и роль не сохраняется. Дублируем запись + используем .commit()
    // через прямой доступ к SharedPreferences для синхронного flush.
    prefs.saveUser(updated)
    forceCommitUserChange(context, updated)
    restartProcessDelayed(context)
}

private fun forceCommitClearAll(context: Context) {
    try {
        val masterKey = androidx.security.crypto.MasterKey.Builder(context.applicationContext)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = androidx.security.crypto.EncryptedSharedPreferences.create(
            context.applicationContext,
            "belsi_secure_prefs",
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        prefs.edit().clear().commit()
    } catch (e: Exception) {
        android.util.Log.e("RoleSwitcher", "Force clear failed", e)
    }
}

private fun forceCommitUserChange(context: Context, updated: com.belsi.work.data.models.User) {
    try {
        val gson = com.google.gson.Gson()
        val json = gson.toJson(updated)
        // Прямой синхронный commit чтобы данные дошли до диска до killProcess
        val masterKey = androidx.security.crypto.MasterKey.Builder(context.applicationContext)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = androidx.security.crypto.EncryptedSharedPreferences.create(
            context.applicationContext,
            "belsi_secure_prefs",
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        // KEY_USER = "current_user" в PrefsManager (companion object)
        prefs.edit().putString("current_user", json).commit()  // СИНХРОННО
    } catch (e: Exception) {
        android.util.Log.e("RoleSwitcher", "Force commit failed", e)
    }
}

private fun restartProcessDelayed(context: Context) {
    val appContext = context.applicationContext
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        try {
            val intent = appContext.packageManager
                .getLaunchIntentForPackage(appContext.packageName)
                ?: return@post
            intent.addFlags(
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    or android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            )
            appContext.startActivity(intent)
            (context as? android.app.Activity)?.finishAndRemoveTask()
            // Гарантированно убиваем процесс чтобы Compose state сбросился
            android.os.Process.killProcess(android.os.Process.myPid())
        } catch (e: Exception) {
            android.util.Log.e("RoleSwitcher", "Failed to restart", e)
        }
    }
}
