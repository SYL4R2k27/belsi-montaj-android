package com.belsi.work.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.belsi.work.data.offline.OfflineQueueRepository
import com.belsi.work.presentation.navigation.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

/**
 * FIX(2026-05-05): Невидимый бейдж до тех пор пока в очереди ничего нет.
 *
 * Принцип: для юзера 1.2.5 без шатдауна интерфейс ВИЗУАЛЬНО ИДЕНТИЧЕН.
 * Иконка появляется только когда pendingCount > 0.
 *
 * Использование в TopAppBar:
 *   actions = {
 *       // другие иконки...
 *       PendingBadge(navController)
 *   }
 *
 * При клике — переход на экран «Ожидают отправки».
 */
@HiltViewModel
class PendingBadgeViewModel @Inject constructor(
    private val queue: OfflineQueueRepository,
) : ViewModel() {
    val count: StateFlow<Int> = queue.pendingCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0,
    )
}

@Composable
fun PendingBadge(
    navController: NavController,
    viewModel: PendingBadgeViewModel = hiltViewModel(),
) {
    val count by viewModel.count.collectAsState()

    // ВАРИАНТ C: ничего не показываем если очередь пуста — UI идентичен 1.2.5
    if (count <= 0) return

    IconButton(onClick = { navController.navigate(AppRoute.PendingActions.route) }) {
        BadgedBox(
            badge = {
                Badge(containerColor = Color(0xFFF59E0B)) {
                    Text(
                        text = if (count > 99) "99+" else "$count",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.CloudUpload,
                contentDescription = "Ожидают отправки: $count",
            )
        }
    }
}
