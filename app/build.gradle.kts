plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.midigameengine"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.midigameengine"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            val storeFilePath = providers.gradleProperty("RELEASE_STORE_FILE")
                .orElse(providers.environmentVariable("RELEASE_STORE_FILE"))
                .orNull
            val storePasswordValue = providers.gradleProperty("RELEASE_STORE_PASSWORD")
                .orElse(providers.environmentVariable("RELEASE_STORE_PASSWORD"))
                .orNull
            val keyAliasValue = providers.gradleProperty("RELEASE_KEY_ALIAS")
                .orElse(providers.environmentVariable("RELEASE_KEY_ALIAS"))
                .orNull
            val keyPasswordValue = providers.gradleProperty("RELEASE_KEY_PASSWORD")
                .orElse(providers.environmentVariable("RELEASE_KEY_PASSWORD"))
                .orNull

            if (storeFilePath != null && storePasswordValue != null && keyAliasValue != null && keyPasswordValue != null) {
                storeFile = file(storeFilePath)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
}

kotlin {
    jvmToolchain(17)
}
