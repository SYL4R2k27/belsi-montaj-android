package com.belsi.work.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FIX(2026-05-25): генератор baseline-профиля стартового пути BELSI.Work.
 *
 * `./gradlew :app:generateBaselineProfile` собирает non-minified release :app,
 * прогоняет холодный старт несколько раз и записывает реальный ART-профиль
 * (классы/методы стартового пути) в app/src/release/generated/baselineProfiles/.
 * AGP бейкает его в release-APK поверх курируемого app/src/main/baseline-prof.txt.
 *
 * Сценарий минимальный — холодный старт MainActivity (login/выбор роли/home),
 * т.к. именно cold-start и был узким местом (JIT-прогрев, см. perf-аудит 2026-05-22).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateStartupProfile() {
        baselineProfileRule.collect(
            packageName = "com.belsi.work",
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
        }
    }
}
