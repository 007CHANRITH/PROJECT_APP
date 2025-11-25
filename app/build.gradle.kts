plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.project_ez_talk"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.project_ez_talk"
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
}

dependencies {

    //noinspection UseTomlInstead
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    //noinspection GradleDependency
    implementation("androidx.appcompat:appcompat:1.6.1")
    //noinspection GradleDependency
    implementation("com.google.android.material:material:1.11.0")

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}