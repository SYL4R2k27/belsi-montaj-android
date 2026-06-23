package com.belsi.work.presentation.screens.legal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.belsi.work.data.legal.LegalTexts

/**
 * FIX(2026-05-12) build19 hotfix: универсальный просмотрщик юр-документов.
 *
 * Используется в Settings → «О приложении» → клик на любой документ:
 *  - Политика конфиденциальности → type="privacy"
 *  - Пользовательское соглашение → type="tos"
 *  - Лицензионное соглашение (EULA) → type="eula"
 *  - Согласие на AI-обработку → type="ai_consent"
 *
 * Текст берётся из LegalTexts.byType() — единый источник истины (тот же текст
 * используется в TermsScreen при регистрации и в UpdateGate при обновлении).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentScreen(
    navController: NavController,
    type: String,
) {
    val (title, docType) = when (type.lowercase()) {
        "tos" -> "Пользовательское соглашение" to LegalTexts.DocumentType.TOS
        "privacy" -> "Политика конфиденциальности" to LegalTexts.DocumentType.PRIVACY
        "eula" -> "Лицензионное соглашение (EULA)" to LegalTexts.DocumentType.EULA
        "ai_consent" -> "Согласие на AI-обработку" to LegalTexts.DocumentType.AI_CONSENT
        "self_employed_contract" -> "Договор с самозанятым (НПД)" to LegalTexts.DocumentType.SELF_EMPLOYED_CONTRACT
        else -> "Документ" to LegalTexts.DocumentType.PRIVACY
    }
    val text = LegalTexts.byType(docType)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Версия документов: ${LegalTexts.DOCUMENT_VERSION}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
