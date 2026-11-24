plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mohit.rehabit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mohit.rehabit"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.9"
    }

    // Permanent signing key so every build can be installed over the previous one (keeping data).
    // CI provides it via secrets; locally, set these env vars to use it (otherwise the default debug key is used).
    val keystorePath: String? = System.getenv("REHABIT_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null) {
            create("rehabit") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("REHABIT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("REHABIT_KEY_ALIAS")
                keyPassword = System.getenv("REHABIT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("rehabit")
        }
        release {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("rehabit")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}

// Print the full assertion message (e.g. the list of content problems) in the CI log.
tasks.withType<Test> {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
