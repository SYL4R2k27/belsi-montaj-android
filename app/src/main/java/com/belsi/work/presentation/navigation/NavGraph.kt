package com.belsi.work.presentation.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.belsi.work.presentation.screens.auth.phone.AuthPhoneScreen
import com.belsi.work.presentation.screens.auth.otp.OTPScreen
import com.belsi.work.presentation.screens.role.RoleSelectScreen
import com.belsi.work.presentation.screens.main.MainScreen
import com.belsi.work.presentation.screens.settings.SettingsScreen
import com.belsi.work.presentation.screens.about.AboutScreen
import com.belsi.work.presentation.screens.camera.CameraScreen
import com.belsi.work.presentation.screens.installer_invite.InstallerInviteScreen
import com.belsi.work.presentation.screens.foreman.ForemanMainScreen
import com.belsi.work.presentation.screens.coordinator.CoordinatorMainScreen
import com.belsi.work.presentation.screens.curator.CuratorMainScreen
import com.belsi.work.presentation.screens.profile.EditProfileScreen
import com.belsi.work.presentation.screens.wallet.WalletScreen
import com.belsi.work.presentation.screens.wallet.WithdrawScreen
import com.belsi.work.presentation.screens.support.SupportScreen
import com.belsi.work.presentation.screens.support.CreateTicketScreen
import com.belsi.work.presentation.screens.support.TicketDetailScreen
import com.belsi.work.presentation.screens.chat.InstallerChatScreen
import com.belsi.work.presentation.screens.chat.CuratorChatListScreen
import com.belsi.work.presentation.screens.chat.CuratorChatConversationScreen
import com.belsi.work.presentation.screens.shift_history.ShiftHistoryScreen
import com.belsi.work.presentation.screens.curator.photos.CuratorPhotosScreen
import com.belsi.work.presentation.screens.foreman.photos.ForemanPhotosScreen
import com.belsi.work.presentation.screens.debug.DebugSettingsScreen
import com.belsi.work.presentation.screens.gallery.PhotoGalleryScreen
import com.belsi.work.presentation.screens.tools.ToolsListScreen
import com.belsi.work.presentation.screens.tools.ToolIssueScreen
import com.belsi.work.presentation.screens.tools.ToolReturnScreen
import com.belsi.work.presentation.screens.curator.tools.CuratorToolsScreen
import com.belsi.work.presentation.screens.curator.support.CuratorSupportScreen
import com.belsi.work.presentation.screens.installer.invite.RedeemInviteScreen
import com.belsi.work.presentation.screens.installer.tools.RequestToolScreen
import com.belsi.work.presentation.screens.auth.login.LoginScreen
import com.belsi.work.presentation.screens.terms.TermsScreen
import com.belsi.work.presentation.screens.instructions.InstructionsScreen
import com.belsi.work.presentation.screens.curator.userdetail.CuratorUserDetailScreen
import com.belsi.work.presentation.screens.foreman.installerdetail.InstallerDetailScreen
import com.belsi.work.presentation.screens.messenger.GroupInfoScreen
import com.belsi.work.presentation.screens.messenger.MessengerConversationScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // FIX(2026-04-30): баннер обновления версии — виден на всех экранах,
        // включая главный (Settings, Foreman, Curator, Coordinator, Installer).
        // При update_required визуально перекрывает экран и предлагает скачать APK.
        com.belsi.work.presentation.components.UpdateBanner()
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.weight(1f)
        ) {
        // Auth Flow
        composable(AppRoute.AuthPhone.route) {
            AuthPhoneScreen(navController = navController)
        }

        // Login (password auth)
        composable(AppRoute.Login.route) {
            LoginScreen(navController = navController)
        }

        composable(
            route = AppRoute.OTP.route,
            arguments = listOf(navArgument("phone") { type = NavType.StringType })
        ) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            OTPScreen(navController = navController, phone = phone)
        }

        // Terms & Conditions (after OTP, before role select)
        composable(AppRoute.Terms.route) {
            TermsScreen(navController = navController)
        }

        // Role Selection
        composable(AppRoute.RoleSelect.route) {
            RoleSelectScreen(navController = navController)
        }

        // Instructions (after role select, before main)
        composable(AppRoute.Instructions.route) {
            InstructionsScreen(navController = navController)
        }

        // Installer Invite
        composable(AppRoute.InstallerInvite.route) {
            InstallerInviteScreen(navController = navController)
        }

        // Main
        composable(AppRoute.Main.route) {
            MainScreen(navController = navController)
        }

        // Foreman Main
        composable(AppRoute.ForemanMain.route) {
            ForemanMainScreen(navController = navController)
        }

        // Coordinator Main
        composable(AppRoute.CoordinatorMain.route) {
            CoordinatorMainScreen(navController = navController)
        }

        // Curator Main
        composable(AppRoute.CuratorMain.route) {
            CuratorMainScreen(navController = navController)
        }

        // Settings
        composable(AppRoute.Settings.route) {
            SettingsScreen(navController = navController)
        }

        // Reports
        composable(AppRoute.Reports.route) {
            com.belsi.work.presentation.screens.reports.ReportsScreen(navController = navController)
        }

        // About
        composable(AppRoute.About.route) {
            AboutScreen(navController = navController)
        }

        // Camera
        composable(AppRoute.Camera.route) {
            CameraScreen(navController = navController)
        }

        composable(
            route = AppRoute.CameraWithParams.route,
            arguments = listOf(
                navArgument("shiftId") { type = NavType.StringType },
                navArgument("slotIndex") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val shiftId = backStackEntry.arguments?.getString("shiftId")
            val slotIndex = backStackEntry.arguments?.getInt("slotIndex")
            CameraScreen(
                navController = navController,
                shiftId = shiftId,
                slotIndex = slotIndex
            )
        }

        // Edit Profile
        composable(AppRoute.EditProfile.route) {
            EditProfileScreen(navController = navController)
        }

        // Wallet
        composable(AppRoute.Wallet.route) {
            WalletScreen(navController = navController)
        }

        // Withdraw
        composable(AppRoute.Withdraw.route) {
            WithdrawScreen(navController = navController)
        }

        // Support
        composable(AppRoute.Support.route) {
            SupportScreen(navController = navController)
        }

        // Installer Chat
        composable(AppRoute.Chat.route) {
            InstallerChatScreen(navController = navController)
        }

        // Curator Chat List
        composable(AppRoute.CuratorChatList.route) {
            CuratorChatListScreen(navController = navController)
        }

        // Curator Chat Conversation
        composable(
            route = AppRoute.CuratorChatConversation.route,
            arguments = listOf(
                navArgument("ticketId") { type = NavType.StringType },
                navArgument("userPhone") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val ticketId = backStackEntry.arguments?.getString("ticketId") ?: ""
            val userPhone = backStackEntry.arguments?.getString("userPhone")
            CuratorChatConversationScreen(navController = navController, ticketId = ticketId, userPhone = userPhone)
        }

        // Create Ticket
        composable(AppRoute.CreateTicket.route) {
            CreateTicketScreen(navController = navController)
        }

        // Ticket Detail
        composable(
            route = AppRoute.TicketDetail.route,
            arguments = listOf(navArgument("ticketId") { type = NavType.StringType })
        ) { backStackEntry ->
            val ticketId = backStackEntry.arguments?.getString("ticketId") ?: ""
            TicketDetailScreen(navController = navController, ticketId = ticketId)
        }

        // Shift History
        composable(AppRoute.ShiftHistory.route) {
            ShiftHistoryScreen(navController = navController)
        }

        // Curator Photos
        composable(AppRoute.CuratorPhotos.route) {
            CuratorPhotosScreen(navController = navController)
        }

        // Foreman Photos
        composable(AppRoute.ForemanPhotos.route) {
            ForemanPhotosScreen(navController = navController)
        }

        // Photo Gallery
        composable(AppRoute.PhotoGallery.route) {
            PhotoGalleryScreen(navController = navController)
        }

        // Photo Detail
        composable(AppRoute.PhotoDetail.route) { backStackEntry ->
            val photoId = backStackEntry.arguments?.getString("photoId") ?: ""
            com.belsi.work.presentation.screens.photo_detail.PhotoDetailScreen(
                navController = navController,
                photoId = photoId
            )
        }

        // Debug Settings
        composable(AppRoute.DebugSettings.route) {
            DebugSettingsScreen(navController = navController)
        }

        // Tools Management
        composable(AppRoute.ToolsList.route) {
            ToolsListScreen(navController = navController)
        }

        composable(AppRoute.ToolIssue.route) {
            ToolIssueScreen(navController = navController)
        }

        composable(
            route = AppRoute.ToolIssueForInstaller.route,
            arguments = listOf(navArgument("installerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val installerId = backStackEntry.arguments?.getString("installerId")
            ToolIssueScreen(navController = navController, installerId = installerId)
        }

        // Note: ToolReturnScreen requires a ToolTransaction object
        // This will be handled through navigation arguments when implemented
        // For now, this route is a placeholder

        // Curator Tools
        composable(AppRoute.CuratorTools.route) {
            CuratorToolsScreen(navController = navController)
        }

        // Curator Support
        composable(AppRoute.CuratorSupport.route) {
            CuratorSupportScreen(navController = navController)
        }

        // Curator User Detail
        composable(
            route = AppRoute.CuratorUserDetail.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            CuratorUserDetailScreen(navController = navController, userId = userId)
        }

        // Curator Analytics
        composable(AppRoute.CuratorAnalytics.route) {
            com.belsi.work.presentation.screens.curator.analytics.CuratorAnalyticsScreen(
                navController = navController
            )
        }

        // AI Dashboard
        composable(AppRoute.AiDashboard.route) {
            com.belsi.work.presentation.screens.curator.ai.AiDashboardScreen(
                navController = navController
            )
        }

        // Installer - Redeem Invite
        composable(AppRoute.RedeemInvite.route) {
            RedeemInviteScreen(navController = navController)
        }

        // Installer - Request Tool
        composable(
            route = AppRoute.RequestTool.route,
            arguments = listOf(navArgument("foremanId") { type = NavType.StringType })
        ) { backStackEntry ->
            val foremanId = backStackEntry.arguments?.getString("foremanId") ?: ""
            RequestToolScreen(navController = navController, foremanId = foremanId)
        }

        // Foreman - Installer Detail
        composable(
            route = AppRoute.InstallerDetail.route,
            arguments = listOf(navArgument("installerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val installerId = backStackEntry.arguments?.getString("installerId") ?: ""
            InstallerDetailScreen(navController = navController, installerId = installerId)
        }

        // Messenger Conversation
        composable(
            route = AppRoute.MessengerConversation.route,
            arguments = listOf(navArgument("threadId") { type = NavType.StringType })
        ) { backStackEntry ->
            val threadId = backStackEntry.arguments?.getString("threadId") ?: ""
            MessengerConversationScreen(
                onBack = { navController.popBackStack() },
                onNavigateToGroupInfo = { tid ->
                    navController.navigate(AppRoute.GroupInfo.createRoute(tid))
                }
            )
        }

        // Group Info
        composable(
            route = AppRoute.GroupInfo.route,
            arguments = listOf(navArgument("threadId") { type = NavType.StringType })
        ) { backStackEntry ->
            val threadId = backStackEntry.arguments?.getString("threadId") ?: ""
            GroupInfoScreen(
                threadId = threadId,
                onBack = { navController.popBackStack() }
            )
        }

        // More routes will be added as screens are implemented
        }  // close NavHost
    }  // close Column
}
