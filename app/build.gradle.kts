plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "fr.polo.cockpit"
    compileSdk = 34

    defaultConfig {
        applicationId = "fr.polo.cockpit"
        minSdk = 26          // l'autoradio est en Android 12 (API 31)
        targetSdk = 34
        versionCode = 3
        versionName = "0.2.0"

        // L'AC8257 peut tourner en userspace 32 ou 64 bits : on embarque les deux.
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    // Clé de signature fixe (usage personnel) : permet d'installer les mises à jour
    // par-dessus l'ancienne version sans désinstaller.
    signingConfigs {
        create("polo") {
            storeFile = rootProject.file("keystore/polo-cockpit.jks")
            storePassword = "polocockpit"
            keyAlias = "polo"
            keyPassword = "polocockpit"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("polo")
        }
        debug {
            signingConfig = signingConfigs.getByName("polo")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true; buildConfig = true }

    // Les modèles .glb ne doivent pas être compressés dans l'APK
    androidResources { noCompress += listOf("glb", "filamat") }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Moteur 3D léger de Google (Apache 2.0) : rendu PBR + chargement glTF/GLB
    implementation("com.google.android.filament:filament-android:1.51.0")
    implementation("com.google.android.filament:gltfio-android:1.51.0")
}
