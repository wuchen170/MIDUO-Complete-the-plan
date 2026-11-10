plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aritxonly.myhypermodifier"
    compileSdk = 37

    defaultConfig {
        // 与旧模块（dev.hg.glass / com.aritxonly.softglassnav）都不同，可并存安装。
        applicationId = "duo18.plan"
        minSdk = 33
        targetSdk = 35
        versionCode = 28
        versionName = "1.1.5"

        ndk {
            // The module only hooks ARM64 hosts; shipping x86/x86_64/armeabi-v7a copies of the
            // Compose native libraries tripled the lib payload for no benefit.
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // The module ships no user-facing assets of its own: everything on screen is drawn by
            // Compose. Trimming the library resource tables removes Miuix/Material string and
            // dimens tables that no code path reaches (ARSC alone was ~480 KB).
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 模块本身没有需要保护的自有资源，但 FOLD 侧带的桌面载荷要靠固定签名覆盖安装。
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Only arm64 is relevant for the supported hosts; the other three ABI copies of
    // androidx.graphics.path are dead weight.
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    sourceSets.getByName("main").kotlin.srcDir(rootProject.file("third_party/miuix-glass/src/commonMain/kotlin"))

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3:1.5.0-alpha22")
    implementation("com.materialkolor:material-kolor:4.1.1")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4-rc01")
    implementation("io.github.kyant0:shapes:1.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
