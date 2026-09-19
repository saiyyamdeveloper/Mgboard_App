import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Klipy API key kahin se bhi aa sakti hai (priority order):
 *   1. `local.properties`  → KLIPY_APP_KEY=...   (sabse aasaan; repo mein commit NAHI hoti)
 *   2. gradle property      → gradle.properties ya -PKLIPY_APP_KEY=...
 *   3. environment variable → KLIPY_APP_KEY=...
 *   4. runtime              → app ke andar 🔑 "Klipy API key" field (prefs mein save)
 */
fun klipyKeyFromLocalProperties(): String {
    val props = Properties()
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
    return props.getProperty("KLIPY_APP_KEY")
        ?: project.findProperty("KLIPY_APP_KEY")?.toString()
        ?: System.getenv("KLIPY_APP_KEY")
        ?: ""
}


android {
    namespace = "com.mgboard.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mgboard.keyboard"
        minSdk = 24

        // Klipy GIF API key — repo mein commit nahi hoti.
        // local.properties ya environment se: KLIPY_APP_KEY=...
        // (khali chhodo to GIF tab par setup hint dikhta hai; bundled stickers chalte rehte hain)
        buildConfigField("String", "KLIPY_APP_KEY",
            "\"" + klipyKeyFromLocalProperties() + "\"")
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        buildConfig = true compose = true }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            kotlin.srcDirs("src/test/kotlin")
        }
    }

    testOptions {
        unitTests.all {
            it.systemProperty("file.encoding", "UTF-8")
        }
    }

    packaging {
        resources {
            // font license ko APK mein rakhein (OFL requirement)
            excludes += setOf("META-INF/*.kotlin_module")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core:1.13.1")   // ViewCompat/WindowCompat insets (padding-project)

    // ── translate-project: ML Kit on-device translation (hi ↔ en) ────────────
    // Research: docs/research/toolbar-project/TRANSLATE-GIF-FEASIBILITY.md §1.1
    //  - 59 languages, `hi` + `en` officially supported; ~30 MB model per language
    //  - poora on-device → koi permission nahi, koi API key nahi, text cloud par nahi jaata
    //  - standalone SDK: Firebase/google-services.json ki zaroorat nahi
    implementation("com.google.mlkit:translate:17.0.3")

    // ── GIF/Stickers tabs: Compose mein animated GIF render karne ke liye ──────
    // (coil-gif = MovieImageDecoder; WebP/PNG bhi inbuilt hain)
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    // Compose IME (InputMethodService = Service) ke liye SavedStateRegistryOwner
    // — bina iske ComposeView attach hote hi crash karta hai.
    implementation("androidx.savedstate:savedstate:1.2.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // Pure-logic unit tests (engine/converter/layout/grid) — no Android runtime needed
    testImplementation("junit:junit:4.13.2")
}
