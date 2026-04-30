package com.belsi.work.presentation.screens.role

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.models.UserRole
import com.belsi.work.presentation.navigation.AppRoute

@Composable
fun RoleSelectScreen(
    navController: NavController,
    viewModel: RoleSelectViewModel = hiltViewModel()
) {
    val selectedRole by viewModel.selectedRole.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // Handle navigation events
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is NavigationEvent.NavigateToInstructions -> {
                    navController.navigate(AppRoute.Instructions.route) {
                        popUpTo(AppRoute.RoleSelect.route) { inclusive = true }
                    }
                }
                is NavigationEvent.NavigateToMain -> {
                    navController.navigate(AppRoute.Main.route) {
                        popUpTo(AppRoute.AuthPhone.route) { inclusive = true }
                    }
                }
                is NavigationEvent.NavigateToForemanMain -> {
                    navController.navigate(AppRoute.ForemanMain.route) {
                        popUpTo(AppRoute.AuthPhone.route) { inclusive = true }
                    }
                }
                is NavigationEvent.NavigateToCoordinatorMain -> {
                    navController.navigate(AppRoute.CoordinatorMain.route) {
                        popUpTo(AppRoute.AuthPhone.route) { inclusive = true }
                    }
                }
                is NavigationEvent.NavigateToCuratorMain -> {
                    navController.navigate(AppRoute.CuratorMain.route) {
                        popUpTo(AppRoute.AuthPhone.route) { inclusive = true }
                    }
                }
                is NavigationEvent.NavigateToInstallerInvite -> {
                    navController.navigate(AppRoute.InstallerInvite.route) {
                        popUpTo(AppRoute.AuthPhone.route) { inclusive = true }
                    }
                }
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
                )
            )
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Выберите роль",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Вы сможете изменить роль позже в настройках",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Role Cards
            UserRole.values().forEach { role ->
                RoleCard(
                    role = role,
                    isSelected = selectedRole == role,
                    onSelect = { viewModel.onRoleSelected(role) },
                    enabled = !isLoading
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Continue Button
            Button(
                onClick = { viewModel.confirmRole() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isLoading && selectedRole != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                    disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "Продолжить",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun RoleCard(
    role: UserRole,
    isSelected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onSelect() }
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        shape = MaterialTheme.shapes.large
                    )
                } else Modifier
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (isSelected) 0.25f else 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = role.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = role.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                )
            }
            
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Выбрано",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
