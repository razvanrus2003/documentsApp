import org.gradle.api.tasks.testing.logging.TestExceptionFormat

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
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.mockito:mockito-core:5.3.1")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test:runner:1.5.2")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("androidx.fragment:fragment-testing:1.6.2")
    testImplementation("androidx.navigation:navigation-testing:2.6.0")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

tasks.withType<Test> {
    jvmArgs("-XX:+EnableDynamicAgentLoading")

    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
    }

    addTestListener(object : TestListener {
        val RESET = "\u001B[0m"
        val GREEN = "\u001B[32m"
        val RED = "\u001B[31m"
        val YELLOW = "\u001B[33m"
        val CYAN = "\u001B[36m"
        val BOLD = "\u001B[1m"

        override fun beforeSuite(suite: TestDescriptor) {}
        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent == null) {
                val statusColor = if (result.resultType == TestResult.ResultType.SUCCESS) GREEN else RED
                println("\n$CYAN$BOLD========================================")
                println("          TEST EXECUTION SUMMARY        ")
                println("========================================$RESET")
                println(" Status  : $statusColor${result.resultType}$RESET")
                println(" Passed  : $GREEN${result.successfulTestCount}$RESET")
                println(" Failed  : $RED${result.failedTestCount}$RESET")
                println(" Skipped : $YELLOW${result.skippedTestCount}$RESET")
                println(" Total   : ${result.testCount}")
                println("$CYAN$BOLD========================================$RESET\n")
            }
        }
        override fun beforeTest(test: TestDescriptor) {}
        override fun afterTest(test: TestDescriptor, result: TestResult) {
            val status = when (result.resultType) {
                TestResult.ResultType.SUCCESS -> "$GREEN✅ PASSED$RESET"
                TestResult.ResultType.FAILURE -> "$RED❌ FAILED$RESET"
                else -> "$YELLOW⚠️ SKIPPED$RESET"
            }
            println("$status: ${test.className} > ${test.name}")
        }
    })
}

tasks.register("runAllTests") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "Runs all unit and Robolectric tests for the app."
}


