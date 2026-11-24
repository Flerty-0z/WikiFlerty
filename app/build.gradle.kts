plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val publicNumber = System.getenv("PUBLIC_VERSION")?.trim()?.toIntOrNull()

android {
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.maxHeapSize = "1536m"
            test.testLogging { events("passed", "failed", "standard_out") }
        }
    }
    namespace = "com.flerty.wikiflerty"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.flerty.wikiflerty"
        minSdk = 24
        targetSdk = 37
        versionCode = buildNumber
        versionName = "0.1.${publicNumber ?: buildNumber}"
        buildConfigField("String", "SYNC_HOST", "\"${(System.getenv("VDS_HOST") ?: "").trim()}\"")
    }

    val releaseKeystore = file("release.keystore")
    signingConfigs {
        create("app") {
            if (releaseKeystore.exists()) {
                storeFile = releaseKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "wikiflerty"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore.exists()) signingConfig = signingConfigs.getByName("app")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("studio") {
            initWith(getByName("release"))
            applicationIdSuffix = ".studio"
            versionNameSuffix = "-studio"
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes.add("META-INF/*")
        resources.excludes.add("org/bouncycastle/pqc/**")
        jniLibs.useLegacyPackaging = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = project.hasProperty("universal")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        freeCompilerArgs.addAll(
            "-opt-in=org.readium.r2.shared.ExperimentalReadiumApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")

    implementation(libs.androidx.profileinstaller)
    implementation(libs.readium.shared)
    implementation("com.github.junrar:junrar:7.5.5")
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    implementation(libs.readium.navigator.media.tts)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.media3.exoplayer)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.google.material)

    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.core)
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
