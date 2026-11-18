<#
  앱 빌드: Android APK + Windows 설치 파일(exe) -> 저장소 맨 위 dist\
    dist\mq-release-<버전>.apk, dist\mq-release-<버전>.exe  (버전: apps\kmp\gradle\libs.versions.toml 의 appVersionName)

  빌드할 때마다 버전 끝자리(1.0.0 -> 1.0.1)와 Android versionCode 를 하나씩 올린다.
  Windows 설치 프로그램은 버전이 더 높아야 이미 깔린 MQ 를 업데이트하고, 같은 버전이면 설치를 막기 때문이다.
  올라간 버전은 libs.versions.toml 에 남으니 커밋한다. 올리지 않으려면 -KeepVersion

  사용 (저장소 맨 위 폴더에서):
    deploy\app-build.bat                       https://mq.dayoon.me 에 붙는 앱
    deploy\app-build.bat https://다른주소      다른 서버에 붙는 앱
    deploy\app-build.bat -KeepVersion          버전을 올리지 않고 다시 빌드

  필요한 것: ~/.gradle/gradle.properties 의 mq.signing.* (APK 서명), mq.firebase.* (앱 푸시, 없으면 푸시만 꺼짐)
#>
param([string]$ApiUrl = 'https://mq.dayoon.me', [switch]$KeepVersion)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

# 이 PC 의 기본 java 는 25 라서, JDK 21 이 있으면 그것으로 빌드한다
if (Test-Path 'C:\Program Files\Java\jdk-21\bin\java.exe') { $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21' }

Set-Location (Join-Path $root 'apps\kmp')

# 버전 올리기 (빌드가 실패하면 되돌린다)
$versions = Join-Path (Get-Location) 'gradle\libs.versions.toml'
$utf8 = New-Object System.Text.UTF8Encoding $false
$before = [IO.File]::ReadAllText($versions, $utf8)
if (-not $KeepVersion) {
    $name = [regex]::Match($before, 'appVersionName = "(\d+)\.(\d+)\.(\d+)"')
    $code = [regex]::Match($before, 'appVersionCode = "(\d+)"')
    if (-not $name.Success -or -not $code.Success) { throw 'libs.versions.toml 에서 appVersionName / appVersionCode 를 찾지 못했습니다' }
    $current = '{0}.{1}.{2}' -f $name.Groups[1].Value, $name.Groups[2].Value, $name.Groups[3].Value
    $next = '{0}.{1}.{2}' -f $name.Groups[1].Value, $name.Groups[2].Value, ([int]$name.Groups[3].Value + 1)
    $after = $before.Replace($name.Value, ('appVersionName = "{0}"' -f $next)).Replace($code.Value, ('appVersionCode = "{0}"' -f ([int]$code.Groups[1].Value + 1)))
    [IO.File]::WriteAllText($versions, $after, $utf8)
    Write-Host "[app-build] 버전: $current -> $next" -ForegroundColor Cyan
}

Write-Host "[app-build] 서버 주소: $ApiUrl" -ForegroundColor Cyan
$start = Get-Date
& .\gradlew.bat :androidApp:distApk :composeApp:distExe "-Pmq.apiUrl=$ApiUrl"
if ($LASTEXITCODE -ne 0) {
    [IO.File]::WriteAllText($versions, $before, $utf8)
    Write-Host '[app-build] 빌드에 실패했습니다. 버전은 되돌렸습니다. 위 로그를 확인하세요.' -ForegroundColor Red
    exit 1
}

Write-Host ('[app-build] 완료 ({0:N0}초):' -f ((Get-Date) - $start).TotalSeconds) -ForegroundColor Green
Get-ChildItem (Join-Path $root 'dist') | ForEach-Object { Write-Host ('  dist\{0}  ({1:N1} MB)' -f $_.Name, ($_.Length / 1MB)) }
