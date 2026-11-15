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

        // google-services.json 대신 ~/.gradle/gradle.properties 의 mq.firebase.* 로 Firebase 를 켠다 (값이 없으면 앱 푸시만 꺼진다).
        // FirebaseInitProvider 가 이 리소스 이름들을 읽어서 자동으로 초기화한다
        val firebase = mapOf(
            "google_app_id" to "mq.firebase.appId",
            "google_api_key" to "mq.firebase.apiKey",
            "gcm_defaultSenderId" to "mq.firebase.senderId",
            "project_id" to "mq.firebase.projectId",
        ).mapValues { providers.gradleProperty(it.value).orNull }
        if (firebase.values.all { it != null }) firebase.forEach { (name, value) -> resValue("string", name, value!!) }
    }

    // 서명 키와 비밀번호는 저장소에 두지 않고 ~/.gradle/gradle.properties 에서 읽는다. 없으면 서명 안 된 APK 가 나온다
    val storeFilePath = providers.gradleProperty("mq.signing.storeFile").orNull
    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("mq.signing.storePassword").get()
                keyAlias = providers.gradleProperty("mq.signing.keyAlias").get()
                keyPassword = providers.gradleProperty("mq.signing.keyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (storeFilePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        resValues = true
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
