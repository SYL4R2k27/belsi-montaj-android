package com.belsi.work.presentation.screens.curator.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.belsi.work.data.models.AiPhotoSearchResponse
import com.belsi.work.data.models.AiPhotoSearchResultItem
import com.belsi.work.data.repositories.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-10) BELSI 1.3.0: NLP-поиск фото для куратора.
 *
 * Куратор пишет произвольный текст («селфи Иванова за неделю», «без касок»)
 * → AI парсит запрос в JSON-фильтр → backend возвращает релевантные фото.
 *
 * Endpoint: POST /curator/ai-photo-search → {intent_understood, explanation_ru, photos[], total_found}
 */

@HiltViewModel
class AiPhotoSearchViewModel @Inject constructor(
    private val aiRepository: AiRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AiPhotoSearchState())
    val state: StateFlow<AiPhotoSearchState> = _state.asStateFlow()

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, query = query)
            aiRepository.searchPhotos(query).onSuccess { response ->
                _state.value = _state.value.copy(
                    loading = false,
                    response = response,
                    error = null,
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    loading = false,
                    error = it.message ?: "Ошибка поиска",
                )
            }
        }
    }
}

data class AiPhotoSearchState(
    val query: String = "",
    val loading: Boolean = false,
    val response: AiPhotoSearchResponse? = null,
    val error: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPhotoSearchScreen(
    navController: NavController,
    viewModel: AiPhotoSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var queryText by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI-поиск фото") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        // FIX(2026-05-11) BELSI 2.0.0: max-width 900dp + центрирование на широких
        // экранах. NLP-поиск + список результатов — текстовая колонка с пред-просмотрами,
        // нет смысла растягивать на 1232dp Tab Ultra.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = 900.dp)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Search input
            OutlinedTextField(
                value = queryText,
                onValueChange = { queryText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Селфи Иванова за неделю · без касок · плохие за сегодня") },
                leadingIcon = { Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            keyboard?.hide()
                            viewModel.search(queryText)
                        },
                    ) {
                        Icon(Icons.Default.Search, "Найти")
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))

            // Подсказки запросов
            Text(
                "Опишите что ищете на естественном языке. AI поймёт и подберёт фото.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(16.dp))

            // Loading / Error / Results
            if (state.loading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        state.error ?: "",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            } else if (state.response != null) {
                val r = state.response!!

                // Что AI понял (объяснение)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🤖 ", fontSize = 14.sp)
                            Text(
                                if (r.intentUnderstood) "AI понял запрос" else "AI не уверен в запросе",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (r.intentUnderstood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                        if (r.explanationRu.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                r.explanationRu,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        // FIX(2026-05-12) build19 hotfix: "XeroCode" убран из UI.
                        Text(
                            "Найдено: ${r.totalFound}  ·  AI processing",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Результаты
                if (r.photos.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔍", fontSize = 40.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Ничего не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(r.photos) { photo ->
                            PhotoSearchResultCard(photo, onClick = {
                                navController.navigate("photo_detail/${photo.id}")
                            })
                        }
                    }
                }
            } else {
                // Initial state — нет ничего
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✨", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Введите запрос — AI найдёт нужные фото",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
        }  // FIX(2026-05-11): close Box max-width wrapper
    }
}

@Composable
private fun PhotoSearchResultCard(
    photo: AiPhotoSearchResultItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = photo.photoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    photo.userName ?: "—",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                if (photo.siteObjectName != null) {
                    Text(
                        "📍 ${photo.siteObjectName}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    photo.createdAt.take(16).replace('T', ' '),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (photo.aiScore != null) {
                    Spacer(Modifier.height(4.dp))
                    val color = when {
                        photo.aiScore >= 80 -> Color(0xFF10B981)
                        photo.aiScore >= 50 -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
                    }
                    Text(
                        "🤖 AI ${photo.aiScore}/100  ·  ${photo.aiCategory ?: "—"}",
                        fontSize = 11.sp,
                        color = color,
                        fontWeight = FontWeight.Medium,
                    )
                    if (!photo.aiComment.isNullOrBlank()) {
                        Text(
                            photo.aiComment,
                            fontSize = 11.sp,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}
