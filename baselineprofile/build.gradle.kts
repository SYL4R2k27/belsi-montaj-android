// FIX(2026-05-25): модуль генерации baseline-профиля (macrobenchmark).
// Прогон `./gradlew :app:generateBaselineProfile` собирает non-minified release
// :app, ставит на устройство/эмулятор, гоняет BaselineProfileGenerator (UiAutomator),
// собирает реальный ART-профиль стартового пути и пишет его в
// app/src/release/generated/baselineProfiles/ → AGP бейкает в release-APK.
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.belsi.work.baselineprofile"
    compileSdk = 34

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        // baseline profile capture требует API 28+ (UiAutomator + ART dump).
        minSdk = 28
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Целевое приложение, профиль которого генерируем.
    targetProjectPath = ":app"
}

baselineProfile {
    // Генерируем на подключённом устройстве/эмуляторе (arm64 на Apple Silicon).
    useConnectedDevices = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.1.5")
    implementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.3")
}
