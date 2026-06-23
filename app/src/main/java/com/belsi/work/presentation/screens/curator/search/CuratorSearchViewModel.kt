package com.belsi.work.presentation.screens.curator.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.dto.curator.AllUserDto
import com.belsi.work.data.remote.dto.objects.SiteObjectDto
import com.belsi.work.data.repositories.CuratorRepository
import com.belsi.work.data.repositories.ObjectsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BELSI 2.1.0 — универсальный поиск куратора.
 * Грузит людей (getAllUsers) и объекты (getCuratorObjects) один раз; фильтрация — на экране,
 * по запросу. Разделы — статический список маршрутов. Фото — переход в AI-поиск фото.
 */
@HiltViewModel
class CuratorSearchViewModel @Inject constructor(
    private val curatorRepository: CuratorRepository,
    private val objectsRepository: ObjectsRepository,
) : ViewModel() {

    private val _users = MutableStateFlow<List<AllUserDto>>(emptyList())
    val users: StateFlow<List<AllUserDto>> = _users.asStateFlow()

    private val _objects = MutableStateFlow<List<SiteObjectDto>>(emptyList())
    val objects: StateFlow<List<SiteObjectDto>> = _objects.asStateFlow()

    init {
        viewModelScope.launch {
            // Универсальный поиск ищет среди ВСЕХ ролей (производство/логистика тоже), без 200-капа.
            curatorRepository.getAllUsers(allRoles = true, limit = 500).onSuccess { _users.value = it }
        }
        viewModelScope.launch {
            objectsRepository.getCuratorObjects().onSuccess { _objects.value = it }
        }
    }
}
