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
        versionCode = 3
        versionName = "0.2.2-wcp-fix"
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
    testImplementation("junit:junit:4.13.2")
}
