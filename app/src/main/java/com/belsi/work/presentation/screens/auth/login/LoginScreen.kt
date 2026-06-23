package com.belsi.work.presentation.screens.auth.login

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.models.SavedAccount
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.theme.Indigo100
import com.belsi.work.presentation.theme.Indigo200
import com.belsi.work.presentation.theme.Indigo600
import com.belsi.work.presentation.theme.Indigo700
import com.belsi.work.presentation.theme.Slate100
import com.belsi.work.presentation.theme.Slate200
import com.belsi.work.presentation.theme.Slate300
import com.belsi.work.presentation.theme.Slate400
import com.belsi.work.presentation.theme.Slate500
import com.belsi.work.presentation.theme.Slate600
import com.belsi.work.presentation.theme.Slate700
import com.belsi.work.presentation.theme.Slate900
import com.belsi.work.presentation.theme.SteelDeep
import com.belsi.work.presentation.theme.SteelMid
import com.belsi.work.presentation.theme.SteelTop
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthSdk

/**
 * FIX(2026-05-12) build19+: финальный экран логина (C2 · Mesh accent).
 *
 * Особенности:
 *   - BELSI brand-mark в Steel-blue градиенте с corner-glow и SVG-B-монограммой
 *   - Декоративный mesh-blob в правом верхнем углу (Steel + Indigo radial gradients)
 *   - Динамическое приветствие по 3 состояниям (см. Greeting.kt):
 *       FirstTime → «Добро пожаловать»
 *       Returning → ротация: С возвращением / Снова в работе / ...
 *       Personal  → ротация: С возвращением, Имя / Снова в деле, Имя / ...
 *   - Personal state показывает аватар-карточку с инициалами + телефоном + кнопкой «Сменить»
 *   - CTA Indigo (фирменный), 56dp, full-width
 *   - Yandex outlined-кнопка (на той же высоте)
 *   - «Войти по СМС» внизу — переход на новый AuthPhoneScreen (тот же стиль)
 *
 * Все размеры/отступы 1:1 с HTML-mockup (MDfile/login-variants.html · C2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val login by viewModel.login.collectAsState()
    val password by viewModel.password.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val greetingState by viewModel.greetingState.collectAsState()
    val savedAccounts by viewModel.savedAccounts.collectAsState()
    val openSwitcher by viewModel.openSwitcher.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    var showSwitcher by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }

    // Авто-открытие переключателя (приход из Settings → «Сменить аккаунт»).
    LaunchedEffect(openSwitcher) {
        if (openSwitcher) { showSwitcher = true; viewModel.consumeOpenSwitcher() }
    }
    if (showSwitcher) {
        AccountSwitcherSheet(
            accounts = savedAccounts,
            onSelect = { viewModel.selectAccount(it); showSwitcher = false },
            onRemove = { viewModel.removeAccount(it) },
            onAddAccount = { viewModel.addAccount(); showSwitcher = false },
            onDismiss = { showSwitcher = false },
        )
    }

    // Side-effects: errors → snackbar, navigation events → routing
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbar.showSnackbar(it, duration = SnackbarDuration.Short) }
    }
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            val target = when (event) {
                LoginNavigationEvent.NavigateToTerms -> AppRoute.Terms.route
                LoginNavigationEvent.NavigateToMain -> AppRoute.Main.route
                LoginNavigationEvent.NavigateToForemanMain -> AppRoute.ForemanMain.route
                LoginNavigationEvent.NavigateToCoordinatorMain -> AppRoute.CoordinatorMain.route
                LoginNavigationEvent.NavigateToCuratorMain -> AppRoute.CuratorMain.route
            }
            navController.navigate(target) {
                popUpTo(AppRoute.Login.route) { inclusive = true }
            }
        }
    }

    // Yandex AuthSDK
    val context = LocalContext.current
    val yandexSdk = remember { YandexAuthSdk.create(YandexAuthOptions(context)) }
    val yandexLauncher = rememberLauncherForActivityResult(contract = yandexSdk.contract) { result ->
        when (result) {
            is YandexAuthResult.Success -> viewModel.onYandexAuthResult(result.token.value)
            is YandexAuthResult.Failure -> viewModel.onYandexAuthError(
                result.exception.message ?: "Ошибка авторизации Яндекс"
            )
            YandexAuthResult.Cancelled -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color(0xFFEEF2FF),  // Indigo50 — соответствует mesh-фону
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding()
        ) {
            // ── Mesh background — атмосфера во весь экран с медленным дыханием ─
            MeshBackground(modifier = Modifier.matchParentSize())

            // FIX(2026-05-12) build19+: адаптив для fold/tablet — форма центрируется,
            // не растягивается на 2076dp inner-экран Pixel Fold.
            // FIX(2026-05-14): .fillMaxSize() ставил minWidth=screen.width и
            // ДОМИНИРОВАЛ над widthIn(max=480.dp) → форма растягивалась на всю
            // ширину Z Fold 5 unfolded (775dp). Поменяли на fillMaxHeight().
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = 480.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 28.dp),
            ) {
                Spacer(Modifier.height(72.dp))

                // ── BELSI brand mark (steel gradient + corner-glow + B-monogram) ──
                BelsiMark(size = 48.dp)

                Spacer(Modifier.height(40.dp))

                // ── Personal: аватар-баннер (если есть последний юзер) ──────
                val personal = greetingState as? GreetingState.Personal
                AnimatedVisibility(
                    visible = personal != null,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                ) {
                    if (personal != null) {
                        PersonalAccountBanner(
                            personal = personal,
                            onSwitchAccount = { showSwitcher = true },
                            modifier = Modifier.padding(bottom = 24.dp),
                        )
                    }
                }

                // ── Headline (динамический) ─────────────────────────────────
                val (headline, subhead) = remember(greetingState) {
                    when (val s = greetingState) {
                        GreetingState.FirstTime -> Greeting.headlineFirst() to Greeting.subheadFirst()
                        GreetingState.Returning -> Greeting.headlineReturning() to Greeting.subheadReturning()
                        is GreetingState.Personal -> Greeting.headlinePersonal(s.firstName) to Greeting.subheadPersonal()
                    }
                }

                Text(
                    text = headline,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate900,
                    fontSize = 40.sp,
                    letterSpacing = (-1.0).sp,
                    lineHeight = 44.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subhead,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Slate500,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                )

                Spacer(Modifier.height(32.dp))

                // ── Login field (скрыто в Personal state, т.к. логин уже известен) ──
                if (personal == null) {
                    FieldLabel("Логин")
                    OutlinedTextField(
                        value = login,
                        onValueChange = viewModel::onLoginChanged,
                        placeholder = { Text("Телефон, email или логин", color = Slate400) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next,
                            keyboardType = KeyboardType.Email,
                        ),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                        singleLine = true,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Slate900),
                        colors = darkTextFieldColors(),
                    )
                    Spacer(Modifier.height(20.dp))
                }

                // ── Password field ────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Пароль",
                        style = MaterialTheme.typography.labelLarge,
                        color = Slate700,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(
                        onClick = { navController.navigate(AppRoute.ForgotPassword.route) },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Text(
                            "Забыли?",
                            color = Indigo600,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = viewModel::onPasswordChanged,
                    placeholder = { Text("Минимум 6 символов", color = Slate400) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Password,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focus.clearFocus()
                        viewModel.login()
                    }),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = Slate500,
                            )
                        }
                    },
                    singleLine = true,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Slate900),
                    colors = darkTextFieldColors(),
                )

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(32.dp))

                // ── Primary CTA (Indigo, фирменный) ─────────────────────────
                Button(
                    onClick = {
                        focus.clearFocus()
                        viewModel.login()
                    },
                    enabled = !isLoading && password.isNotBlank()
                        && (personal != null || login.isNotBlank()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Indigo600,
                        contentColor = Color.White,
                        disabledContainerColor = Slate200,
                        disabledContentColor = Slate400,
                    ),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text(
                            Greeting.ctaText(greetingState is GreetingState.FirstTime),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                LabeledDivider("или через")
                Spacer(Modifier.height(16.dp))

                // ── Yandex ────────────────────────────────────────────────────
                OutlinedButton(
                    onClick = { yandexLauncher.launch(YandexAuthLoginOptions()) },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Slate900,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF0000)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Я",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Войти через Яндекс ID",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(Modifier.weight(1f, fill = true))
                Spacer(Modifier.height(28.dp))

                // ── Bottom actions ────────────────────────────────────────────
                FooterActions(
                    isFirstTime = greetingState is GreetingState.FirstTime,
                    onCreateAccount = { navController.navigate(AppRoute.SignUp.route) },
                    onLoginBySms = { navController.navigate(AppRoute.AuthPhone.route) },
                    enabled = !isLoading,
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    "BELSI.Команда v 2.0 · ООО ЗСО",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────
// Reusable components (also used by AuthPhone + OTP editorial screens)
// ────────────────────────────────────────────────────────────────────────

/**
 * BELSI brand mark — настоящая иконка приложения (mipmap/ic_launcher_foreground.webp).
 * BS + молоток + линейка на Steel-blue gradient — точная копия launcher-иконки.
 *
 * Реализация: Box со скруглением + наш Steel-gradient + foreground webp поверх.
 * Adaptive foreground имеет safe-zone padding ~33%, компенсируем через scale 1.5x.
 */
@Composable
fun BelsiMark(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val cornerRadius = size * 0.24f
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(SteelTop, SteelMid, SteelDeep),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Реальная иконка приложения (foreground layer adaptive-icon'а)
        Image(
            painter = painterResource(id = com.belsi.work.R.mipmap.ic_launcher_foreground),
            contentDescription = "BELSI",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Компенсируем safe-zone padding adaptive foreground (≈33% по краям)
                    scaleX = 1.5f
                    scaleY = 1.5f
                },
        )
    }
}

/**
 * Декоративный mesh — Indigo-доминантный фон с волновым «дыханием».
 *
 * Композиция (по слоям сверху вниз):
 *   - База: lavender wash (Indigo50) на весь экран
 *   - Слой 1: огромный Indigo blob (центр, медленный sin-pulse) — главный «вдох/выдох»
 *   - Слой 2: вторичный Indigo blob (top-right ↔ bottom-left, перекрёстный sin)
 *   - Слой 3: тёплый Steel-blob (для бренд-нотки)
 *   - Слой 4: белый шёлковый highlight (плывёт по диагонали, ripple-эффект)
 *
 * Дыхание реализовано через 2 phase-переменных с разной длительностью
 * (12 и 18 сек). Не синхронны → волны накладываются ритмично, не зацикленно.
 */
@Composable
fun MeshBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "mesh")

    // Фаза 1 — медленная (12 сек) — основное дыхание
    val phase1 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "phase1",
    )
    // Фаза 2 — длиннее (18 сек), несинхронно → волновое движение
    val phase2 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "phase2",
    )

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFEEF2FF),   // Indigo50 base
                    Color(0xFFE0E7FF),   // Indigo100
                    Color(0xFFEEF2FF),   // Indigo50 again
                )
            )
        ),
    ) {
        // ── Слой 1: основной Indigo «вдох/выдох» в центре ────────────────
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .blur(96.dp),
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Indigo600.copy(alpha = 0.32f + phase1 * 0.10f),
                        Indigo200.copy(alpha = 0.18f),
                        Color.Transparent,
                    ),
                ),
                radius = size.minDimension * (0.85f + phase1 * 0.10f),
                center = Offset(
                    x = size.width * (0.50f + (phase2 - 0.5f) * 0.15f),
                    y = size.height * (0.40f + (phase1 - 0.5f) * 0.10f),
                ),
            )
        }

        // ── Слой 2: вторичный Indigo (top-right ↔ bottom-left) ──────────
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .blur(72.dp),
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Indigo600.copy(alpha = 0.28f),
                        Indigo200.copy(alpha = 0.12f),
                        Color.Transparent,
                    ),
                ),
                radius = size.minDimension * (0.55f + (1f - phase2) * 0.08f),
                center = Offset(
                    x = size.width * (0.85f - phase2 * 0.25f),
                    y = size.height * (0.20f + phase1 * 0.30f),
                ),
            )
        }

        // ── Слой 3: Steel-blue бренд-нотка (mid-left) ─────────────────
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .blur(80.dp),
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SteelTop.copy(alpha = 0.18f + (1f - phase1) * 0.06f),
                        Color.Transparent,
                    ),
                ),
                radius = size.minDimension * 0.50f,
                center = Offset(
                    x = size.width * (0.10f + phase2 * 0.10f),
                    y = size.height * (0.65f - phase1 * 0.15f),
                ),
            )
        }

        // ── Слой 4: белый шёлковый highlight (плывёт диагонально) ───────
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .blur(64.dp),
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.15f),
                        Color.Transparent,
                    ),
                ),
                radius = size.minDimension * (0.35f + phase2 * 0.05f),
                center = Offset(
                    // Плывёт сверху-слева вниз-вправо по sin-волне
                    x = size.width * (0.20f + phase1 * 0.60f),
                    y = size.height * (0.30f + phase2 * 0.40f),
                ),
            )
        }
    }
}

/** Backward-compat: старое имя MeshBlob — теперь просто синоним. */
@Composable
fun MeshBlob(modifier: Modifier = Modifier) = MeshBackground(modifier = modifier)

/**
 * Personal-баннер — аватар + ФИО + телефон + «Сменить».
 */
@Composable
private fun PersonalAccountBanner(
    personal: GreetingState.Personal,
    onSwitchAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar with initials
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(Indigo600, SteelDeep))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    personal.initials,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    personal.fullName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate900,
                    maxLines = 1,
                )
                if (!personal.phoneDisplay.isNullOrBlank()) {
                    Text(
                        personal.phoneDisplay,
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500,
                        fontSize = 12.sp,
                    )
                }
            }
            TextButton(
                onClick = onSwitchAccount,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(
                    Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = Indigo600,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Сменить",
                    color = Indigo600,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * FIX(2026-05-22) BELSI 2.1.0: переключатель сохранённых аккаунтов (до 5).
 * Тап по профилю → предзаполняем логин и просим пароль. «Добавить аккаунт» →
 * чистый вход. ✕ — убрать профиль из списка.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitcherSheet(
    accounts: List<SavedAccount>,
    onSelect: (SavedAccount) -> Unit,
    onRemove: (String) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            // Цвета по теме листа (тема приложения тёмная) — иначе заголовок почти не виден.
            Text(
                "Аккаунты",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "До 5 профилей на устройстве. Переключение по паролю.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            accounts.forEach { acc ->
                AccountRow(acc = acc, onClick = { onSelect(acc) }, onRemove = { onRemove(acc.login) })
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onAddAccount() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEEF2FF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Indigo600)
                }
                Spacer(Modifier.width(14.dp))
                Text("Добавить аккаунт", fontWeight = FontWeight.SemiBold, color = Indigo600, fontSize = 15.sp)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AccountRow(
    acc: SavedAccount,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, Slate200, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Indigo600, SteelDeep))),
            contentAlignment = Alignment.Center,
        ) {
            Text(Greeting.initials(acc.name), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(acc.name, fontWeight = FontWeight.SemiBold, color = Slate900, fontSize = 15.sp, maxLines = 1)
            Text(
                acc.phoneDisplay?.takeIf { it.isNotBlank() } ?: acc.login,
                color = Slate500,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "Убрать", tint = Slate400, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * FIX(2026-05-12) build19+: единые TextField-цвета для всех auth-экранов.
 * Material 3 OutlinedTextFieldDefaults.colors() без явного focused/unfocusedTextColor
 * рендерит текст с альфой → выглядит как placeholder. Прибиваем явно Slate900.
 */
@Composable
internal fun darkTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Slate900,
    unfocusedTextColor = Slate900,
    disabledTextColor = Slate500,
    focusedBorderColor = Slate900,
    unfocusedBorderColor = Slate200,
    disabledBorderColor = Slate200,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = Slate100,
    cursorColor = Indigo600,
    focusedPlaceholderColor = Slate400,
    unfocusedPlaceholderColor = Slate400,
)

@Composable
internal fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Slate700,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(bottom = 6.dp),
    )
}

@Composable
internal fun LabeledDivider(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = Slate200)
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp),
            color = Slate400,
            fontSize = 12.sp,
            letterSpacing = 0.4.sp,
            fontWeight = FontWeight.Medium,
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = Slate200)
    }
}

@Composable
private fun FooterActions(
    isFirstTime: Boolean,
    onCreateAccount: () -> Unit,
    onLoginBySms: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (!isFirstTime) {
            Text("Нет аккаунта?", color = Slate500, fontSize = 14.sp)
            TextButton(
                onClick = onCreateAccount,
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text(
                    "Создать",
                    color = Indigo600,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        } else {
            Text("Уже есть аккаунт?", color = Slate500, fontSize = 14.sp)
            TextButton(
                onClick = { /* same screen — focus password */ },
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text(
                    "Войти",
                    color = Indigo600,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }
        Text("·", color = Slate300, modifier = Modifier.padding(horizontal = 4.dp))
        TextButton(
            onClick = onLoginBySms,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 6.dp),
        ) {
            Text(
                "Войти по СМС",
                color = Slate600,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
        }
    }
}
