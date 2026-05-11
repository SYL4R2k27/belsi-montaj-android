package com.belsi.work.data.local

import android.content.SharedPreferences
import com.belsi.work.data.remote.dto.coordinator.CoordinatorSiteDto
import com.belsi.work.data.remote.dto.coordinator.UpdateSiteRequest
import com.belsi.work.data.repositories.CoordinatorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Локальное хранилище объектов координатора.
 * Бэкенд поддерживает только 1 объект (GET/PUT /coordinator/site),
 * но координатор может работать с несколькими объектами локально.
 * Только активный объект синхронизируется с сервером.
 */
class SiteStore(
    private val userPhone: String,
    private val prefs: SharedPreferences,
    private val repository: CoordinatorRepository? = null,
    private val keyPrefix: String = "coordinator"
) {
    private val storageKey = "${keyPrefix}_sites_${userPhone}"
    private val json = Json { ignoreUnknownKeys = true }

    private val _sites = MutableStateFlow<List<LocalSite>>(emptyList())
    val sites: StateFlow<List<LocalSite>> = _sites.asStateFlow()

    val activeSite: LocalSite?
        get() = _sites.value.firstOrNull { it.isActive }

    init {
        load()
    }

    fun addSite(name: String, address: String): LocalSite {
        val isFirst = _sites.value.isEmpty()
        val site = LocalSite(
            id = UUID.randomUUID().toString(),
            name = name,
            address = address,
            measurements = emptyMap(),
            comments = "",
            status = "active",
            isActive = isFirst, // первый объект сразу активен
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        _sites.value = _sites.value + site
        save()
        return site
    }

    fun selectSite(id: String) {
        _sites.value = _sites.value.map { site ->
            site.copy(isActive = site.id == id)
        }
        save()
    }

    fun updateSite(
        id: String,
        name: String? = null,
        address: String? = null,
        measurements: Map<String, String>? = null,
        comments: String? = null,
        status: String? = null
    ) {
        _sites.value = _sites.value.map { site ->
            if (site.id == id) {
                site.copy(
                    name = name ?: site.name,
                    address = address ?: site.address,
                    measurements = measurements ?: site.measurements,
                    comments = comments ?: site.comments,
                    status = status ?: site.status,
                    updatedAt = System.currentTimeMillis()
                )
            } else site
        }
        save()
    }

    fun deleteSite(id: String) {
        val wasActive = _sites.value.firstOrNull { it.id == id }?.isActive == true
        _sites.value = _sites.value.filter { it.id != id }

        // Если удалили активный, активируем первый из оставшихся
        if (wasActive && _sites.value.isNotEmpty()) {
            selectSite(_sites.value.first().id)
        }
        save()
    }

    /**
     * Синхронизация активного объекта С сервером → на сервер (PUT).
     * Только для keyPrefix == "coordinator".
     */
    suspend fun syncActiveToServer() {
        if (keyPrefix != "coordinator" || repository == null) return
        val active = activeSite ?: return

        // Проверяем, существует ли серверный объект
        val serverSite = repository.getSite().getOrNull() ?: return

        // PUT /coordinator/site
        repository.updateSite(
            UpdateSiteRequest(
                measurements = active.measurements.ifEmpty { null },
                comments = active.comments.ifBlank { null },
                status = active.status
            )
        )
    }

    /**
     * Синхронизация с сервера → в локальное хранилище (GET + merge).
     */
    suspend fun syncFromServer() {
        if (keyPrefix != "coordinator" || repository == null) return

        val serverSite = repository.getSite().getOrNull() ?: return

        val currentActive = activeSite
        if (currentActive != null) {
            // Мержим: серверные данные имеют приоритет
            updateSite(
                id = currentActive.id,
                name = serverSite.name.ifBlank { null },
                address = serverSite.address,
                measurements = if (serverSite.measurements.isNotEmpty())
                    currentActive.measurements + serverSite.measurements
                else null,
                comments = serverSite.comments,
                status = serverSite.status
            )
        } else if (_sites.value.isEmpty()) {
            // Нет локальных — создаём из серверного
            val site = LocalSite(
                id = serverSite.id,
                name = serverSite.name,
                address = serverSite.address ?: "",
                measurements = serverSite.measurements,
                comments = serverSite.comments ?: "",
                status = serverSite.status,
                isActive = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            _sites.value = listOf(site)
            save()
        }
    }

    private fun save() {
        val data = json.encodeToString(_sites.value)
        prefs.edit().putString(storageKey, data).apply()
    }

    private fun load() {
        val data = prefs.getString(storageKey, null) ?: return
        try {
            _sites.value = json.decodeFromString<List<LocalSite>>(data)
        } catch (e: Exception) {
            android.util.Log.e("SiteStore", "Failed to load sites", e)
        }
    }
}

@Serializable
data class LocalSite(
    val id: String,
    val name: String,
    val address: String,
    val measurements: Map<String, String> = emptyMap(),
    val comments: String = "",
    val status: String = "active",
    val isActive: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
