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
import com.belsi.work.presentation.screens.profile.ProfileScreen
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
// FIX(2026-05-12) build17: ToolReturnScreen import удалён — placeholder route не нужен.
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
            // FIX(2026-05-12) build19+: финальный LoginScreen (C2 · mesh accent + dynamic greeting)
            LoginScreen(navController = navController)
        }

        // FIX(2026-05-04): SignUp wizard (V1 без email/SMS подтверждения)
        composable(AppRoute.SignUp.route) {
            com.belsi.work.presentation.screens.auth.signup.SignUpScreen(navController = navController)
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

        // FIX(2026-05-13) release/2.0.1-internal: убран DriverPlayground route
        // (sandbox-экран для тестирования). Production routes ниже.
        // ─────────────────────────────────────────────────────────────────
        composable(AppRoute.DriverHome.route) {
            com.belsi.work.presentation.screens.driver.DriverHomeScreen(
                onPointClick = { pointId ->
                    navController.navigate(AppRoute.DriverPointDetail.createRoute(pointId))
                },
                onMenuClick = { /* TODO menu */ },
                topBar = {},  // build16: используем дефолт пустой topBar
            )
        }
        composable(
            route = AppRoute.DriverPointDetail.route,
            arguments = listOf(androidx.navigation.navArgument("pointId") { type = androidx.navigation.NavType.StringType }),
        ) { entry ->
            val pointId = entry.arguments?.getString("pointId") ?: return@composable
            com.belsi.work.presentation.screens.driver.DriverPointDetailScreen(
                pointId = pointId,
                onCameraClick = { eventType ->
                    // TODO: camera flow с GPS (build11 DriverEventCameraScreen) — пока через AppRoute.Camera
                    navController.navigate(AppRoute.Camera.route)
                },
                onMarkDelivered = { navController.popBackStack() },
            )
        }
        composable(AppRoute.LogisticianHome.route) {
            com.belsi.work.presentation.screens.logistician.LogisticianHomeScreen(
                onCreateRoute = { navController.navigate(AppRoute.LogistCreateRoute.route) },
                onRequestClick = { id -> navController.navigate(AppRoute.LogistRequestDetail.createRoute(id)) },
                onRouteClick = { id -> navController.navigate(AppRoute.LogistRouteDetail.createRoute(id)) },
                onBatchesClick = { navController.navigate(AppRoute.BatchList.route) },
                onDriverListClick = { navController.navigate(AppRoute.LogistDriverList.route) },
                topBar = {},
            )
        }
        composable(AppRoute.LogistDriverList.route) {
            com.belsi.work.presentation.screens.logistician.LogistDriverListScreen(
                onDriverClick = { id -> navController.navigate(AppRoute.LogistDriverDetail.createRoute(id)) },
                onCreateRoute = { navController.navigate(AppRoute.LogistCreateRoute.route) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = AppRoute.LogistDriverDetail.route,
            arguments = listOf(androidx.navigation.navArgument("driverId") { type = androidx.navigation.NavType.StringType }),
        ) { entry ->
            val driverId = entry.arguments?.getString("driverId") ?: return@composable
            com.belsi.work.presentation.screens.logistician.LogistDriverDetailScreen(
                driverId = driverId,
                onCreateRoute = { navController.navigate(AppRoute.LogistCreateRoute.route) },
                onRouteClick = { rid -> navController.navigate(AppRoute.LogistRouteDetail.createRoute(rid)) },
            )
        }
        composable(
            route = AppRoute.LogistRouteDetail.route,
            arguments = listOf(androidx.navigation.navArgument("routeId") { type = androidx.navigation.NavType.StringType }),
        ) { entry ->
            val routeId = entry.arguments?.getString("routeId") ?: return@composable
            com.belsi.work.presentation.screens.logistician.LogistRouteDetailScreen(
                routeId = routeId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoute.LogistCreateRoute.route) {
            com.belsi.work.presentation.screens.logistician.LogistCreateRouteScreen(
                onAssign = { navController.popBackStack() },
            )
        }
        composable(
            route = AppRoute.LogistRequestDetail.route,
            arguments = listOf(androidx.navigation.navArgument("requestId") { type = androidx.navigation.NavType.StringType }),
        ) { entry ->
            val requestId = entry.arguments?.getString("requestId") ?: return@composable
            com.belsi.work.presentation.screens.logistician.LogistRequestDetailScreen(
                requestId = requestId,
                onAddToRoute = { navController.popBackStack() },
                onCreateNewRoute = {
                    navController.navigate(AppRoute.LogistCreateRoute.route) {
                        popUpTo(AppRoute.LogistRequestDetail.route) { inclusive = true }
                    }
                },
            )
        }

        // Settings
        composable(AppRoute.Settings.route) {
            SettingsScreen(navController = navController)
        }

        // Change Password
        composable(AppRoute.ChangePassword.route) {
            com.belsi.work.presentation.screens.settings.ChangePasswordScreen(navController = navController)
        }

        // Curator Shift Admin (re-open / edit / audit)
        composable(
            route = AppRoute.CuratorShiftAdmin.route,
            arguments = listOf(
                androidx.navigation.navArgument("userId") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("name") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            val name = backStackEntry.arguments?.getString("name")
            com.belsi.work.presentation.screens.curator.shiftadmin.ShiftAdminScreen(
                navController = navController,
                userId = userId,
                userName = name,
            )
        }

        // Reports
        composable(AppRoute.Reports.route) {
            com.belsi.work.presentation.screens.reports.ReportsScreen(navController = navController)
        }

        // About
        composable(AppRoute.About.route) {
            AboutScreen(navController = navController)
        }

        // FIX(2026-05-12) build19 hotfix: универсальный просмотрщик юр-документов.
        // Вызывается из Settings → «О приложении» → клик на любой документ.
        composable(
            route = AppRoute.LegalDocument.route,
            arguments = listOf(androidx.navigation.navArgument("type") { type = androidx.navigation.NavType.StringType }),
        ) { entry ->
            val type = entry.arguments?.getString("type") ?: "privacy"
            com.belsi.work.presentation.screens.legal.LegalDocumentScreen(
                navController = navController,
                type = type,
            )
        }

        // FIX(2026-05-12) build19 Этап3: tool-transfer pipeline экраны.
        composable(
            route = AppRoute.ToolTransferCreate.route,
            arguments = listOf(
                androidx.navigation.navArgument("siteObjectId") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                androidx.navigation.navArgument("batchId") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            com.belsi.work.presentation.screens.tools.ToolTransferCreateScreen(
                navController = navController,
                siteObjectId = entry.arguments?.getString("siteObjectId"),
                batchId = entry.arguments?.getString("batchId"),
            )
        }
        composable(AppRoute.ToolTransferIncoming.route) {
            // FIX(2026-05-12) build19: совместимость с push-deeplink'ами на старый route.
            // Открываем универсальный hub на табе incoming.
            com.belsi.work.presentation.screens.tools.ToolTransferHubScreen(
                navController = navController,
                initialTab = "incoming",
            )
        }
        composable(
            route = AppRoute.ToolTransferHub.route,
            arguments = listOf(androidx.navigation.navArgument("tab") {
                type = androidx.navigation.NavType.StringType
                defaultValue = "incoming"
            }),
        ) { entry ->
            com.belsi.work.presentation.screens.tools.ToolTransferHubScreen(
                navController = navController,
                initialTab = entry.arguments?.getString("tab") ?: "incoming",
            )
        }
        composable(
            route = AppRoute.ToolTransferDetail.route,
            arguments = listOf(androidx.navigation.navArgument("transferId") {
                type = androidx.navigation.NavType.StringType
            }),
        ) { entry ->
            com.belsi.work.presentation.screens.tools.ToolTransferDetailScreen(
                navController = navController,
                transferId = entry.arguments?.getString("transferId") ?: "",
            )
        }

        // FIX(2026-05-14) BELSI 2.0.1: return flow routes
        composable(
            route = AppRoute.ToolReturnRequest.route,
            arguments = listOf(androidx.navigation.navArgument("transferId") {
                type = androidx.navigation.NavType.StringType
            }),
        ) { entry ->
            com.belsi.work.presentation.screens.tools.ToolReturnRequestScreen(
                navController = navController,
                transferId = entry.arguments?.getString("transferId") ?: "",
            )
        }
        composable(
            route = AppRoute.ToolReturnPickup.route,
            arguments = listOf(androidx.navigation.navArgument("transferId") {
                type = androidx.navigation.NavType.StringType
            }),
        ) { entry ->
            com.belsi.work.presentation.screens.tools.ToolReturnPickupScreen(
                navController = navController,
                transferId = entry.arguments?.getString("transferId") ?: "",
            )
        }
        composable(
            route = AppRoute.ToolReturnDeliver.route,
            arguments = listOf(androidx.navigation.navArgument("transferId") {
                type = androidx.navigation.NavType.StringType
            }),
        ) { entry ->
            com.belsi.work.presentation.screens.tools.ToolReturnDeliverScreen(
                navController = navController,
                transferId = entry.arguments?.getString("transferId") ?: "",
            )
        }
        composable(
            route = AppRoute.ToolReturnAccept.route,
            arguments = listOf(androidx.navigation.navArgument("transferId") {
                type = androidx.navigation.NavType.StringType
            }),
        ) { entry ->
            com.belsi.work.presentation.screens.tools.ToolReturnAcceptScreen(
                navController = navController,
                transferId = entry.arguments?.getString("transferId") ?: "",
            )
        }
        composable(AppRoute.CuratorReturns.route) {
            com.belsi.work.presentation.screens.tools.CuratorReturnsScreen(
                navController = navController,
            )
        }
        // FIX(2026-05-14) BELSI 2.0.1: единая лента алертов куратора
        composable(AppRoute.CuratorAlertsFeed.route) {
            com.belsi.work.presentation.screens.curator.alerts.CuratorAlertsFeedScreen(
                navController = navController,
            )
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

        // FIX(2026-05-11) BELSI 2.0.0: standalone Profile route — для куратора
        // (вход через overflow меню «Мой профиль»). У бригадира ProfileScreen
        // живёт как таб в ForemanMainScreen — этот route её НЕ дублирует.
        composable(AppRoute.Profile.route) {
            ProfileScreen(navController = navController)
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
        // FIX(2026-05-12) build17: ToolReturn — placeholder удалён, AppRoute убран.

        // FIX(2026-05-12) build17 P0: реальный экран создания заявки на доставку координатором.
        composable(
            route = AppRoute.CoordCreateRequest.route,
            arguments = listOf(navArgument("batchId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val batchId = backStackEntry.arguments?.getString("batchId")
            com.belsi.work.presentation.screens.coordinator.CoordCreateRequestScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() },
                initialBatchId = batchId,
            )
        }

        // FIX(2026-05-12) build17 P2: ShiftDetail composable — теперь подключён.
        composable(
            route = AppRoute.ShiftDetail.route,
            arguments = listOf(navArgument("shiftId") { type = NavType.StringType })
        ) { backStackEntry ->
            val shiftId = backStackEntry.arguments?.getString("shiftId") ?: ""
            com.belsi.work.presentation.screens.shift_history.ShiftDetailScreen(
                shiftId = shiftId,
                onBack = { navController.popBackStack() },
                onPhotoClick = { photoId ->
                    navController.navigate(AppRoute.PhotoDetail.createRoute(photoId))
                },
            )
        }

        // FIX(2026-05-12) build18 P2: детальная карточка участника команды координатора.
        composable(
            route = AppRoute.CoordTeamMemberDetail.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            com.belsi.work.presentation.screens.coordinator.teammember.CoordinatorTeamMemberDetailScreen(
                userId = userId,
                onBack = { navController.popBackStack() },
                onPhotoClick = { photoId ->
                    navController.navigate(AppRoute.PhotoDetail.createRoute(photoId))
                },
                onShiftClick = { sid ->
                    navController.navigate(AppRoute.ShiftDetail.createRoute(sid))
                },
            )
        }

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
        // FIX(2026-05-10) BELSI 1.3.0: AI-поиск фото
        composable(AppRoute.AiPhotoSearch.route) {
            com.belsi.work.presentation.screens.curator.ai.AiPhotoSearchScreen(
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

        // ────────── BELSI.Команда — производство (FIX 2026-05-05) ──────────
        // FIX(2026-05-06): Scaffold-обёртки с нижним таб-баром (как у монтажника).
        // Каждая роль имеет 4-5 вкладок: профильный экран + Партии + Чат + Профиль.
        composable(AppRoute.WorkerMain.route) {
            com.belsi.work.presentation.screens.factory.WorkerMainScaffold(navController)
        }
        composable(AppRoute.SeniorWorkerMain.route) {
            com.belsi.work.presentation.screens.factory.SeniorWorkerMainScaffold(navController)
        }
        composable(AppRoute.ProductionChiefMain.route) {
            com.belsi.work.presentation.screens.factory.ProductionChiefMainScaffold(navController)
        }
        composable(AppRoute.SupplierMain.route) {
            com.belsi.work.presentation.screens.factory.SupplierMainScaffold(navController)
        }
        composable(AppRoute.EngineerMain.route) {
            com.belsi.work.presentation.screens.factory.EngineerMainScaffold(navController)
        }
        composable(AppRoute.FactoryIdleReasons.route) {
            com.belsi.work.presentation.screens.factory.FactoryIdleReasonsScreen(navController)
        }
        composable(AppRoute.FactoryFacilitySwitch.route) {
            com.belsi.work.presentation.screens.factory.FactoryFacilitySwitchScreen(navController)
        }
        composable(AppRoute.RoleSelectV2.route) {
            com.belsi.work.presentation.screens.auth.roleselect.RoleSelectScreenV2(
                navController = navController,
                onContinue = { selectedRoles ->
                    // FIX(2026-05-05): после выбора → переходим в основной экран первой роли.
                    // ActiveRoleManager сохранит выбор. В будущем — отправить на сервер /user/me/roles.
                    val firstRole = selectedRoles.firstOrNull()
                    val target = when (firstRole) {
                        com.belsi.work.data.models.UserRole.FOREMAN -> AppRoute.ForemanMain.route
                        com.belsi.work.data.models.UserRole.COORDINATOR -> AppRoute.CoordinatorMain.route
                        com.belsi.work.data.models.UserRole.CURATOR -> AppRoute.CuratorMain.route
                        com.belsi.work.data.models.UserRole.PRODUCTION_CHIEF -> AppRoute.ProductionChiefMain.route
                        com.belsi.work.data.models.UserRole.SENIOR_WORKER -> AppRoute.SeniorWorkerMain.route
                        com.belsi.work.data.models.UserRole.WORKER -> AppRoute.WorkerMain.route
                        com.belsi.work.data.models.UserRole.SUPPLIER -> AppRoute.SupplierMain.route
                        com.belsi.work.data.models.UserRole.ENGINEER -> AppRoute.EngineerMain.route
                        // FIX(2026-05-12) build16: real routes вместо playground
                        com.belsi.work.data.models.UserRole.DRIVER -> AppRoute.DriverHome.route
                        com.belsi.work.data.models.UserRole.LOGISTICIAN -> AppRoute.LogisticianHome.route
                        else -> AppRoute.Main.route
                    }
                    navController.navigate(target) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        // Pipeline партии — общие экраны
        composable(AppRoute.BatchList.route) {
            com.belsi.work.presentation.screens.factory.BatchListScreen(navController)
        }
        composable(AppRoute.BatchCreate.route) {
            com.belsi.work.presentation.screens.factory.BatchCreateScreen(navController)
        }
        composable(
            route = AppRoute.BatchDetail.route,
            arguments = listOf(navArgument("batchId") { type = NavType.StringType })
        ) { backStackEntry ->
            val batchId = backStackEntry.arguments?.getString("batchId") ?: ""
            com.belsi.work.presentation.screens.factory.BatchDetailScreen(navController, batchId)
        }
        // FIX(2026-05-05): История объекта — для Координатора и Куратора
        composable(
            route = AppRoute.ObjectHistory.route,
            arguments = listOf(navArgument("objectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val objectId = backStackEntry.arguments?.getString("objectId") ?: ""
            com.belsi.work.presentation.screens.coordinator.ObjectHistoryScreen(navController, objectId)
        }
        // FIX(2026-05-05): Очередь pending-действий (offline-first)
        composable(AppRoute.PendingActions.route) {
            com.belsi.work.presentation.screens.offline.PendingActionsScreen(navController)
        }

        // More routes will be added as screens are implemented
        }  // close NavHost
    }  // close Column
}
