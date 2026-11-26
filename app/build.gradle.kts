import java.util.jar.JarFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ahmed.zaghlolplayer.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ahmed.zaghlolplayer.tv"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // Jetpack Compose for TV (focus handling, TV lazy layouts)
    implementation("androidx.tv:tv-foundation:1.0.0")
    implementation("androidx.tv:tv-material:1.0.0")

    // ExoPlayer (Media3) — HLS playback
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // Image loading (channel logos, movie/series covers)
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}

// Diagnostic: list what the tv-foundation artifact on the compile classpath
// actually contains (used to debug "Unresolved reference 'lazy'").
tasks.register("diagnoseTv") {
    doLast {
        val cp = configurations.getByName("debugCompileClasspath")
        val tvJars = cp.files.filter { it.name.contains("tv-foundation") }
        println("DIAG tv-foundation jars: $tvJars")
        if (tvJars.isEmpty()) {
            println("DIAG: NO tv-foundation jar on debugCompileClasspath!")
        }
        for (f in tvJars) {
            println("DIAG jar: $f (${f.length()} bytes)")
            JarFile(f).use { jar ->
                val lazyEntries = jar.entries().asSequence().map { it.name }
                    .filter { it.startsWith("androidx/tv/foundation/lazy") }.toList()
                println("DIAG lazy entries: ${lazyEntries.size}")
                lazyEntries.take(15).forEach { println("DIAG   $it") }
                println("DIAG total entries: ${jar.entries().asSequence().count()}")
            }
        }
    }
}
