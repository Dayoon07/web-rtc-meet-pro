/*
 * IntelliJ 에서 저장소 루트를 열면 서버와 앱(KMP)을 한 번에 Gradle 프로젝트로 가져오기 위한 컴포지트 빌드.
 * 각 빌드는 지금처럼 따로 실행해도 된다 (apps/server/gradlew, apps/kmp/gradlew).
 * 루트에서 실행할 때(빌드 이름 = 폴더 이름): ./gradlew :server:bootRun, ./gradlew :kmp:composeApp:run
 */
rootProject.name = "web-rtc-meet-pro"

includeBuild("apps/server")
includeBuild("apps/kmp")
