plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val releaseSigning = listOf("GRAFIT_KEYSTORE_PATH", "GRAFIT_STORE_PASSWORD", "GRAFIT_KEY_ALIAS", "GRAFIT_KEY_PASSWORD")
    .associateWith { providers.environmentVariable(it).orNull }
require(releaseSigning.values.all { it == null } || releaseSigning.values.all { !it.isNullOrBlank() }) {
    "Release signing requires all four GRAFIT signing environment variables."
}
// A one-time, V3-signed key migration requires Android's signing-history support.
val signingMigration = providers.gradleProperty("grafitSigningMigration").orNull == "true"
android {
    namespace = "hu.grafit"
    compileSdk = 35
    defaultConfig {
        applicationId = "hu.grafit"
        minSdk = if (signingMigration) 28 else 26
        targetSdk = 35
        versionCode = 23
        versionName = "1.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (releaseSigning.values.all { !it.isNullOrBlank() }) create("release") {
            storeFile = file(releaseSigning.getValue("GRAFIT_KEYSTORE_PATH")!!)
            storePassword = releaseSigning.getValue("GRAFIT_STORE_PASSWORD")
            keyAlias = releaseSigning.getValue("GRAFIT_KEY_ALIAS")
            keyPassword = releaseSigning.getValue("GRAFIT_KEY_PASSWORD")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    buildTypes {
        getByName("release") {
            // The install failure was a signing-key conflict; restore release optimisation.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    packaging {
        // Extract native libraries through the traditional installer path.
        jniLibs.useLegacyPackaging = true
        // Compress the optimised DEX payload to keep the distributed APK small.
        dex.useLegacyPackaging = true
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
