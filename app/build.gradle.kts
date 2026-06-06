plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ciVersionCode = providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull() ?: 1
val ciVersionName = providers.environmentVariable("VERSION_NAME").orNull ?: "${providers.gradleProperty("calm.versionNameBase").get()}.0"
val signingKeystoreFile = providers.environmentVariable("CALM_SIGNING_KEYSTORE_FILE").orNull?.let(::file)
val signingStorePassword = providers.environmentVariable("CALM_SIGNING_STORE_PASSWORD").orNull
val signingKeyAlias = providers.environmentVariable("CALM_SIGNING_KEY_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("CALM_SIGNING_KEY_PASSWORD").orNull
val hasReleaseSigning = signingKeystoreFile?.exists() == true &&
    !signingStorePassword.isNullOrBlank() &&
    !signingKeyAlias.isNullOrBlank() &&
    !signingKeyPassword.isNullOrBlank()

android {
    namespace = "dev.barna.calm"
    compileSdk = 36

    signingConfigs {
        if (hasReleaseSigning) {
            create("releaseSigning") {
                storeFile = signingKeystoreFile
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "dev.barna.calm"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = ciVersionName
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("releaseSigning")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    testImplementation("junit:junit:4.13.2")
}
