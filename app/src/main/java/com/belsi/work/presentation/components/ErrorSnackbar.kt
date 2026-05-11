package com.belsi.work.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.belsi.work.presentation.theme.belsiColors

/**
 * Компонент для отображения ошибок в виде Snackbar
 */
@Composable
fun ErrorSnackbarHost(
    snackbarHostState: SnackbarHostState
) {
    SnackbarHost(
        hostState = snackbarHostState,
        snackbar = { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                actionColor = MaterialTheme.colorScheme.onError
            )
        }
    )
}

/**
 * Компонент для отображения успешных сообщений
 */
@Composable
fun SuccessSnackbarHost(
    snackbarHostState: SnackbarHostState
) {
    SnackbarHost(
        hostState = snackbarHostState,
        snackbar = { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.belsiColors.success,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    )
}

/**
 * Универсальный Snackbar с настраиваемым цветом
 */
@Composable
fun BelsiSnackbarHost(
    snackbarHostState: SnackbarHostState,
    isError: Boolean = false
) {
    SnackbarHost(
        hostState = snackbarHostState,
        snackbar = { data ->
            Snackbar(
                snackbarData = data,
                containerColor = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = if (isError) {
                    MaterialTheme.colorScheme.onError
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
                actionColor = if (isError) {
                    MaterialTheme.colorScheme.onError
                } else {
                    MaterialTheme.colorScheme.onPrimary
                }
            )
        }
    )
}
