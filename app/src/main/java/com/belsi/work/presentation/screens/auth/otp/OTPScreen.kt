package com.belsi.work.presentation.screens.auth.otp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
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
import com.belsi.work.presentation.screens.auth.login.MeshBackground
import com.belsi.work.presentation.theme.Indigo600
import com.belsi.work.presentation.theme.Slate200
import com.belsi.work.presentation.theme.Slate400
import com.belsi.work.presentation.theme.Slate500
import com.belsi.work.presentation.theme.Slate600
import com.belsi.work.presentation.theme.Slate900

/**
 * FIX(2026-05-12) build19+: OTPScreen в editorial-стиле (C5b).
 *
 * Особенности:
 *   - 6 дискретных OTP-cells (квадратные, 12dp радиус), filled/focused border = Slate900
 *   - BasicTextField прячется под ячейками: принимает paste + SMS-autofill, рендерится через Row
 *   - Контекст «куда отправили» с возможностью изменить номер (back pill)
 *   - Таймер до повторной отправки → активная ссылка «Отправить заново»
 *   - Тот же mesh-blob что на Login/AuthPhone — визуальная преемственность
 *   - Возврат к паролю снизу (выход из SMS-flow)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OTPScreen(
    navController: NavController,
    phone: String,
    viewModel: OTPViewModel = hiltViewModel(),
) {
    val otpCode by viewModel.otpCode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val remainingTime by viewModel.remainingTime.collectAsState()
    val canResend by viewModel.canResend.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current
    val codeFocus = remember { FocusRequester() }

    LaunchedEffect(phone) {
        if (phone.isNotEmpty()) viewModel.setPhone(phone)
    }

    LaunchedEffect(Unit) {
        // Autofocus на скрытом OTP-поле
        codeFocus.requestFocus()
        viewModel.navigationEvent.collect { event ->
            val target = when (event) {
                is NavigationEvent.NavigateToTerms -> AppRoute.Terms.route
                NavigationEvent.NavigateToMain -> AppRoute.Main.route
                NavigationEvent.NavigateToForemanMain -> AppRoute.ForemanMain.route
                NavigationEvent.NavigateToCoordinatorMain -> AppRoute.CoordinatorMain.route
                NavigationEvent.NavigateToCuratorMain -> AppRoute.CuratorMain.route
            }
            navController.navigate(target) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbar.showSnackbar(it, duration = SnackbarDuration.Short) }
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
            MeshBackground(modifier = Modifier.matchParentSize())

            // FIX(2026-05-12) build19+: адаптив для fold/tablet
            // FIX(2026-05-14): .fillMaxSize() блокировал widthIn(max=480.dp).
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = 480.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 28.dp),
            ) {
                Spacer(Modifier.height(40.dp))

                // Back pill — «Изменить номер»
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { navController.popBackStack() },
                        )
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
                        "Изменить номер",
                        color = Slate600,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(Modifier.height(20.dp))

                BelsiMark(size = 40.dp)

                Spacer(Modifier.height(32.dp))

                Text(
                    "Введите код",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate900,
                    fontSize = 38.sp,
                    letterSpacing = (-1.0).sp,
                    lineHeight = 42.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Мы отправили 6-значный код на",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Slate500,
                    fontSize = 16.sp,
                )
                Text(
                    phone,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Slate900,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(Modifier.height(32.dp))

                // ── 6 OTP cells ─────────────────────────────────────────────
                Box {
                    // Скрытое BasicTextField — принимает ввод (paste + SMS-autofill)
                    BasicTextField(
                        value = otpCode,
                        onValueChange = viewModel::onOTPChanged,
                        modifier = Modifier
                            .matchParentSize()
                            .focusRequester(codeFocus),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
                        singleLine = true,
                        enabled = !isLoading,
                    )
                    // Визуальные ячейки
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { codeFocus.requestFocus() },
                            ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(6) { index ->
                            val ch = otpCode.getOrNull(index)?.toString() ?: ""
                            val isFocused = index == otpCode.length && !isLoading
                            val isFilled = ch.isNotEmpty()
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .border(
                                        width = if (isFocused) 2.dp else 1.dp,
                                        color = when {
                                            isFocused -> Slate900
                                            isFilled -> Slate900
                                            else -> Slate200
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    ch,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (canResend) {
                    TextButton(
                        onClick = { viewModel.resendOTP(phone) },
                        enabled = !isLoading,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            "Отправить заново",
                            color = Indigo600,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    Text(
                        "Отправить заново через 0:${remainingTime.toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = {
                        focus.clearFocus()
                        viewModel.verifyOTP(phone)
                    },
                    enabled = !isLoading && otpCode.length == 6,
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
                            "Войти",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(Modifier.weight(1f, fill = true))
                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(
                        onClick = {
                            navController.navigate(AppRoute.Login.route) {
                                popUpTo(AppRoute.AuthPhone.route) { inclusive = true }
                            }
                        },
                        enabled = !isLoading,
                    ) {
                        Text(
                            "Вернуться к паролю",
                            color = Indigo600,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
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
