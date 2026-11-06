rootProject.name = "limeet-pro-kmp"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

// 앱: 화면·네비게이션·DI (commonMain / androidMain / desktopMain)
include(":composeApp")
// Android 실행 진입점 (AGP 9 부터 KMP 모듈과 com.android.application 을 한 모듈에 둘 수 없음)
include(":androidApp")

// 공통 모듈
include(":core:model")        // 서버 API 모델 (@Serializable)
include(":core:network")      // Ktor 클라이언트, 토큰 갱신, 에러 매핑
include(":core:data")         // 세션 저장(DataStore), 리포지토리
include(":core:designsystem") // 테마(보라 · 라이트/다크), 공통 컴포넌트
