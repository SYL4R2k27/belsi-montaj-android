// Top-level build file
// FIX(2026-05-12) build19: Kotlin 1.9.20 → 1.9.25 + KSP 1.9.20-1.0.14 → 1.9.25-1.0.20.
// Compose BOM 2024.12.01 содержит Compose 1.7.x, которой нужен compose-compiler >= 1.5.15.
// Compose-compiler 1.5.15 совместим только с Kotlin 1.9.25. Без этого fix —
// IntStack.peek2 ArrayIndexOutOfBoundsException при первой композиции
// NavigationSuiteScaffold / AnimatedVisibility (ABI mismatch).
plugins {
    id("com.android.application") version "8.4.2" apply false
    id("com.android.library") version "8.4.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
    id("com.google.dagger.hilt.android") version "2.48.1" apply false
    id("com.google.devtools.ksp") version "1.9.25-1.0.20" apply false
    id("com.google.gms.google-services") version "4.4.0" apply false
    id("com.google.firebase.crashlytics") version "2.9.9" apply false
}
