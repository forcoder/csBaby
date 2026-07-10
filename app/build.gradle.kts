plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.csbaby.kefu"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.csbaby.kefu"
        minSdk = 26
        targetSdk = 34
        versionCode = project.property("APP_VERSION_CODE").toString().toInt()
        versionName = project.property("APP_VERSION_NAME").toString()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // 主 API 服务配置（api.agentai0.com - HTTP）
        // 统一使用主 API 的 /api/auth/user/login（支持 phone 或 email 双字段）
        buildConfigField("String", "API_BASE_URL", "\"http://api.agentai0.com/\"")
        // 同步服务器配置（自建部署：sync.agentai0.com - HTTP）
        // 仅用于数据同步 (sync/all, sync/changes, sync/push, backup)
        // 认证已在主 API 完成
        buildConfigField("String", "SYNC_BASE_URL", "\"http://sync.agentai0.com/\"")
    }

    // Release 签名 - OTA 分发必须用统一签名,避免不同构建机器 debug.keystore 不一致导致
    // "解析包出现问题"。本地开发/CI 通过环境变量注入 keystore 路径和密码。
    signingConfigs {
        create("release") {
            // 环境变量来自 GitHub Actions secrets(KS_FILE / KS_STORE_PASS / KS_KEY_ALIAS / KS_KEY_PASS)
            // 本地开发可在 ~/.gradle/gradle.properties 或环境变量设置
            val ksFile: String? = System.getenv("KS_FILE")
            if (ksFile != null && ksFile.isNotBlank()) {
                storeFile = file(ksFile)
                storePassword = System.getenv("KS_STORE_PASS")
                keyAlias = System.getenv("KS_KEY_ALIAS")
                keyPassword = System.getenv("KS_KEY_PASS")
            }
        }
    }

    buildTypes {
        debug {
            // 调试签名，允许安装到设备
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            // 仅当 signingConfigs.release 已配置 storeFile 时才使用 release 签名,
            // 否则降级为 debug 签名以便本地直接构建( CI 中必须配置 KS_FILE 等环境变量 )
            signingConfig = if (signingConfigs.findByName("release")?.storeFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Lint 检查不中止构建（CI 中先修复 lint 错误再开启）
    lint {
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.6"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")

    // Hilt: kapt handles annotation processing
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    kapt("androidx.hilt:hilt-compiler:1.1.0")

    // Room: KSP
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // WorkManager for background updates
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Timber for logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
