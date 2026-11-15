<#
  앱 빌드: Android APK + Windows 설치 파일(exe) -> 저장소 맨 위 dist\
    dist\mq-release-<버전>.apk, dist\mq-release-<버전>.exe  (버전: apps\kmp\gradle\libs.versions.toml 의 appVersionName)

  사용 (저장소 맨 위 폴더에서):
    deploy\app-build.bat                       https://mq.dayoon.me 에 붙는 앱
    deploy\app-build.bat https://다른주소      다른 서버에 붙는 앱

  필요한 것: ~/.gradle/gradle.properties 의 mq.signing.* (APK 서명), mq.firebase.* (앱 푸시, 없으면 푸시만 꺼짐)
#>
param([string]$ApiUrl = 'https://mq.dayoon.me')

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

# 이 PC 의 기본 java 는 25 라서, JDK 21 이 있으면 그것으로 빌드한다
if (Test-Path 'C:\Program Files\Java\jdk-21\bin\java.exe') { $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21' }

Set-Location (Join-Path $root 'apps\kmp')
Write-Host "[app-build] 서버 주소: $ApiUrl" -ForegroundColor Cyan
$start = Get-Date
& .\gradlew.bat :androidApp:distApk :composeApp:distExe "-Pmq.apiUrl=$ApiUrl"
if ($LASTEXITCODE -ne 0) {
    Write-Host '[app-build] 빌드에 실패했습니다. 위 로그를 확인하세요.' -ForegroundColor Red
    exit 1
}

Write-Host ('[app-build] 완료 ({0:N0}초):' -f ((Get-Date) - $start).TotalSeconds) -ForegroundColor Green
Get-ChildItem (Join-Path $root 'dist') | ForEach-Object { Write-Host ('  dist\{0}  ({1:N1} MB)' -f $_.Name, ($_.Length / 1MB)) }
