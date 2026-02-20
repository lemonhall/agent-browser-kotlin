plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

import java.io.File

fun readDotEnv(file: File): Map<String, String> {
    if (!file.exists()) return emptyMap()
    val out = linkedMapOf<String, String>()
    file.readLines(Charsets.UTF_8).forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEach
        val idx = line.indexOf('=')
        if (idx <= 0) return@forEach
        val key = line.substring(0, idx).trim()
        var value = line.substring(idx + 1).trim()
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length - 1)
        }
        if (key.isNotEmpty()) out[key] = value
    }
    return out
}

val dotEnv = readDotEnv(rootProject.file(".env"))
val openAiApiKey = (dotEnv["OPENAI_API_KEY"] ?: System.getenv("OPENAI_API_KEY")).orEmpty()
val openAiBaseUrl = (dotEnv["OPENAI_BASE_URL"] ?: System.getenv("OPENAI_BASE_URL")).orEmpty()
val openAiModel = (dotEnv["MODEL"] ?: System.getenv("MODEL")).orEmpty()

fun isLikelyRealOpenAiKey(value: String): Boolean {
    val v = value.trim()
    if (v.isBlank()) return false
    // Avoid accidentally enabling network tests with redacted placeholders like "sk-xxxxxx".
    if (v.contains("xxxx", ignoreCase = true)) return false
    return v.length >= 20
}

android {
    namespace = "com.lsl.agent_browser_kotlin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lsl.agent_browser_kotlin"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Pass OpenAI config to instrumentation tests without committing secrets.
        // NOTE: do not print these values in logs.
        if (isLikelyRealOpenAiKey(openAiApiKey)) {
            testInstrumentationRunnerArguments["OPENAI_API_KEY"] = openAiApiKey
        }
        if (openAiBaseUrl.isNotBlank()) {
            testInstrumentationRunnerArguments["OPENAI_BASE_URL"] = openAiBaseUrl
        }
        if (openAiModel.isNotBlank()) {
            testInstrumentationRunnerArguments["MODEL"] = openAiModel
        }
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(project(":agent-browser-kotlin"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.kotlinx.serialization.json)
    androidTestImplementation("com.squareup.okio:okio:3.8.0")
    androidTestImplementation("me.lemonhall.openagentic:openagentic-sdk-kotlin:0.1.0-SNAPSHOT")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
