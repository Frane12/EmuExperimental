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
        versionCode = 8
        versionName = "0.4.2-gamenative-loader"
    }
    ndkVersion = "27.2.12479018"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildTypes {
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
}
