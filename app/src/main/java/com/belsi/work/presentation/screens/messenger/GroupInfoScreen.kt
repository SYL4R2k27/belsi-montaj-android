package com.belsi.work.presentation.screens.messenger

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.TokenManager
import com.belsi.work.data.models.ContactDTO
import com.belsi.work.data.models.ParticipantDTO
import com.belsi.work.data.models.ThreadDTO
import com.belsi.work.data.repositories.MessengerRepository
import com.belsi.work.presentation.theme.BelsiPrimary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ───

data class GroupInfoUiState(
    val thread: ThreadDTO? = null,
    val contacts: List<ContactDTO> = emptyList(),
    val currentUserId: String = "",
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val editName: String = "",
    val error: String? = null
)

@HiltViewModel
class GroupInfoViewModel @Inject constructor(
    private val messengerRepository: MessengerRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupInfoUiState())
    val uiState: StateFlow<GroupInfoUiState> = _uiState.asStateFlow()

    private var threadId: String = ""

    fun init(threadId: String) {
        this.threadId = threadId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                currentUserId = tokenManager.getUserId() ?: ""
            )
            // Load thread info
            messengerRepository.getThreads()
                .onSuccess { threads ->
                    val thread = threads.find { it.id == threadId }
                    _uiState.value = _uiState.value.copy(
                        thread = thread,
                        editName = thread?.name ?: "",
                        isLoading = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
                }
        }
    }

    fun loadContacts() {
        viewModelScope.launch {
            messengerRepository.getContacts()
                .onSuccess { _uiState.value = _uiState.value.copy(contacts = it) }
        }
    }

    fun startEditing() {
        _uiState.value = _uiState.value.copy(
            isEditing = true,
            editName = _uiState.value.thread?.name ?: ""
        )
    }

    fun cancelEditing() {
        _uiState.value = _uiState.value.copy(isEditing = false)
    }

    fun updateEditName(name: String) {
        _uiState.value = _uiState.value.copy(editName = name)
    }

    fun saveGroupName() {
        val name = _uiState.value.editName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            messengerRepository.updateThread(threadId, name)
                .onSuccess { updatedThread ->
                    _uiState.value = _uiState.value.copy(
                        thread = updatedThread,
                        isEditing = false,
                        error = null
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(error = "Ошибка: ${it.message}")
                }
        }
    }

    fun addMember(userId: String) {
        viewModelScope.launch {
            messengerRepository.addMembers(threadId, listOf(userId))
                .onSuccess { updatedThread ->
                    _uiState.value = _uiState.value.copy(thread = updatedThread, error = null)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(error = "Ошибка добавления: ${it.message}")
                }
        }
    }

    fun removeMember(userId: String) {
        viewModelScope.launch {
            messengerRepository.removeMember(threadId, userId)
                .onSuccess {
                    // Reload thread to refresh participant list
                    init(threadId)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(error = "Ошибка удаления: ${it.message}")
                }
        }
    }
}

// ─── Screen ───

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    threadId: String,
    onBack: () -> Unit,
    viewModel: GroupInfoViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var confirmRemoveUserId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(threadId) {
        viewModel.init(threadId)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Инфо о группе") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BelsiPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BelsiPrimary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // Group header
                item(key = "header") {
                    GroupHeader(
                        thread = uiState.thread,
                        isEditing = uiState.isEditing,
                        editName = uiState.editName,
                        onEditNameChange = { viewModel.updateEditName(it) },
                        onStartEditing = { viewModel.startEditing() },
                        onSave = { viewModel.saveGroupName() },
                        onCancel = { viewModel.cancelEditing() }
                    )
                }

                // Divider
                item(key = "divider1") {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }

                // Participants header
                item(key = "participants_header") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Участники (${uiState.thread?.participants?.size ?: 0})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = {
                            viewModel.loadContacts()
                            showAddMemberDialog = true
                        }) {
                            Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Добавить")
                        }
                    }
                }

                // Participant list
                val participants = uiState.thread?.participants ?: emptyList()
                items(participants, key = { it.userId }) { participant ->
                    ParticipantItem(
                        participant = participant,
                        isCurrentUser = participant.userId == uiState.currentUserId,
                        onRemove = {
                            confirmRemoveUserId = participant.userId
                        }
                    )
                }
            }
        }
    }

    // Add member dialog
    if (showAddMemberDialog) {
        val existingIds = uiState.thread?.participants?.map { it.userId }?.toSet() ?: emptySet()
        val availableContacts = uiState.contacts.filter { it.id !in existingIds }

        AddMemberDialog(
            contacts = availableContacts,
            onDismiss = { showAddMemberDialog = false },
            onAddMember = { contactId ->
                showAddMemberDialog = false
                viewModel.addMember(contactId)
            }
        )
    }

    // Confirm remove dialog
    if (confirmRemoveUserId != null) {
        val participant = uiState.thread?.participants?.find { it.userId == confirmRemoveUserId }
        AlertDialog(
            onDismissRequest = { confirmRemoveUserId = null },
            title = { Text("Удалить участника") },
            text = {
                Text("Удалить ${participant?.fullName ?: "участника"} из группы?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemoveUserId?.let { viewModel.removeMember(it) }
                        confirmRemoveUserId = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveUserId = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

// ─── Components ───

@Composable
private fun GroupHeader(
    thread: ThreadDTO?,
    isEditing: Boolean,
    editName: String,
    onEditNameChange: (String) -> Unit,
    onStartEditing: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Group avatar
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = BelsiPrimary.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Groups,
                    null,
                    Modifier.size(40.dp),
                    tint = BelsiPrimary
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (isEditing) {
            OutlinedTextField(
                value = editName,
                onValueChange = onEditNameChange,
                label = { Text("Название группы") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onCancel) {
                    Text("Отмена")
                }
                Button(
                    onClick = onSave,
                    enabled = editName.isNotBlank()
                ) {
                    Text("Сохранить")
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    thread?.displayName ?: "Группа",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onStartEditing,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        "Редактировать",
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (thread?.type == "group") {
            Text(
                "Группа · ${thread.participants.size} участников",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ParticipantItem(
    participant: ParticipantDTO,
    isCurrentUser: Boolean,
    onRemove: () -> Unit
) {
    val roleLabel = when (participant.userRole) {
        "installer" -> "Монтажник"
        "foreman" -> "Бригадир"
        "coordinator" -> "Координатор"
        "curator" -> "Куратор"
        else -> participant.userRole
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Person,
                        null,
                        Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        participant.fullName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isCurrentUser) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "(Вы)",
                            style = MaterialTheme.typography.bodySmall,
                            color = BelsiPrimary
                        )
                    }
                    if (participant.role == "admin") {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BelsiPrimary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                "Админ",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = BelsiPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (roleLabel.isNotBlank()) {
                    Text(
                        roleLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Remove button (only for non-current-user)
            if (!isCurrentUser) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.RemoveCircleOutline,
                        "Удалить",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddMemberDialog(
    contacts: List<ContactDTO>,
    onDismiss: () -> Unit,
    onAddMember: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить участника") },
        text = {
            if (contacts.isEmpty()) {
                Text(
                    "Все контакты уже в группе",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(contacts, key = { it.id }) { contact ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAddMember(contact.id) },
                            color = Color.Transparent
                        ) {
                            Row(
                                Modifier.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, null, Modifier.size(20.dp))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        contact.fullName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        contact.roleDisplayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}
