package com.belsi.work

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.data.models.UserRole
import com.belsi.work.presentation.navigation.AppNavHost
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.screens.update_gate.UpdateGateDialog
import com.belsi.work.presentation.screens.update_gate.UpdateGateViewModel
import com.belsi.work.presentation.theme.BelsiWorkTheme
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var prefsManager: PrefsManager

    // FIX(2026-04-30): мост между push-intent и Compose-навигацией.
    // Поток с маршрутом, который надо открыть после push'а.
    private val pendingDeepLink = MutableStateFlow<String?>(null)

    // FIX(2026-04-30): runtime-permission для POST_NOTIFICATIONS (Android 13+).
    // Без этого FCM push'и не доходят (system silently drops). Явно спрашиваем
    // на каждом холодном старте, если ещё не выдано.
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            android.util.Log.i("MainActivity", "POST_NOTIFICATIONS granted=$granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // FIX(2026-04-30): Splash screen — должен быть установлен ДО super.onCreate().
        // Показывает Steel-фон + BS иконку при холодном старте Android 12+.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // FIX(2026-05-14) BELSI 2.0.1: UI-test mode (по образцу Telegram-iOS).
        // При запуске с `adb shell am start ... --es ui-test true` (или intent
        // extra `belsi.uiTest=true`) приложение:
        //   1. Очищает PrefsManager (полный logout, никаких сохранённых токенов)
        //   2. Помечает себя test-mode через TestMode.isTestRun=true
        //   3. SMS-провайдер на бэке возвращает фейковый код (последние 4 цифры телефона)
        //
        // Дополнительно `--es clear-test-data true` принудительно очищает БД-кэш.
        // См. docs/ui-testing.md.
        handleUiTestArgs()

        // Android 13+ требует runtime-разрешение на показ уведомлений.
        // Без него FCM-пуши и локальные уведомления не отображаются.
        ensureNotificationPermission()

        // Determine start destination based on auth state and role BEFORE setContent
        // FIX(2026-05-05): добавлены 7 новых ролей (производство + логистика)
        val startDestination = try {
            if (prefsManager.getToken() != null) {
                when (prefsManager.getUser()?.role) {
                    UserRole.FOREMAN -> AppRoute.ForemanMain.route
                    UserRole.COORDINATOR -> AppRoute.CoordinatorMain.route
                    UserRole.CURATOR -> AppRoute.CuratorMain.route
                    // Производство — 5 ролей
                    UserRole.PRODUCTION_CHIEF -> AppRoute.ProductionChiefMain.route
                    UserRole.SENIOR_WORKER -> AppRoute.SeniorWorkerMain.route
                    UserRole.WORKER -> AppRoute.WorkerMain.route
                    UserRole.SUPPLIER -> AppRoute.SupplierMain.route
                    UserRole.ENGINEER -> AppRoute.EngineerMain.route
                    // FIX(2026-05-12) BELSI 2.0.0 build16: реальные routes вместо playground.
                    // Driver → DriverHome (точки маршрута + ShiftControlBar с photo),
                    // Logistician → LogisticianHome (KPI + Driver list + Create route).
                    UserRole.DRIVER -> AppRoute.DriverHome.route
                    UserRole.LOGISTICIAN -> AppRoute.LogisticianHome.route
                    // Installer и null
                    UserRole.INSTALLER -> AppRoute.Main.route
                    null -> AppRoute.Main.route
                }
            } else {
                // FIX(2026-05-12) build19+: приоритет = логин+пароль (вариант A Hero Card).
                // «Войти по СМС» доступен с экрана логина → AppRoute.AuthPhone.
                AppRoute.Login.route
            }
        } catch (e: Exception) {
            AppRoute.Login.route
        }

        // Сразу пробуем понять, не пришли ли мы из push'а
        handlePushIntent(intent)

        setContent {
            BelsiWorkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val deepLink by pendingDeepLink.asStateFlow().collectAsState()

                    LaunchedEffect(deepLink) {
                        val target = deepLink ?: return@LaunchedEffect
                        try {
                            navController.navigate(target) {
                                launchSingleTop = true
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("MainActivity", "Navigation failed: $target", e)
                        }
                        pendingDeepLink.value = null
                    }

                    // FIX(2026-05-11) BELSI 2.0.0: ГЛОБАЛЬНЫЙ imePadding на root.
                    // Причина: enableEdgeToEdge() в onCreate отключает работу
                    // android:windowSoftInputMode="adjustResize" из Manifest — Compose
                    // нужно явно подвинуть контент над клавиатурой. Применяем на root,
                    // покрывает все 33 экрана с TextField которые раньше уезжали под
                    // клавиатуру. У 6 экранов с локальным imePadding (Login/AuthPhone/
                    // OTP/SignUp/ChangePassword/InstallerChat/CuratorChat) — двойного
                    // отступа НЕ будет (Compose consume'ит insets автоматически).
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                    ) {
                        AppNavHost(
                            navController = navController,
                            startDestination = startDestination
                        )

                        // FIX(2026-05-11) BELSI 2.0.0 build8: offline-баннер сверху.
                        // Показывается если нет сети ИЛИ есть pending_actions в очереди.
                        // Брендбук р.05 (Offline mode для московских шатдаунов).
                        // OfflineBanner сам внутри делает Surface(fillMaxWidth) + статус-бар inset.
                        com.belsi.work.presentation.components.OfflineBanner()

                        // FIX(2026-05-06): Универсальная плавающая FAB переключения ролей.
                        // FIX(2026-05-11) BELSI 2.0.0 build3+4 (брендбук ecosystem 06):
                        // - RoleSwitcher FAB показывается **всем юзерам с >1 ролью** (универсальный)
                        // - При первом логине (если ещё нет активной роли) — auto-показ RoleSelectBottomSheet
                        // - Dev-юзер 9886 видит FAB всегда (для отладки UX любой роли)
                        val brandRoleVm: com.belsi.work.presentation.components.BrandRoleSwitcherViewModel =
                            hiltViewModel()
                        val brandRoleState by brandRoleVm.uiState.collectAsState()
                        var showAutoRolePicker by remember(brandRoleState.availableRoles.size) {
                            mutableStateOf(brandRoleState.availableRoles.size > 1 && brandRoleState.currentRole == null)
                        }

                        // FIX(2026-05-13) release/2.0.1-internal: убрана debug-only FAB
                        // GlobalRoleSwitcherFab. BrandRoleSwitcher по-прежнему доступен
                        // через bottom-sheet при логине (для multi-role пользователей).

                        // Auto bottom-sheet при логине если ролей >1 и нет активной
                        if (showAutoRolePicker && brandRoleState.availableRoles.size > 1) {
                            com.belsi.work.presentation.components.RoleSelectBottomSheet(
                                availableRoles = brandRoleState.availableRoles,
                                currentRole = brandRoleState.currentRole,
                                onRoleSelected = { role ->
                                    brandRoleVm.setActiveRole(role)
                                    showAutoRolePicker = false
                                },
                                onDismiss = { showAutoRolePicker = false },
                            )
                        }

                        // FIX(2026-05-11) BELSI 2.0.0 (B5, M2): Update Gate.
                        // Проверяем версию только когда юзер авторизован (для не-логиненных
                        // — нет user_id для записи согласия в audit-лог). Сервер вернёт
                        // update_required=true → ViewModel сам поднимет диалог.
                        if (prefsManager.getToken() != null) {
                            val updateGateVm: UpdateGateViewModel = hiltViewModel()
                            LaunchedEffect(Unit) { updateGateVm.checkVersion() }
                            UpdateGateDialog(viewModel = updateGateVm)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePushIntent(intent)
    }

    /**
     * FIX(2026-04-30): обработка push-навигации.
     *
     * Раньше extras типа open_messenger / open_chat / open_tasks / open_photos
     * приходили в MainActivity, но игнорировались — приложение всегда открывалось
     * на стартовом экране. Теперь мы транслируем их в Compose-роуты.
     */
    private fun handlePushIntent(intent: Intent?) {
        intent ?: return
        val extras = intent.extras ?: return

        // Прямой override: kотовый route целиком
        extras.getString("nav_route")?.let {
            pendingDeepLink.value = it
            return
        }

        // Тематические флаги из BelsiFirebaseMessagingService
        val target = when {
            extras.containsKey("open_messenger") -> {
                val threadId = extras.getString("thread_id")
                if (!threadId.isNullOrBlank())
                    AppRoute.MessengerConversation.createRoute(threadId)
                else null
            }
            extras.containsKey("open_chat") -> AppRoute.Chat.route
            extras.containsKey("open_tasks") -> {
                when (prefsManager.getUser()?.role) {
                    UserRole.FOREMAN -> AppRoute.ForemanMain.route
                    UserRole.CURATOR -> AppRoute.CuratorMain.route
                    UserRole.COORDINATOR -> AppRoute.CoordinatorMain.route
                    else -> AppRoute.Main.route
                }
            }
            extras.containsKey("open_photos") -> {
                when (prefsManager.getUser()?.role) {
                    UserRole.CURATOR -> AppRoute.CuratorPhotos.route
                    UserRole.FOREMAN -> AppRoute.ForemanPhotos.route
                    else -> AppRoute.PhotoGallery.route
                }
            }
            // FIX(2026-05-12) build19 Этап3+: deep-link на tool-transfer detail/hub.
            // FIX(2026-05-14) BELSI 2.0.1: расширено для return-flow событий.
            // Из push'а:
            //  data.kind = tool_transfer_arrived / accepted / rejected / dispatched
            //              tool_transfer_return_requested / return_driver_assigned /
            //              return_picked_up / return_ready_to_accept /
            //              return_completed / return_rejected
            //  data.transfer_id (если есть) → детальный экран (по статусу там action-кнопки)
            //  иначе → если return-* → CuratorReturns overview, остальное → hub на табе incoming
            extras.getString("kind")?.startsWith("tool_transfer") == true -> {
                val kind = extras.getString("kind") ?: ""
                val tid = extras.getString("transfer_id")
                val isReturn = kind.contains("return")
                when {
                    !tid.isNullOrBlank() -> AppRoute.ToolTransferDetail.createRoute(tid)
                    isReturn -> AppRoute.CuratorReturns.route
                    else -> AppRoute.ToolTransferHub.createRoute("incoming")
                }
            }
            // FIX(2026-05-14) BELSI 2.0.1: deep-link на маршрут водителя.
            // data.kind = route_point_delivered / route_assigned / route_started / route_completed
            // data.point_id + data.route_id → конкретная точка
            // иначе → DriverHome
            extras.getString("kind")?.startsWith("route_") == true ||
                extras.getString("kind")?.contains("route_point") == true -> {
                val pid = extras.getString("point_id")
                if (!pid.isNullOrBlank()) AppRoute.DriverPointDetail.createRoute(pid)
                else AppRoute.DriverHome.route
            }
            else -> null
        }
        if (target != null) pendingDeepLink.value = target
    }

    /**
     * FIX(2026-04-30): на Android 13+ (TIRAMISU) уведомления требуют отдельного
     * runtime-разрешения. Без него POST_NOTIFICATIONS = false → ВСЕ FCM-пуши
     * молча игнорируются системой.
     */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val granted = ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermissionLauncher.launch(permission)
        }
    }

    /**
     * FIX(2026-05-14) BELSI 2.0.1: обработка test-mode arguments.
     *
     * Активация:
     *   adb shell am start -n com.belsi.work.debug/com.belsi.work.MainActivity \
     *     --es ui-test true [--es clear-test-data true]
     *
     * Работает ТОЛЬКО в debug-сборках (BuildConfig.DEBUG=true).
     * В release-сборке метод — no-op (даже если кто-то попытается передать флаг).
     *
     * При активации:
     *  1. TestMode.isTestRun = true
     *  2. PrefsManager очищается (clearAll + markLaunched=false)
     *  3. Если --es clear-test-data true → TestMode.shouldClearTestData = true
     *     (Room-кэш чистится при инициализации AppDatabase)
     */
    private fun handleUiTestArgs() {
        if (!com.belsi.work.BuildConfig.DEBUG) return

        val extras = intent.extras ?: return
        val uiTest = extras.getString("ui-test")?.equals("true", ignoreCase = true) == true
            || extras.getBoolean("belsi.uiTest", false)
        if (!uiTest) return

        val clearData = extras.getString("clear-test-data")?.equals("true", ignoreCase = true) == true
            || extras.getBoolean("belsi.clearTestData", false)

        com.belsi.work.testmode.TestMode.activate(clearData = clearData)
        // Полный logout — никаких сохранённых токенов
        try {
            prefsManager.clearAll()
        } catch (e: Exception) {
            android.util.Log.w("MainActivity.test", "prefsManager.clearAll failed", e)
        }
        android.util.Log.i(
            "MainActivity.test",
            "UI test mode ACTIVATED (clearData=$clearData). " +
                "SMS codes are deterministic: last 4 digits of phone.",
        )
    }
}
