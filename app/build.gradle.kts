import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    // FIX(2026-05-25): консьюмер сгенерированного baseline-профиля (см. :baselineprofile).
    id("androidx.baselineprofile")
    kotlin("plugin.serialization") version "1.9.25"
}

// FIX(2026-04-30): Gradle НЕ парсит local.properties в project.properties по умолчанию.
// findProperty("YANDEX_CLIENT_ID") возвращал null → manifestPlaceholder = "" →
// Yandex AuthSDK получал пустой client_id → ошибка 400 "Missing client_id parameter".
// Загружаем local.properties вручную.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
// FIX(2026-05-01): build label внутри минорной версии — независимо от versionCode.
// versionCode должен монотонно расти (для in-app force-update), а внутри 1.2.5
// мы хотим начать счётчик заново: build1, build2, ...
val versionMinorBuild: Int = (project.findProperty("MINOR_BUILD") as? String)?.toIntOrNull() ?: 1

val yandexClientId: String =
    localProps.getProperty("YANDEX_CLIENT_ID")
        ?: (project.findProperty("YANDEX_CLIENT_ID") as? String)
        ?: System.getenv("YANDEX_CLIENT_ID")
        ?: ""

if (yandexClientId.isBlank()) {
    println("WARN: YANDEX_CLIENT_ID is empty. Yandex OAuth will fail with 400.")
}

// Yandex MapKit (driver-map). Ключ — в local.properties (gitignored), НЕ в git.
val yandexMapKitKey: String =
    localProps.getProperty("YANDEX_MAPKIT_KEY")
        ?: System.getenv("YANDEX_MAPKIT_KEY")
        ?: ""
if (yandexMapKitKey.isBlank()) {
    println("WARN: YANDEX_MAPKIT_KEY is empty — карта водителя не отрисуется.")
}

android {
    namespace = "com.belsi.work"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.belsi.work"
        minSdk = 26
        targetSdk = 34
        // FIX(2026-05-11): bump 1.3.0 → 2.0.0 — major релиз: интеграция XeroCode AI Office,
        // 8 AI use cases, полный AI UI в Compose, driver/logistician backend wiring,
        // 3 security fixes, Update Gate URL allowlist, role-switcher.
        // build3 (versionCode 16): brand-core от брендбука — мульти-роль, объект как нить,
        //   pipeline партии связки, timeline объекта, idle reasons по доменам, universal RoleSwitcher.
        // build4 (versionCode 17): capability matrix, push routing, adaptive deps, auto bottom-sheet, role switch dialog.
        // build5 (versionCode 18): полный adaptive UI-комплит из foldable-tablet-design-guide.
        // build6 (versionCode 19): rename приложения «Belsi.Монтаж» → «BELSI.Команда».
        //   Брендбук BELSI.Команда раздел 04: правильное написание — заглавные BELSI,
        //   точка-разделитель, кириллическое «Команда» с заглавной К.
        //   Обновлено: strings.xml app_name, AuthPhone/LoginScreen titles, AboutScreen,
        //   ShiftWidget, BelsiFirebaseMessagingService fallback title.
        // FIX(2026-05-13): release/2.0.1-internal — релизный пак без публикации.
        // versionCode 20 (build7): bump для 2.0.1.
        //   Включает: Tool Transfer pipeline, brand-core multi-role, Login redesign C2,
        //   AI Dashboard «Проблемные монтажники» enriched, pause race-condition fix,
        //   server-side guard /shift/pause/start, удалён debug-стек
        //   (DevUserGuard, GlobalRoleSwitcherFab, RoleSwitchDialog, RoleSwitcherDialog,
        //   RoleTestPlaygroundScreen, PlaygroundDestination).
        // FIX(2026-05-21): bump 2.0.1 → 2.1.0 (versionCode 21) — UI/UX rewrite «Монтаж»
        //   (object schema v3, cabinet detail, installer «Сейчас работаю», curator stage progress).
        //   Первая release-сборка ветки feature/ui-2.1.0 (com.belsi.work, release keystore).
        // FIX(2026-06-08): bump 2.1.0 → 2.1.1 (versionCode 22) — редизайн бригадира (M3 фейтфул)
        //   + предстоящий мелкий редизайн монтажника (эмодзи → нормальные иконки).
        // FIX(2026-06-19): bump 2.1.5 → 2.1.6 (versionCode 27) — security-hardened сборка:
        //   токен убран из logcat (MessengerWebSocket — фикс лога), поверх бэкенд-фиксов безопасности.
        // FIX(2026-06-23): bump 2.1.6 → 2.1.7 (versionCode 28) — inline-переключатель ролей
        //   на standalone-экранах логиста/водителя (мультироль больше не застревает).
        versionCode = 28
        versionName = "2.1.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "API_BASE_URL", "\"https://api.belsi.ru/\"")
        buildConfigField("String", "YANDEX_MAPKIT_KEY", "\"$yandexMapKitKey\"")

        // Yandex OAuth Client ID — set in local.properties: YANDEX_CLIENT_ID=your_id
        manifestPlaceholders["YANDEX_CLIENT_ID"] = yandexClientId

        // FIX(2026-05-25): MapKit native libs ×4 ABI раздували APK до ~113 МБ.
        // Шаг 1 (56 МБ): оставили arm64 + arm32, выкинули x86/x86_64 (эмулятор).
        // Шаг 2 (2026-05-25, ~38 МБ): по решению пользователя оставляем ТОЛЬКО arm64-v8a.
        //   ⚠️ Это отрезает 32-битные телефоны (armeabi-v7a). Допущение: все боевые
        //   устройства 14-ти монтажников 64-битные. Если найдётся 32-бит телефон —
        //   вернуть "armeabi-v7a" в список и пересобрать (APK снова ~56 МБ).
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // FIX(2026-04-30): release signing config для Yandex OAuth.
    // Yandex AuthSDK валидирует SHA256 сертификата подписи APK.
    // Кейстор живёт в /keystore/ (НЕ в git — добавлен в .gitignore).
    // Параметры из keystore.properties (тоже в .gitignore).
    signingConfigs {
        create("release") {
            val ksPropsFile = rootProject.file("keystore.properties")
            if (ksPropsFile.exists()) {
                val props = Properties()
                FileInputStream(ksPropsFile).use { props.load(it) }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // FIX(2026-05-03): driver-integration debug-вариант.
        // .debug-суффикс позволяет держать прод 1.2.5 (release) и driver-test (debug)
        // одновременно на одном устройстве. Firebase google-services.json расширен
        // дополнительным client_info для com.belsi.work.debug.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-driver"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        // FIX(2026-05-11) BELSI 2.0.0: side-by-side preview-сборка.
        // Те же настройки что release (minify + shrink + release-signing),
        // но с .preview applicationId — ставится ОТДЕЛЬНОЙ иконкой рядом с
        // прод (com.belsi.work) и driver-test (com.belsi.work.debug).
        // app_name переопределён в src/preview/res/values/strings.xml.
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
        }
    }

    // FIX(2026-05-01): имя APK = versionName + minor-build-counter + buildType.
    // Пример: BELSI_Montaj_1.2.5_build1_debug.apk.
    // versionMinorBuild — счётчик внутри минорной версии (сбрасывается при bump 1.2.5→1.2.6).
    // versionCode остаётся монотонным в android-метаданных (для force-update).
    applicationVariants.all {
        val variant = this
        outputs.all {
            val out = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            out.outputFileName =
                "BELSI_Montaj_${variant.versionName}_build${versionMinorBuild}_${variant.buildType.name}.apk"
        }
    }

    // FIX(2026-04-30): не блокировать release-build из-за lint-предупреждений.
    // Lint-ошибки из data_extraction_rules.xml — старые, не связаны с релизом.
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        // FIX(2026-05-12) build19: 1.5.4 → 1.5.15. Compose BOM 2024.12.01 (Compose 1.7.x)
        // требует compose-compiler ≥ 1.5.15. Совместим с Kotlin 1.9.25.
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/ASL2.0"
            excludes += "/META-INF/*.kotlin_module"
        }
    }
}

// FIX(2026-05-11) BELSI 2.0.0: отключаем Crashlytics mapping-upload для preview-сборки.
// Причина: .preview applicationId не зарегистрирован в Firebase Console (мы только
// зеркалили запись `.debug` в google-services.json для прохождения проверки плагина).
// Сам Crashlytics в APK будет работать, отключается ТОЛЬКО upload de-obfuscation map.
// Это локальная диагностическая pre-grade-сборка для разработчика — символьные стектрейсы
// можно собрать вручную из mappings/preview/mapping.txt при необходимости.
afterEvaluate {
    tasks.findByName("uploadCrashlyticsMappingFilePreview")?.enabled = false
}

dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    // FIX(2026-05-11) BELSI 2.0.0 build4: Material3 adaptive — list-detail pane scaffold
    // для табов куратора (Tickets / Photos / People / Brigades / Objects / Tasks / Chat).
    // Брендбук foldable-tablet-design-guide раздел 17.
    implementation("androidx.compose.material3.adaptive:adaptive:1.0.0")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.0.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.0.0")
    // FIX(2026-05-11) BELSI 2.0.0 build5: androidx.window для FoldingFeature posture API.
    // Используется в CameraScreen — при TableTop posture (Z Fold лежит как ноутбук
    // 90°) разделяем экран на preview сверху + controls снизу.
    // Брендбук foldable-tablet раздел 03 + 13.
    implementation("androidx.window:window:1.3.0")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.compose.material:material-icons-extended")

    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    // Splash screen API (compatible with API 23+)
    implementation("androidx.core:core-splashscreen:1.0.1")
    // WindowSizeClass — для адаптивного UI на foldables / tablets
    implementation("androidx.compose.material3:material3-window-size-class:1.2.1")
    // Adaptive layouts — TwoPane / SupportingPane (master-detail)
    implementation("androidx.compose.material3.adaptive:adaptive:1.0.0")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.0.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.48.1")
    ksp("com.google.dagger:hilt-android-compiler:2.48.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Retrofit + Kotlinx Serialization
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // Coil
    implementation("io.coil-kt:coil-compose:2.5.0")

    // QR (генерация кода бригады — мок 2.2)
    implementation("com.google.zxing:core:3.5.3")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Location
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    // ExifInterface for image rotation handling
    implementation("androidx.exifinterface:exifinterface:1.3.6")

    // Media3 ExoPlayer for voice message playback
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // PDF Generation
    implementation("com.itextpdf:itext7-core:7.2.5")

    // CSV Export (легковесная альтернатива Excel)
    implementation("com.opencsv:opencsv:5.7.1")

    // FIX(2026-06-02) Report v2: настоящий .xlsx через FastExcel (~600KB, java.time-only,
    // работает на API 26+ с desugar). Замена самописного CSV. Apache POI отброшен — ~10МБ.
    implementation("org.dhatim:fastexcel:0.18.0")

    // Vico Charts for analytics
    implementation("com.patrykandpatrick.vico:compose-m3:2.0.0-alpha.19")

    // Glance (App Widgets with Compose)
    implementation("androidx.glance:glance-appwidget:1.0.0")
    implementation("androidx.glance:glance-material3:1.0.0")

    // Yandex AuthSDK
    implementation("com.yandex.android:authsdk:3.1.3")

    // Yandex MapKit (driver-map) — lite: карта + маркеры + полилинии (без платного routing).
    // Маршрут по дорогам строится дип-линком в консумер-приложение Яндекс.Карт (free).
    implementation("com.yandex.android:maps.mobile:4.33.1-lite")

    // Baseline Profile: бейкает baseline-профиль в APK и устанавливает
    // ART-профиль на первом запуске (cold-start ускорение, работает и для sideload-APK).
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    // FIX(2026-05-25): сгенерированный baseline-профиль из модуля :baselineprofile.
    // `./gradlew :app:generateBaselineProfile` (на устройстве/эмуляторе) запишет
    // профиль в app/src/release/generated/baselineProfiles/ → AGP забейкает его в release-APK.
    baselineProfile(project(":baselineprofile"))

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
