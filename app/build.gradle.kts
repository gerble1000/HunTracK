import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "uk.hunttrack"
    compileSdk = 36

    defaultConfig {
        applicationId = "uk.hunttrack.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 28
        versionName = "1.1.27"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(checkNotNull(keystoreProperties.getProperty("storeFile")))
                storePassword = checkNotNull(keystoreProperties.getProperty("storePassword"))
                keyAlias = checkNotNull(keystoreProperties.getProperty("keyAlias"))
                keyPassword = checkNotNull(keystoreProperties.getProperty("keyPassword"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:location"))
    implementation(project(":feature:home"))
    implementation(project(":feature:map"))
    implementation(project(":feature:weather"))
    implementation(project(":feature:compass"))
    implementation(project(":feature:wind"))
    implementation(project(":feature:markers"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:species"))
    implementation(project(":feature:calls"))
    implementation(project(":feature:permissions"))
    implementation(project(":feature:emergency"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:tides"))
    implementation(project(":feature:culls"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.maplibre.sdk)
    implementation(libs.androidx.biometric)
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    debugImplementation(libs.androidx.compose.ui.tooling)
}
