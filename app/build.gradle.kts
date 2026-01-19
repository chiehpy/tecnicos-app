plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.enofir.tecnicos_app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.enofir.tecnicos_app"

        // Android 5.1.1 = API 22
        minSdk = 22
        targetSdk = 34

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        multiDexEnabled = true

        // Si tenés .so (por vendor), mantenelo. Si no, podés borrar esto.
        ndk {
            abiFilters += listOf(
                "armeabi-v7a",
                "arm64-v8a"
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module"
            )
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // AndroidX / UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Multidex
    implementation("androidx.multidex:multidex:2.0.1")

    // Networking (API 22 compatible)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:3.12.13")
    implementation("com.squareup.okhttp3:logging-interceptor:3.12.13")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // --- ZXing (AGREGAR ESTO) ---
    implementation("com.journeyapps:zxing-android-embedded:3.6.0")
   // implementation("com.google.zxing:core:3.5.2")

    // Newland NSDK (si ya NO lo usás, podés borrarlo)
    implementation(files("libs/Newland-NSDK-2.17.0.aar"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
