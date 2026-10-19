val drnasKeyPath = System.getenv("DRNAS_SIGNING_KEYSTORE_FILE")
val drnasStorePassword = System.getenv("DRNAS_SIGNING_STORE_PASSWORD")
val drnasKeyAlias = System.getenv("DRNAS_SIGNING_KEY_ALIAS")
val drnasKeyPassword = System.getenv("DRNAS_SIGNING_KEY_PASSWORD")
val drnasStableSigning = listOf(
    drnasKeyPath,drnasStorePassword,drnasKeyAlias,drnasKeyPassword
).all { !it.isNullOrBlank() }

// Build jobs that distribute APKs must never silently fall back to a one-off
// Android debug certificate. Local debug/test builds remain possible.
if (System.getenv("DRNAS_REQUIRE_STABLE_SIGNING") == "1" && !drnasStableSigning) {
    error("DRNAS permanent APK signing is mandatory; set the keystore credentials before assembling an installable APK.")
}

plugins {
    id("com.android.application")
}
android {
    namespace = "com.drnas.console"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.drnas.console"
        minSdk = 26
        targetSdk = 35
        ndk { abiFilters += listOf("arm64-v8a") }
        manifestPlaceholders["appLabel"] = "Blender"
        versionCode = 122
        versionName = "1.1-r54-input-focus-diagnostics"
    }
    packaging {
        jniLibs.useLegacyPackaging = true
    }
    ndkVersion = "27.2.12479018"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    signingConfigs {
        if (drnasStableSigning) {
            create("drnasStable") {
                storeFile = file(requireNotNull(drnasKeyPath))
                storePassword = requireNotNull(drnasStorePassword)
                keyAlias = requireNotNull(drnasKeyAlias)
                keyPassword = requireNotNull(drnasKeyPassword)
            }
        }
    }
    buildTypes {
        getByName("debug") {
            // Default Android debug signing is unique to each ephemeral CI runner.
            // Use the SAME private secret-backed key on EVERY downloadable build.
            if (drnasStableSigning) {
                signingConfig = signingConfigs.getByName("drnasStable")
            } else {
                // Missing project signing secrets: install experimental CI APK
                // alongside existing DRNAS and earlier LAB builds without uninstalling data.
                applicationIdSuffix = ".prealpha01r4lab"
                manifestPlaceholders["appLabel"] = "Blender DRNAS LAB"
            }
        }
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Offline inspection of untrusted .wcp TAR+XZ content, never execution.
    implementation("org.tukaani:xz:1.10")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("com.github.luben:zstd-jni:1.5.6-9@aar")
    testImplementation("junit:junit:4.13.2")
    // Real org.json on the JVM for FEXCore WCP manifest validation regression tests.
    testImplementation("org.json:json:20240303")
}









