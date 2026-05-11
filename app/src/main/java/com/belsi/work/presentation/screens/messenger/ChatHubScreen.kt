package com.belsi.work.presentation.screens.messenger

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.screens.chat.InstallerChatScreen

/**
 * Hub screen with two tabs: Messenger (personal chats) + Support (current support chat)
 * Replaces direct InstallerChatScreen in the tab bar
 */
@Composable
fun ChatHubScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        // Tab row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Сообщения") },
                icon = { Icon(Icons.Default.Chat, null, Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Поддержка") },
                icon = { Icon(Icons.Default.Support, null, Modifier.size(18.dp)) }
            )
        }

        // Content
        when (selectedTab) {
            0 -> ThreadListScreen(
                onNavigateToConversation = { threadId ->
                    navController.navigate(AppRoute.MessengerConversation.createRoute(threadId))
                }
            )
            1 -> InstallerChatScreen(
                navController = navController,
                modifier = Modifier
            )
        }
    }
}
