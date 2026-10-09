import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase's Android config is public app metadata, not a server credential.
// Read it without copying it into the repository or requiring it for polling builds.
val firebaseConfigPath = System.getenv("BARK_FIREBASE_CONFIG")
val firebaseConfigFile = firebaseConfigPath?.let(::file) ?: file("google-services.json")
require(firebaseConfigPath.isNullOrBlank() || firebaseConfigFile.isFile) {
    "BARK_FIREBASE_CONFIG must point to an existing google-services.json"
}
@Suppress("UNCHECKED_CAST")
val firebaseConfig = if (firebaseConfigFile.isFile) JsonSlurper().parse(firebaseConfigFile) as Map<String, Any> else null

android {
    namespace = "day.bark.android"
    compileSdk = 36

    val releaseKeystorePath = System.getenv("BARK_ANDROID_KEYSTORE_PATH")
    val releaseKeystorePassword = System.getenv("BARK_ANDROID_KEYSTORE_PASSWORD")
    val releaseKeyAlias = System.getenv("BARK_ANDROID_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("BARK_ANDROID_KEY_PASSWORD")
    val hasReleaseSigning = listOf(
        releaseKeystorePath,
        releaseKeystorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).all { !it.isNullOrBlank() }

    defaultConfig {
        applicationId = "day.bark.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.4.2"
        buildConfigField("boolean", "FIREBASE_CONFIGURED", (firebaseConfig != null).toString())
        firebaseConfig?.let { config ->
            @Suppress("UNCHECKED_CAST")
            val project = config["project_info"] as Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val clients = config["client"] as List<Map<String, Any>>
            val client = clients.firstOrNull {
                val info = it["client_info"] as Map<*, *>
                (info["android_client_info"] as Map<*, *>)["package_name"] == "day.bark.android"
            } ?: error("Firebase config must contain package day.bark.android")
            val info = client["client_info"] as Map<*, *>
            val apiKey = (client["api_key"] as List<*>).first() as Map<*, *>
            mapOf(
                "google_app_id" to info["mobilesdk_app_id"],
                "google_api_key" to apiKey["current_key"],
                "gcm_defaultSenderId" to project["project_number"],
                "project_id" to project["project_id"],
            ).forEach { (name, value) ->
                require(!value?.toString().isNullOrBlank()) { "Missing Firebase config field: $name" }
                resValue("string", name, value.toString())
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation("com.google.firebase:firebase-messaging:25.1.3")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.concurrent:concurrent-futures:1.3.0")
    val composeBom = platform("androidx.compose:compose-bom:2026.05.01")
    implementation(composeBom)
    testImplementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core:1.13.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    testImplementation("junit:junit:4.13.2")
}
