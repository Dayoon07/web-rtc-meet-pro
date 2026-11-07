package com.webrtcmeetpro.platform

import androidx.compose.runtime.Composable

/**
 * 앱 안에서 화상회의 화면(웹 /meet/{id})을 띄울 수 있는지.
 * Android: 앱 안 웹뷰. Windows: 지금처럼 브라우저로 연다 (내장 브라우저를 넣으면 설치 파일이 100MB 넘게 커진다).
 */
expect val inAppMeetingSupported: Boolean

/**
 * 회의 화면. url 은 웹 로그인 넘김 주소(/oauth/callback?code=…&next=/meet/{id}).
 * 회의에서 나가면(웹이 /meet 밖으로 이동) onClose 를 부른다.
 */
@Composable
expect fun InAppMeeting(url: String, onClose: () -> Unit)
