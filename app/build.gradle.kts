plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ciVersionCode = providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull() ?: 1
val ciVersionName = providers.environmentVariable("VERSION_NAME").orNull ?: "${providers.gradleProperty("calm.versionNameBase").get()}.0"
fun signingValue(propertyName: String, environmentName: String): String? {
    return providers.gradleProperty(propertyName).orNull ?: providers.environmentVariable(environmentName).orNull
}

val signingKeystoreFile = signingValue("calmSigningKeystoreFile", "CALM_SIGNING_KEYSTORE_FILE")?.let(rootProject::file)
val signingStorePassword = signingValue("calmSigningStorePassword", "CALM_SIGNING_STORE_PASSWORD")
val signingKeyAlias = signingValue("calmSigningKeyAlias", "CALM_SIGNING_KEY_ALIAS")
val signingKeyPassword = signingValue("calmSigningKeyPassword", "CALM_SIGNING_KEY_PASSWORD")
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
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
}
