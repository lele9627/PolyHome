plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val defaultApiBaseUrl = "https://polyhome.lesmoulinsdudev.com"
val configuredApiBaseUrl = providers.environmentVariable("POLYHOME_API_BASE_URL")
    .orElse(providers.gradleProperty("POLYHOME_API_BASE_URL"))
    .getOrElse(defaultApiBaseUrl)
    .trimEnd('/')
val escapedApiBaseUrl = configuredApiBaseUrl
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.example.projet_androide"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.projet_androide"
        minSdk = 25
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "POLYHOME_API_BASE_URL", "\"$escapedApiBaseUrl\"")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
    }
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
