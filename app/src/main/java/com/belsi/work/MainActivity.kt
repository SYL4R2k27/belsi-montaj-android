package com.belsi.work

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.data.models.UserRole
import com.belsi.work.presentation.navigation.AppNavHost
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.theme.BelsiWorkTheme
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

    override fun onCreate(savedInstanceState: Bundle?) {
        // FIX(2026-04-30): Splash screen — должен быть установлен ДО super.onCreate().
        // Показывает Steel-фон + BS иконку при холодном старте Android 12+.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Determine start destination based on auth state and role BEFORE setContent
        val startDestination = try {
            if (prefsManager.getToken() != null) {
                when (prefsManager.getUser()?.role) {
                    UserRole.FOREMAN -> AppRoute.ForemanMain.route
                    UserRole.COORDINATOR -> AppRoute.CoordinatorMain.route
                    UserRole.CURATOR -> AppRoute.CuratorMain.route
                    else -> AppRoute.Main.route
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

                    AppNavHost(
                        navController = navController,
                        startDestination = startDestination
                    )
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
}
