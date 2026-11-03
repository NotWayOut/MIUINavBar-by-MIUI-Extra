import java.util.Properties

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.compose.compiler)
}

// 签名配置读取 local.properties（STORE_FILE / STORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD），
// 缺失时 release 退化为 debug 签名
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val storeFilePath: String? = localProperties.getProperty("STORE_FILE") ?: System.getenv("STORE_FILE")
val storePasswordValue: String? =
    localProperties.getProperty("STORE_PASSWORD") ?: System.getenv("STORE_PASSWORD")
val keyAliasValue: String? = localProperties.getProperty("KEY_ALIAS") ?: System.getenv("KEY_ALIAS")
val keyPasswordValue: String? =
    localProperties.getProperty("KEY_PASSWORD") ?: System.getenv("KEY_PWD")

android {
    namespace = "io.github.NotWayOut.MIUINavBar"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "io.github.NotWayOut.MIUINavBar"
        minSdk = 31
        targetSdk = 35
        // 版本号默认值，可通过 -PversionCode=xxx / -PversionName=xxx 或同名环境变量覆盖
        // versionCode 为内部版本号（整数），versionName 为外部展示版本号（字符串）
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull()
            ?: System.getenv("VERSION_CODE")?.toIntOrNull() ?: 20
        versionName = (project.findProperty("versionName") as String?)
            ?: System.getenv("VERSION_NAME") ?: "2.7.1-V-HyperOS1"
        val buildTime = System.currentTimeMillis()
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
    }

    signingConfigs {
        if (storeFilePath != null && storePasswordValue != null &&
            keyAliasValue != null && keyPasswordValue != null
        ) {
            register("release") {
                storeFile = file(storeFilePath)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
                storeType = "pkcs12"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
            signingConfig = if (storeFilePath != null) {
                signingConfigs["release"]
            } else {
                signingConfigs["debug"]
            }
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += setOf(
                "META-INF/*.kotlin_module",
                "META-INF/*.version",
                "META-INF/*.proto",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.compose)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.navigationevent.compose)
}
