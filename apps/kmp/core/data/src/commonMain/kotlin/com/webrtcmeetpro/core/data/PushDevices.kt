package com.webrtcmeetpro.core.data

/** 앱이 꺼져 있어도 알림을 받게 기기를 서버에 등록한다. Android 는 FCM, 데스크톱은 등록할 것이 없다 */
interface PushDevices {
    /** 로그인한 뒤에 부른다. 이 기기가 앱 푸시를 받을 수 있으면 true */
    suspend fun register(): Boolean

    /** 로그아웃·탈퇴 전에 부른다. 이 기기로 다른 사람의 알림이 오지 않게 서버에서 지운다 */
    suspend fun unregister()
}

object NoPushDevices : PushDevices {
    override suspend fun register() = false
    override suspend fun unregister() = Unit
}
