import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Cargar propiedades del keystore
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

// Función para obtener versión desde git tags + commits
fun getVersionFromGit(): String {
    return try {
        val process = ProcessBuilder("git", "describe", "--tags", "--always")
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()

        // Formato esperado: v1.3.0-5-g1234abc (tag-commits-hash)
        // Si no hay commits después del tag: v1.3.0
        val regex = Regex("""v?(\d+)\.(\d+)\.(\d+)(?:-(\d+)-g[a-f0-9]+)?""")
        val match = regex.find(output)

        if (match != null) {
            val major = match.groupValues[1]
            val minor = match.groupValues[2]
            val patch = match.groupValues[3]
            val commits = match.groupValues.getOrNull(4)?.takeIf { it.isNotEmpty() } ?: "0"

            if (commits == "0") {
                "$major.$minor.$patch"
            } else {
                "$major.$minor.$commits"
            }
        } else {
            "1.3.0"
        }
    } catch (e: Exception) {
        "1.3.0"
    }
}

fun getVersionCodeFromGit(): Int {
    return try {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        output.toIntOrNull() ?: 1
    } catch (e: Exception) {
        1
    }
}

android {
    namespace = "com.enofir.tecnicos_app"
    compileSdk = 34

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "com.enofir.tecnicos_app"

        // Android 5.1.1 = API 22
        minSdk = 22
        targetSdk = 34

        versionCode = getVersionCodeFromGit()
        versionName = getVersionFromGit()

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
            signingConfig = signingConfigs.getByName("release")
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

    buildFeatures {
        buildConfig = true
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
