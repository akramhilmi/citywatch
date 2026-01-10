plugins {
    alias(libs.plugins.android.application)

    // Google services
    id("com.google.gms.google-services")
}

android {
    namespace = "com.gitgud.citywatch"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.gitgud.citywatch"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // firebase
    implementation(platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore")
    implementation(libs.firebase.auth)
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-storage")

    // image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation(libs.cardview)
    implementation(libs.recyclerview)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
