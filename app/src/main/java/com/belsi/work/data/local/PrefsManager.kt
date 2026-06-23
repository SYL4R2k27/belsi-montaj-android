package com.belsi.work.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.belsi.work.data.models.SavedAccount
import com.belsi.work.data.models.User
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrefsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val encryptedPrefs: SharedPreferences by lazy {
        createEncryptedPrefs()
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "belsi_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            android.util.Log.e("PrefsManager", "Failed to create encrypted prefs: ${e.message}", e)

            // If it's a decryption error (AEADBadTagException), delete corrupted data and retry
            if (e.cause is javax.crypto.AEADBadTagException ||
                e is java.security.GeneralSecurityException) {
                android.util.Log.w("PrefsManager", "Detected corrupted encrypted prefs, clearing and retrying...")

                try {
                    // Delete corrupted encrypted preferences
                    context.getSharedPreferences("belsi_secure_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .clear()
                        .commit()

                    // Delete master key files
                    context.deleteFile("_androidx_security_master_key_")

                    // Retry creation
                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()

                    return EncryptedSharedPreferences.create(
                        context,
                        "belsi_secure_prefs",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                } catch (retryException: Exception) {
                    android.util.Log.e("PrefsManager", "Retry failed, using regular SharedPreferences", retryException)
                }
            }

            // Final fallback to regular SharedPreferences
            android.util.Log.w("PrefsManager", "Using unencrypted SharedPreferences as fallback")
            context.getSharedPreferences("belsi_secure_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    private val gson = Gson()

    /**
     * FIX(2026-05-22) perf: предварительный прогрев EncryptedSharedPreferences ВНЕ main-треда.
     * Первое обращение к `encryptedPrefs` строит MasterKey (Android Keystore + AES) — это
     * дорого и раньше происходило синхронно в MainActivity.onCreate ДО setContent → janky
     * первый кадр («Slow UI thread»). Вызывается из BelsiWorkApp.onCreate на Dispatchers.Default.
     * `by lazy` (SYNCHRONIZED) гарантирует, что повторное обращение из main просто дождётся
     * результата без двойной инициализации — поэтому хуже, чем раньше, стать не может.
     */
    fun warmUp() {
        runCatching { encryptedPrefs.getString(KEY_TOKEN, null) }
    }

    // Token Management
    fun saveToken(token: String) {
        encryptedPrefs.edit().putString(KEY_TOKEN, token).apply()
    }
    
    fun getToken(): String? = encryptedPrefs.getString(KEY_TOKEN, null)
    
    fun saveRefreshToken(refreshToken: String) {
        encryptedPrefs.edit().putString(KEY_REFRESH_TOKEN, refreshToken).apply()
    }
    
    fun getRefreshToken(): String? = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
    
    fun clearToken() {
        encryptedPrefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }
    
    // Auth extras (phone, userId, role)
    fun setUserPhone(phone: String) {
        encryptedPrefs.edit().putString(KEY_USER_PHONE, phone).apply()
    }

    fun getUserPhone(): String? = encryptedPrefs.getString(KEY_USER_PHONE, null)

    fun setUserId(userId: String) {
        encryptedPrefs.edit().putString(KEY_USER_ID, userId).apply()
    }

    fun getUserId(): String? = encryptedPrefs.getString(KEY_USER_ID, null)

    fun setUserRole(role: String) {
        encryptedPrefs.edit().putString(KEY_USER_ROLE, role).apply()
    }

    fun getUserRole(): String? = encryptedPrefs.getString(KEY_USER_ROLE, null)

    // Текущий кабинет монтажника («Сейчас работаю» — по факту фото/выбора).
    fun setCurrentCabinet(id: String?, label: String?) {
        encryptedPrefs.edit().putString("cur_cabinet_id", id).putString("cur_cabinet_label", label).apply()
    }
    fun getCurrentCabinetId(): String? = encryptedPrefs.getString("cur_cabinet_id", null)
    fun getCurrentCabinetLabel(): String? = encryptedPrefs.getString("cur_cabinet_label", null)

    // Текущий объект («Сейчас работаю») — нужен камере для каскада «Куда отнести фото?».
    fun setCurrentObject(objectId: String?) {
        encryptedPrefs.edit().putString("cur_object_id", objectId).apply()
    }
    fun getCurrentObjectId(): String? = encryptedPrefs.getString("cur_object_id", null)

    // Последний выбранный этап фото — для автозаполнения каскада (karkas|podokonnik|ekran).
    fun setLastPhotoStage(stage: String?) {
        encryptedPrefs.edit().putString("cur_last_stage", stage).apply()
    }
    fun getLastPhotoStage(): String? = encryptedPrefs.getString("cur_last_stage", null)

    fun clearAuthExtras() {
        encryptedPrefs.edit()
            .remove(KEY_USER_PHONE)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_ROLE)
            .apply()
    }

    // ─────────────────────────────────────────────────────────────────
    // FIX(2026-05-12) build19+: «last login context» для personalized
    // greeting на экране логина. Не очищается при logout — для UX
    // «С возвращением, Имя» с предзаполненным логином.
    // Чистится через clearLastLoginContext() при тапе «Сменить» или clearAll().
    // ─────────────────────────────────────────────────────────────────
    private val KEY_LAST_LOGIN = "last_login"
    private val KEY_LAST_USER_NAME = "last_user_name"
    private val KEY_LAST_USER_PHONE_DISPLAY = "last_user_phone_display"
    private val KEY_HAS_LAUNCHED = "has_launched_before"
    private val KEY_SAVED_ACCOUNTS = "saved_accounts"

    fun setLastLoginContext(login: String, name: String?, phoneDisplay: String?) {
        encryptedPrefs.edit()
            .putString(KEY_LAST_LOGIN, login)
            .putString(KEY_LAST_USER_NAME, name)
            .putString(KEY_LAST_USER_PHONE_DISPLAY, phoneDisplay)
            .putBoolean(KEY_HAS_LAUNCHED, true)
            .apply()
    }

    fun getLastLogin(): String? = encryptedPrefs.getString(KEY_LAST_LOGIN, null)
    fun getLastUserName(): String? = encryptedPrefs.getString(KEY_LAST_USER_NAME, null)
    fun getLastUserPhoneDisplay(): String? = encryptedPrefs.getString(KEY_LAST_USER_PHONE_DISPLAY, null)
    fun hasLaunchedBefore(): Boolean = encryptedPrefs.getBoolean(KEY_HAS_LAUNCHED, false)

    fun markLaunched() {
        encryptedPrefs.edit().putBoolean(KEY_HAS_LAUNCHED, true).apply()
    }

    fun clearLastLoginContext() {
        encryptedPrefs.edit()
            .remove(KEY_LAST_LOGIN)
            .remove(KEY_LAST_USER_NAME)
            .remove(KEY_LAST_USER_PHONE_DISPLAY)
            .apply()
    }

    // ── Мульти-аккаунт (до 5 сохранённых профилей, без токена) ──────────────
    // Хранится отдельным ключом → переживает logout (clearAuthData/clearUser его
    // не трогают). Для корп-телефонов: быстрое переключение между профилями.
    private val maxSavedAccounts = 5

    fun getSavedAccounts(): List<SavedAccount> {
        val json = encryptedPrefs.getString(KEY_SAVED_ACCOUNTS, null) ?: return emptyList()
        return try {
            // FIX(2026-05-22): Array<>, НЕ TypeToken<List<>> — у анонимного TypeToken
            // R8 (release) стирает generic-тип, и список читался как мусор → «не
            // сохранялось». Concrete Class<Array<SavedAccount>> R8-безопасен (модель в keep).
            gson.fromJson(json, Array<SavedAccount>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Добавляет/обновляет профиль, поднимает наверх (last-used), дедуп по login, кап 5. */
    fun upsertSavedAccount(acc: SavedAccount) {
        if (acc.login.isBlank()) return
        val key = acc.login.trim().lowercase()
        val current = getSavedAccounts().filterNot { it.login.trim().lowercase() == key }
        val updated = (listOf(acc) + current).take(maxSavedAccounts)
        encryptedPrefs.edit().putString(KEY_SAVED_ACCOUNTS, gson.toJson(updated)).apply()
    }

    fun removeSavedAccount(login: String) {
        val key = login.trim().lowercase()
        val updated = getSavedAccounts().filterNot { it.login.trim().lowercase() == key }
        encryptedPrefs.edit().putString(KEY_SAVED_ACCOUNTS, gson.toJson(updated)).apply()
    }

    // User Management
    fun saveUser(user: User) {
        val json = gson.toJson(user)
        encryptedPrefs.edit().putString(KEY_USER, json).apply()
    }
    
    fun getUser(): User? {
        val json = encryptedPrefs.getString(KEY_USER, null)
        return if (json != null) {
            try {
                gson.fromJson(json, User::class.java)
            } catch (e: Exception) {
                null
            }
        } else null
    }
    
    fun clearUser() {
        encryptedPrefs.edit().remove(KEY_USER).apply()
    }
    
    // Onboarding & Settings
    fun setOnboardingCompleted(completed: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }
    
    fun isOnboardingCompleted(): Boolean =
        encryptedPrefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    
    fun setTermsAccepted(accepted: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_TERMS_ACCEPTED, accepted).apply()
    }

    fun areTermsAccepted(): Boolean =
        encryptedPrefs.getBoolean(KEY_TERMS_ACCEPTED, false)

    // FIX(2026-05-21): generic one-shot flags (например first-run coachmarks по ролям).
    fun setFlag(key: String, value: Boolean) {
        encryptedPrefs.edit().putBoolean("flag_$key", value).apply()
    }

    fun getFlag(key: String, default: Boolean = false): Boolean =
        encryptedPrefs.getBoolean("flag_$key", default)

    // FIX(2026-05-11) BELSI 2.0.0: per-version consent для Update Gate.
    // Используется чтобы Update Gate показался ОДИН РАЗ при first-launch
    // конкретной major-версии (1.2.5 → 2.0.0). Ключ привязан к версии — при
    // следующем major-апгрейде (3.0.0) флаг для 2.0.0 не помешает показать
    // gate для 3.0.0. Параллельно с локальным флагом летит POST на сервер
    // (см. UpdateGateRepository.submitConsent → /audit/update-consent).
    fun setUpdateConsentSigned(toVersion: String) {
        encryptedPrefs.edit()
            .putBoolean("update_consent_signed_$toVersion", true)
            .apply()
    }

    fun hasUpdateConsent(toVersion: String): Boolean =
        encryptedPrefs.getBoolean("update_consent_signed_$toVersion", false)

    // FIX(2026-06-16): кэш id принятых пунктов согласия — для офлайн-фолбэка
    // Update Gate. Раньше плашка показывалась «по версии» (вылазила при каждом
    // обновлении), теперь — «по пунктам» (только непринятые). Сервер
    // (/audit/consents/accepted) — источник истины; этот кэш используется, если
    // сети нет. StringSet возвращает копию — defensively копируем перед записью.
    fun getAcceptedConsentIds(): Set<String> =
        encryptedPrefs.getStringSet(KEY_ACCEPTED_CONSENT_IDS, emptySet())?.toSet() ?: emptySet()

    fun setAcceptedConsentIds(ids: Set<String>) {
        encryptedPrefs.edit()
            .putStringSet(KEY_ACCEPTED_CONSENT_IDS, ids.toSet())
            .apply()
    }

    // App Settings
    fun setNotificationsEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }
    
    fun areNotificationsEnabled(): Boolean =
        encryptedPrefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
    
    fun setLocationTrackingEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_LOCATION_TRACKING, enabled).apply()
    }
    
    fun isLocationTrackingEnabled(): Boolean =
        encryptedPrefs.getBoolean(KEY_LOCATION_TRACKING, true)
    
    // AI Analysis Display
    fun setAiAnalysisVisible(visible: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_AI_ANALYSIS_VISIBLE, visible).apply()
    }

    fun isAiAnalysisVisible(): Boolean =
        encryptedPrefs.getBoolean(KEY_AI_ANALYSIS_VISIBLE, true)

    // Session Management
    fun getLastSyncTime(): Long =
        encryptedPrefs.getLong(KEY_LAST_SYNC_TIME, 0L)
    
    fun setLastSyncTime(time: Long) {
        encryptedPrefs.edit().putLong(KEY_LAST_SYNC_TIME, time).apply()
    }
    
    // Clear All Data
    fun clear() {
        encryptedPrefs.edit().clear().apply()
    }

    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }
    
    // Password Protection
    fun setAppPassword(password: String) {
        // Хэшируем пароль перед сохранением
        val hashedPassword = hashPassword(password)
        encryptedPrefs.edit().putString(KEY_APP_PASSWORD, hashedPassword).apply()
        android.util.Log.d("PrefsManager", "App password set successfully")
    }

    fun checkAppPassword(password: String): Boolean {
        val storedHash = encryptedPrefs.getString(KEY_APP_PASSWORD, null)
        if (storedHash == null) {
            return true // Пароль не установлен - доступ разрешён
        }
        val inputHash = hashPassword(password)
        return storedHash == inputHash
    }

    fun hasAppPassword(): Boolean {
        return encryptedPrefs.contains(KEY_APP_PASSWORD)
    }

    fun removeAppPassword() {
        encryptedPrefs.edit().remove(KEY_APP_PASSWORD).apply()
        android.util.Log.d("PrefsManager", "App password removed")
    }

    /**
     * Простое хэширование пароля (SHA-256)
     */
    private fun hashPassword(password: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(password.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER = "current_user"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_TERMS_ACCEPTED = "terms_accepted"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_LOCATION_TRACKING = "location_tracking"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_APP_PASSWORD = "app_password_hash"
        private const val KEY_AI_ANALYSIS_VISIBLE = "ai_analysis_visible"
        private const val KEY_ACCEPTED_CONSENT_IDS = "accepted_consent_ids"
    }
}
