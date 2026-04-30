package com.belsi.work.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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

    fun clearAuthExtras() {
        encryptedPrefs.edit()
            .remove(KEY_USER_PHONE)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_ROLE)
            .apply()
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
    }
}
