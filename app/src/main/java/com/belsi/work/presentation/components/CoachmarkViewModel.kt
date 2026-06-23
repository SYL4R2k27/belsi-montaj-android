package com.belsi.work.presentation.components

import androidx.lifecycle.ViewModel
import com.belsi.work.data.local.PrefsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * FIX(2026-05-21) BELSI 2.1.0: персист флага «обучение пройдено» по роли.
 * Ключ flag_coach_<role> в EncryptedSharedPreferences — coachmarks показываются
 * один раз при первом запуске каждой роли.
 */
@HiltViewModel
class CoachmarkViewModel @Inject constructor(
    private val prefs: PrefsManager,
) : ViewModel() {
    fun isSeen(roleKey: String): Boolean = prefs.getFlag("coach_$roleKey")
    fun markSeen(roleKey: String) = prefs.setFlag("coach_$roleKey", true)
}
