package com.belsi.work.presentation.components.montage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.belsi.work.presentation.theme.BelsiWorkTheme
import com.belsi.work.presentation.theme.belsiColors

/**
 * 🔥 Super-фото алёрт с контекстом (объект/окно/этап) + 2 действия. Molecule.
 * Топ-приоритет в «Сейчас» курaтора (4.1) / у бригадира. Rose-акцент = срочность.
 */
@Composable
fun SuperPhotoAlert(
    title: String,
    context: String,
    modifier: Modifier = Modifier,
    timeChip: String? = null,
    replyLabel: String = "Ответить",
    onReply: (() -> Unit)? = null,
    onCall: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.errorContainer.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🔥", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.weight(1f)) {
                if (timeChip != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(scheme.error)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(timeChip, style = MaterialTheme.typography.labelSmall, color = scheme.onError, fontWeight = FontWeight.Bold)
                    }
                }
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(context, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onReply != null) {
                Button(
                    onClick = onReply,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Text(replyLabel) }
            }
            if (onCall != null) {
                OutlinedButton(onClick = onCall, modifier = Modifier.weight(1f)) { Text("📞") }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SuperPhotoAlertPreview() {
    BelsiWorkTheme {
        SuperPhotoAlert(
            title = "Super-фото · Тест Старший",
            context = "М.Тульская · 305 окно 2 подок · «стык неровный»",
            timeChip = "🔥 30 сек",
            onReply = {},
            onCall = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
