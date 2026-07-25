import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // Applied only when google-services.json is present (see bottom of this file).
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.yishenghuang.heartext"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.yishenghuang.heartext"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        val apiBase = localProperties.getProperty(
            "HEARTEXT_API_BASE_URL",
            "https://heartext.677000.xyz"
        )
        val clerkKey = localProperties.getProperty("CLERK_PUBLISHABLE_KEY", "")

        buildConfigField("String", "API_BASE_URL", "\"$apiBase\"")
        buildConfigField("String", "CLERK_PUBLISHABLE_KEY", "\"$clerkKey\"")
        // Legacy aliases used by older TTS code paths
        buildConfigField("String", "TTS_API_BASE_URL", "\"$apiBase\"")
        buildConfigField("String", "TTS_API_KEY", "\"\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// Download sherpa-onnx AAR once into app/libs if missing (offline neural TTS).
// Uses task inputs/outputs + java.net so it is configuration-cache compatible.
val sherpaAarVersion = "1.13.4"
tasks.register("downloadSherpaAar") {
    val version = sherpaAarVersion
    val output = layout.projectDirectory.file("libs/sherpa-onnx-$version.aar")
    inputs.property("sherpaAarVersion", version)
    outputs.file(output)
    outputs.upToDateWhen {
        val file = output.asFile
        file.exists() && file.length() > 1_000_000L
    }
    doLast {
        val out = output.asFile
        if (out.exists() && out.length() > 1_000_000L) return@doLast
        out.parentFile.mkdirs()
        val url = URI(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$version/sherpa-onnx-$version.aar"
        ).toURL()
        url.openStream().use { input ->
            out.outputStream().use { dest -> input.copyTo(dest) }
        }
        require(out.exists() && out.length() > 1_000_000L) {
            "Failed to download sherpa-onnx AAR from $url"
        }
    }
}
tasks.named("preBuild").configure { dependsOn("downloadSherpaAar") }

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.clerk.android.api)
    implementation(libs.haze)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.fragment.compose)
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    implementation(libs.readium.adapter.pdfium)
    implementation(libs.media3.common)
    implementation(libs.media3.session)
    implementation(libs.androidx.appcompat)
    implementation(libs.opencc4j)
    implementation("org.apache.commons:commons-compress:1.26.2")

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    // Native crashes from sherpa-onnx / Readium PDF JNI
    implementation(libs.firebase.crashlytics.ndk)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Crashlytics needs google-services.json from the Firebase Console.
// Without it, keep the project buildable and skip the plugins.
val googleServicesFile = file("google-services.json")
if (googleServicesFile.exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
} else {
    logger.warn(
        "app/google-services.json missing — Firebase Crashlytics plugins not applied. " +
            "Download it from Firebase Console (Android app: com.yishenghuang.heartext)."
    )
}
