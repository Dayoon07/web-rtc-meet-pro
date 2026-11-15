/*
 * Android 실행 앱(APK) 껍데기.
 * AGP 9 부터 kotlin.multiplatform 과 com.android.application 을 한 모듈에 둘 수 없어서
 * 화면·로직은 :composeApp(androidMain 포함)에 두고, 이 모듈은 applicationId·매니페스트·아이콘만 가진다.
 */
plugins {
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "com.webrtcmeetpro.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.webrtcmeetpro"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = libs.versions.appVersionCode.get().toInt()
        versionName = libs.versions.appVersionName.get()
    }

    // 서명 키와 비밀번호는 저장소에 두지 않고 ~/.gradle/gradle.properties 에서 읽는다. 없으면 서명 안 된 APK 가 나온다
    val storeFilePath = providers.gradleProperty("limeet.signing.storeFile").orNull
    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("limeet.signing.storePassword").get()
                keyAlias = providers.gradleProperty("limeet.signing.keyAlias").get()
                keyPassword = providers.gradleProperty("limeet.signing.keyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (storeFilePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(projects.composeApp)
}
