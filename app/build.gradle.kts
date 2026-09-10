plugins {
    id("com.android.application")
}

android {
    namespace = "com.neyzo.yamp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.neyzo.yamp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-apk1"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.webkit:webkit:1.14.0")
}
