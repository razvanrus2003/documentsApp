plugins {
    alias(libs.plugins.android.application)
}

configurations.all {
    resolutionStrategy {
        dependencySubstitution {
            substitute(module("org.bouncycastle:bcprov-jdk15to18")).using(module("org.bouncycastle:bcprov-jdk18on:${libs.versions.bouncycastle.get()}"))
            substitute(module("org.bouncycastle:bcpkix-jdk15to18")).using(module("org.bouncycastle:bcpkix-jdk18on:${libs.versions.bouncycastle.get()}"))
            substitute(module("org.bouncycastle:bcutil-jdk15to18")).using(module("org.bouncycastle:bcutil-jdk18on:${libs.versions.bouncycastle.get()}"))
        }

        force(libs.bouncycastle.prov)
        force(libs.bouncycastle.pkix)
        force(libs.bouncycastle.util)

        eachDependency {
            if (requested.group == "org.bouncycastle") {
                useVersion(libs.versions.bouncycastle.get())
            }
        }
    }
}

android {
    namespace = "com.example.documentsapp"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.documentsapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/kotlin-stdlib.kotlin_module"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.material)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)

    // Detection Engines
    implementation(libs.opencv)
    implementation(libs.pdfbox.android)
    implementation(libs.bouncycastle.util)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
