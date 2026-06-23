pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BELSI.Work"
include(":app")
// FIX(2026-05-25): модуль генерации baseline-профиля (macrobenchmark).
// com.android.test модуль; прогон `:app:generateBaselineProfile` собирает
// сгенерированный профиль на устройстве/эмуляторе и бейкает его в release-APK.
include(":baselineprofile")
