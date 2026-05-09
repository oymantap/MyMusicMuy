plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.mymusic.muy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mymusic.muy"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Konfigurasi tanda tangan APK (Keystore)
    signingConfigs {
        create("release") {
            // File release.jks akan dibuat otomatis oleh GitHub Actions dari Secret
            storeFile = file("release.jks") 
            storePassword = System.getenv("RELEASE_STORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            // Pakai konfigurasi signing "release" yang dibuat di atas
            signingConfig = signingConfigs.getByName("release")
            
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

    // Tetap pakai jvmTarget = "11" agar kompatibel dengan plugin lu sekarang
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Library standar dari Version Catalog
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.material)
    
    // Fitur Baru FSP (WAJIB PAKAI TANDA KURUNG)
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    
    // Media & Glide
    implementation("androidx.media:media:1.7.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}