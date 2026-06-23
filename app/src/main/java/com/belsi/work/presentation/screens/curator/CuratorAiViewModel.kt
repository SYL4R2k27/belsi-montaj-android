package com.belsi.work.presentation.screens.curator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.dto.object_v3.ObjectProgressDto
import com.belsi.work.data.repositories.ObjectV3Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Прогресс по 3 этапам на объект для Tab AI курaтора (мок 4.4) — реальные данные /v3/progress.
 */
@HiltViewModel
class CuratorAiViewModel @Inject constructor(
    private val repo: ObjectV3Repository,
) : ViewModel() {

    private val _progress = MutableStateFlow<List<ObjectProgressDto>>(emptyList())
    val progress: StateFlow<List<ObjectProgressDto>> = _progress.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            repo.getProgress().onSuccess { _progress.value = it }
        }
    }
}
