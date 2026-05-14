package com.belsi.work.presentation.screens.auth.phone

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.screens.auth.login.BelsiMark
import com.belsi.work.presentation.screens.auth.login.FieldLabel
import com.belsi.work.presentation.screens.auth.login.MeshBackground
import com.belsi.work.presentation.screens.auth.login.darkTextFieldColors
import com.belsi.work.presentation.theme.Emerald500
import com.belsi.work.presentation.theme.Indigo600
import com.belsi.work.presentation.theme.Slate100
import com.belsi.work.presentation.theme.Slate200
import com.belsi.work.presentation.theme.Slate400
import com.belsi.work.presentation.theme.Slate500
import com.belsi.work.presentation.theme.Slate600
import com.belsi.work.presentation.theme.Slate700
import com.belsi.work.presentation.theme.Slate900
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthSdk

/**
 * FIX(2026-05-12) build19+: AuthPhoneScreen в editorial-стиле (C5).
 *
 * Single mode — это под-flow от логина, поэтому без приветствия.
 * Steel-chip «стоимость 0₽» снимает страх «спишут денег за СМС».
 * Mesh-blob тот же что на LoginScreen — визуальная преемственность.
 *
 * После «Получить код» → AppRoute.OTP с введённым телефоном.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthPhoneScreen(
    navController: NavController,
    viewModel: AuthPhoneViewModel = hiltViewModel(),
) {
    val phone by viewModel.phone.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // Yandex AuthSDK (keep for legacy parity; this screen теперь под-flow от LoginScreen)
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

    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbar.showSnackbar(it, duration = SnackbarDuration.Short) }
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is NavigationEvent.NavigateToOTP -> {
                    if (event.phone.isNotEmpty()) {
                        try {
                            navController.navigate(AppRoute.OTP.createRoute(event.phone))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                is NavigationEvent.NavigateToTerms -> {
                    navController.navigate(AppRoute.Terms.route) {
                        popUpTo(AppRoute.AuthPhone.route) { inclusive = true }
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color(0xFFEEF2FF),  // Indigo50 — соответствует mesh-фону
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            // Атмосфера во весь экран — медленное «дыхание»
            MeshBackground(modifier = Modifier.matchParentSize())

            // FIX(2026-05-12) build19+: адаптив для fold/tablet
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = 480.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 28.dp),
            ) {
                Spacer(Modifier.height(40.dp))

                // ── Back pill ────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickableSafe { navController.popBackStack() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = Slate600,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "К логину и паролю",
                        color = Slate600,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(Modifier.height(20.dp))

                BelsiMark(size = 40.dp)

                Spacer(Modifier.height(32.dp))

                Text(
                    "Вход по СМС",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate900,
                    fontSize = 38.sp,
                    letterSpacing = (-1.0).sp,
                    lineHeight = 42.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Введите номер телефона —\nмы отправим код подтверждения.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Slate500,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                )

                Spacer(Modifier.height(32.dp))

                FieldLabel("Номер телефона")
                OutlinedTextField(
                    value = phone,
                    onValueChange = viewModel::onPhoneChanged,
                    placeholder = { Text("+7 (___) ___-__-__", color = Slate400) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Phone,
                    ),
                    keyboardActions = KeyboardActions(onDone = { viewModel.sendOTP() }),
                    visualTransformation = PhoneVisualTransformation(),
                    singleLine = true,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Slate900),
                    colors = darkTextFieldColors(),
                )

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = { viewModel.sendOTP() },
                    enabled = !isLoading && phone.isNotEmpty(),
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
                            "Получить код",
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

                Spacer(Modifier.weight(1f, fill = true))
                Spacer(Modifier.height(28.dp))

                // ── Bottom links ────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(
                        onClick = { navController.popBackStack() },
                        enabled = !isLoading,
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Text(
                            "Вернуться к паролю",
                            color = Indigo600,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                    Text("·", color = Slate200, modifier = Modifier.padding(horizontal = 4.dp))
                    TextButton(
                        onClick = { navController.navigate(AppRoute.SignUp.route) },
                        enabled = !isLoading,
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Text(
                            "Создать аккаунт",
                            color = Slate600,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                    }
                }

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

/** Helper для clickable без ripple (для текстовых back-pills). */
@Composable
private fun Modifier.clickableSafe(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
    )
}
