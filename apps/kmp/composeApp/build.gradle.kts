import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // AGP 9: 이 모듈은 Android "라이브러리" 타깃. 실행 APK 는 :androidApp 이 만든다
    android {
        namespace = "com.webrtcmeetpro.app"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources { enable = true }
    }
    jvm("desktop")

    // Android 와 Desktop 은 둘 다 JVM 이라 java.time·Locale 을 쓰는 코드(날짜 형식, 국가 이름 등)는
    // jvmSharedMain 한 곳에만 둔다. commonMain → jvmSharedMain → androidMain / desktopMain
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withJvm()
                withCompilations { it.platformType == org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            implementation(projects.core.network)
            implementation(projects.core.data)
            implementation(projects.core.designsystem)

            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.jetbrains.navigation.compose)
            implementation(libs.kotlinx.coroutines.core)

            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
            implementation(libs.firebase.messaging)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
}

/*
 * 운영 서버 주소를 빌드할 때 넣는다. 비우면 플랫폼별 개발 주소(에뮬레이터 10.0.2.2, 데스크톱 localhost).
 *   ./gradlew :androidApp:assembleRelease -Pmq.apiUrl=https://example.com -Pmq.webUrl=https://example.com
 */
val mqApiUrl = providers.gradleProperty("mq.apiUrl").orElse("")
val mqWebUrl = providers.gradleProperty("mq.webUrl").orElse(mqApiUrl)
val buildEnvDir = layout.buildDirectory.dir("generated/buildEnv/kotlin")
val generateBuildEnv by tasks.registering {
    inputs.property("apiUrl", mqApiUrl)
    inputs.property("webUrl", mqWebUrl)
    outputs.dir(buildEnvDir)
    doLast {
        val file = buildEnvDir.get().file("com/webrtcmeetpro/BuildEnv.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.webrtcmeetpro
            |
            |/** 빌드할 때 -Pmq.apiUrl / -Pmq.webUrl 로 넣은 운영 주소 (자동 생성 파일) */
            |object BuildEnv {
            |    const val API_URL: String = "${mqApiUrl.get()}"
            |    const val WEB_URL: String = "${mqWebUrl.get()}"
            |}
            |""".trimMargin(),
        )
    }
}
kotlin.sourceSets.commonMain { kotlin.srcDir(generateBuildEnv) }

compose.resources {
    packageOfResClass = "com.webrtcmeetpro.resources"
}

compose.desktop {
    application {
        mainClass = "com.webrtcmeetpro.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "MQ"
            // Android 와 같은 버전. Windows 설치 파일은 MAJOR.MINOR.BUILD 숫자만 받고, 올려야 기존 설치 위에 업데이트된다
            packageVersion = libs.versions.appVersionName.get()
            windows {
                menu = true
                shortcut = true
                // 업그레이드 설치 시 같은 앱으로 인식되도록 고정
                upgradeUuid = "6c1f5f0e-8f0b-4a53-9a3e-6f2a3c7e9d41"
            }
        }
    }
}

/** deploy/app-build.bat 이 쓴다. Windows 설치 파일을 저장소 맨 위 dist/ 에 mq-release-<버전>.exe 로 */
tasks.register<Copy>("distExe") {
    val version = libs.versions.appVersionName.get()
    dependsOn("packageExe")
    from(layout.buildDirectory.dir("compose/binaries/main/exe")) { include("*.exe") }
    into(rootProject.layout.projectDirectory.dir("../../dist"))
    rename { "mq-release-$version.exe" }
}
