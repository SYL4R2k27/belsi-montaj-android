package com.belsi.work.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.belsi.work.presentation.theme.belsiColors

/**
 * Reusable status badge component for displaying statuses like
 * "active", "completed", "open", "in_progress", "resolved", "closed", etc.
 */
@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val (containerColor, contentColor, displayLabel) = resolveStatusColors(status, label)

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = displayLabel,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun resolveStatusColors(status: String, label: String?): Triple<Color, Color, String> {
    val belsi = MaterialTheme.belsiColors
    val scheme = MaterialTheme.colorScheme

    return when (status.lowercase()) {
        "active", "open", "new" -> Triple(
            belsi.infoContainer,
            belsi.onInfoContainer,
            label ?: "Активный"
        )
        "in_progress", "inprogress", "pending" -> Triple(
            belsi.warningContainer,
            belsi.onWarningContainer,
            label ?: "В работе"
        )
        "completed", "resolved", "done", "approved" -> Triple(
            belsi.successContainer,
            belsi.onSuccessContainer,
            label ?: "Завершён"
        )
        "closed", "archived", "rejected", "cancelled" -> Triple(
            belsi.neutralContainer,
            belsi.onNeutralContainer,
            label ?: "Закрыт"
        )
        "error", "failed" -> Triple(
            scheme.errorContainer,
            scheme.onErrorContainer,
            label ?: "Ошибка"
        )
        else -> Triple(
            belsi.neutralContainer,
            belsi.onNeutralContainer,
            label ?: status
        )
    }
}
