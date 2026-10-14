plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}

android {
    namespace = "com.roshid.sara01"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.roshid.sara01"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
}
