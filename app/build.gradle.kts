plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.marketia.jupiter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.marketia.jupiter"
        minSdk = 29
        targetSdk = 35
        versionCode = 17
        versionName = "1.7.0"
    }

    signingConfigs {
        create("release") {
            val localStore = file("C:\\Users\\robik\\market-ia\\apk\\keys\\jupiter-release.jks")
            val ciStore    = rootProject.file("jupiter-release.jks")
            storeFile      = if (localStore.exists()) localStore else ciStore
            storePassword  = System.getenv("KEY_STORE_PASSWORD") ?: "jupiter2026!"
            keyAlias       = System.getenv("KEY_ALIAS")          ?: "jupiter"
            keyPassword    = System.getenv("KEY_PASSWORD")       ?: "jupiter2026!"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
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
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // DataStore + OkHttp
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)

    // WorkManager + Hilt integration
    implementation(libs.workmanager.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    debugImplementation(libs.androidx.ui.tooling)
}
