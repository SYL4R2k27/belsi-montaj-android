package com.belsi.work.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.belsi.work.data.models.UserRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.activeRoleDataStore by preferencesDataStore(name = "active_role_prefs")

/**
 * FIX(2026-05-03): хранит активную capability пользователя для мульти-роли
 * (Driver+Installer / Curator+Logistician / Worker+Driver+Installer etc).
 *
 * FIX(2026-05-05): расширено до 3 ролей и добавлен переключатель фабрик
 * (для производственных ролей — пользователь может работать на разных площадках).
 *
 * Brandbook: переключатель в шапке (вариант 1.D) — пользователь выбирает в каком
 * режиме работать сейчас. Выбор сохраняется в DataStore и переживает рестарт app.
 *
 * Если у пользователя одна capability — этот менеджер возвращает её и переключатель
 * в UI не показывается.
 */
@Singleton
class ActiveRoleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activeRoleKey = stringPreferencesKey("active_role")
    // FIX(2026-05-05): активная производственная фабрика (site_object_id для production-роли)
    private val activeFacilityKey = stringPreferencesKey("active_facility_id")
    // FIX(2026-05-11) BELSI 2.0.0 build3: список доступных юзеру ролей (для RoleSwitcher).
    // Заполняется после /user/me/roles при логине. Разделитель — запятая.
    private val availableRolesKey = stringPreferencesKey("available_roles")

    /**
     * Текущая активная роль. Поток обновляется при переключении.
     * null = ещё не выбрано (дефолт = первая capability юзера).
     */
    val activeRole: Flow<UserRole?> = context.activeRoleDataStore.data.map { prefs ->
        prefs[activeRoleKey]?.let { roleStr ->
            try { UserRole.valueOf(roleStr) } catch (_: Exception) { null }
        }
    }

    /**
     * FIX(2026-05-05): активная производственная фабрика.
     * Используется только для ролей домена PRODUCTION (производство мебели).
     * Хранится как UUID-строка site_object_id.
     * null = ещё не выбрано (если у юзера несколько фабрик — нужно показать переключатель).
     */
    val activeFacilityId: Flow<String?> = context.activeRoleDataStore.data.map { prefs ->
        prefs[activeFacilityKey]
    }

    /** Установить активную роль. Должна быть одной из capabilities пользователя. */
    suspend fun setActiveRole(role: UserRole) {
        context.activeRoleDataStore.edit { prefs ->
            prefs[activeRoleKey] = role.name
        }
    }

    /**
     * FIX(2026-05-05): установить активную фабрику.
     * Обычно вызывается при первом входе под производственной ролью или
     * через переключатель «Текущая фабрика» в шапке.
     */
    suspend fun setActiveFacility(facilityId: String) {
        context.activeRoleDataStore.edit { prefs ->
            prefs[activeFacilityKey] = facilityId
        }
    }

    /** Сбросить активную роль (например, при logout). */
    suspend fun clear() {
        context.activeRoleDataStore.edit { prefs ->
            prefs.remove(activeRoleKey)
            prefs.remove(activeFacilityKey)
            prefs.remove(availableRolesKey)
        }
    }

    /**
     * FIX(2026-05-11) BELSI 2.0.0 build3: список доступных ролей юзера.
     * Заполняется после /user/me/roles. RoleSwitcher показывается если size > 1.
     */
    val availableRoles: Flow<List<UserRole>> = context.activeRoleDataStore.data.map { prefs ->
        val raw = prefs[availableRolesKey] ?: return@map emptyList()
        raw.split(",").mapNotNull { name ->
            try { UserRole.valueOf(name.trim()) } catch (_: Exception) { null }
        }
    }

    suspend fun setAvailableRoles(roles: List<UserRole>) {
        context.activeRoleDataStore.edit { prefs ->
            prefs[availableRolesKey] = roles.joinToString(",") { it.name }
        }
    }
}
