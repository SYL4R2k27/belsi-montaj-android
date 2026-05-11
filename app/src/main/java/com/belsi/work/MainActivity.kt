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
import com.belsi.work.presentation.components.GlobalRoleSwitcherFab
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
                    // Логистика — 2 роли (пока через playground как песочница)
                    UserRole.DRIVER -> AppRoute.DriverPlayground.route
                    UserRole.LOGISTICIAN -> AppRoute.DriverPlayground.route
                    // Installer и null
                    UserRole.INSTALLER -> AppRoute.Main.route
                    null -> AppRoute.Main.route
                }
            } else {
                AppRoute.AuthPhone.route
            }
        } catch (e: Exception) {
            AppRoute.AuthPhone.route
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

                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                        AppNavHost(
                            navController = navController,
                            startDestination = startDestination
                        )

                        // FIX(2026-05-06): Универсальная плавающая debug-кнопка
                        // переключения ролей — видна на ЛЮБОМ экране в debug-сборке.
                        // Решает проблему «застрял на DriverPlayground без bottom nav».
                        if (BuildConfig.DEBUG) {
                            GlobalRoleSwitcherFab()
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
}
