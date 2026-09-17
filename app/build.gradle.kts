import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/**
 * 正式版签名：从 gradle 属性或环境变量读取；没配置时 release 不签名（不影响日常构建）。
 *
 *   ./gradlew :app:assembleRelease -Pccsut.releaseStoreFile=... -Pccsut.releaseStorePassword=...
 *        -Pccsut.releaseKeyAlias=... -Pccsut.releaseKeyPassword=...
 * 也可以用环境变量 CCSUT_RELEASE_STORE_FILE / _STORE_PASSWORD / _KEY_ALIAS / _KEY_PASSWORD。
 */
val releaseStoreFile = providers.gradleProperty("ccsut.releaseStoreFile")
    .orElse(providers.environmentVariable("CCSUT_RELEASE_STORE_FILE"))
    .orNull
val releaseStorePassword = providers.gradleProperty("ccsut.releaseStorePassword")
    .orElse(providers.environmentVariable("CCSUT_RELEASE_STORE_PASSWORD"))
    .orNull
val releaseKeyAlias = providers.gradleProperty("ccsut.releaseKeyAlias")
    .orElse(providers.environmentVariable("CCSUT_RELEASE_KEY_ALIAS"))
    .orNull
val releaseKeyPassword = providers.gradleProperty("ccsut.releaseKeyPassword")
    .orElse(providers.environmentVariable("CCSUT_RELEASE_KEY_PASSWORD"))
    .orNull
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "com.jingoujiao.ccsutschedule"

    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.jingoujiao.ccsutschedule"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.3.0"
    }

    /**
     * 见文件顶部：只有四项都配置了才创建 release 签名配置。
     * 必须放在 buildTypes 之前，否则 buildTypes 里取不到它。
     */
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // 个人课表 App，不做商店发布；关掉噪音 lint 以保证离线构建稳定。
        disable += setOf("GradleDependency", "OldTargetApi", "UnusedResources")
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeVersion = "1.11.2"

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.compose.ui:ui:$composeVersion")
    implementation("androidx.compose.ui:ui-graphics:$composeVersion")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeVersion")
    implementation("androidx.compose.foundation:foundation:$composeVersion")
    implementation("androidx.compose.runtime:runtime:$composeVersion")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-core:1.7.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling:$composeVersion")

    testImplementation("junit:junit:4.13.2")
}
