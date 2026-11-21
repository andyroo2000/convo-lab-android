plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.cdcg.convolab"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "com.cdcg.convolab"
        minSdk = 28
        targetSdk = 37
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "API_BASE_URL", "\"https://convo-lab.com\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        warningsAsErrors = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    constraints {
        implementation("androidx.lifecycle:lifecycle-runtime:2.11.1") {
            because("Earlier transitive lifecycle versions contain lint checks incompatible with AGP 9.5")
        }
    }
    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.11.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Fail before packaging if the reproducible native preparation step was missed.
val verifyTypingAssets by tasks.registering {
    doLast {
        for (abi in listOf("arm64-v8a", "x86_64")) {
            val libraries = listOf("libtyping_jni.so", "libggml-cpu.so") +
                if (abi == "arm64-v8a") listOf("libggml-cpu-dotprod.so") else emptyList()
            for (library in libraries) {
                check(file("src/main/jniLibs/$abi/$library").isFile) {
                    "Missing $abi/$library. Prepare typing first: see docs/typing-playground.md"
                }
            }
        }
        check(file("src/main/assets/typing/model.gguf").length() == 72298816L)
        check(file("src/main/assets/typing/Dictionary/louds/charID.chid").isFile)
    }
}
tasks.matching { it.name in listOf("mergeDebugAssets", "mergeDebugNativeLibs", "mergeReleaseAssets", "mergeReleaseNativeLibs") }.configureEach {
    dependsOn(verifyTypingAssets)
}
