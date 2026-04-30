package com.belsi.work.presentation.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.api.VersionApi
import com.belsi.work.data.remote.api.VersionPolicyDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Компонент-баннер «Доступно обновление» / «Требуется обновление».
 *
 * Логика 2-недельного безопасного перехода:
 *  - update_required = true  → красный плотный блок, кнопки нет, навигация заблокирована
 *  - update_recommended = true → жёлтый dismissible баннер с кнопкой «Обновить»
 *  - иначе невидим
 */
@HiltViewModel
class UpdateBannerViewModel @Inject constructor(
    private val versionApi: VersionApi,
) : ViewModel() {

    private val _policy = MutableStateFlow<VersionPolicyDto?>(null)
    val policy = _policy.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val resp = versionApi.getVersionPolicy()
                if (resp.isSuccessful) _policy.value = resp.body()
            } catch (e: Exception) {
                android.util.Log.w("UpdateBanner", "Failed to load /version", e)
            }
        }
    }
}

@Composable
fun UpdateBanner(
    viewModel: UpdateBannerViewModel = hiltViewModel(),
) {
    val policy by viewModel.policy.collectAsState()
    val context = LocalContext.current
    var dismissed by remember { mutableStateOf(false) }

    val p = policy ?: return

    when {
        p.updateRequired -> {
            // Жёсткая блокировка — пользователь обязан обновить
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Требуется обновление",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Текущая версия больше не поддерживается. Обновитесь, чтобы продолжить работу.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(p.downloadUrl))
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Скачать ${p.latestVersion}") }
                }
            }
        }
        p.updateRecommended && !dismissed -> {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFF3CD),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.SystemUpdate, null, tint = Color(0xFF856404))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Доступно обновление до ${p.latestVersion}",
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF856404),
                        )
                        if (!p.changelog.isNullOrBlank()) {
                            Text(
                                p.changelog,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF856404),
                            )
                        }
                    }
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(p.downloadUrl))
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }) { Text("Обновить") }
                    TextButton(onClick = { dismissed = true }) { Text("×") }
                }
            }
        }
        else -> Unit
    }
}
