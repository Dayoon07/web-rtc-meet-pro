package com.webrtcmeetpro.application.push

/**
 * 푸시 알림 문장 (브라우저가 화면에 띄울 제목·내용). 화면의 i18n 은 서비스 워커에 없어서 서버가 만든다.
 * 언어는 구독할 때의 화면 언어(ko·en·ja·zh). 없으면 한국어
 */
object PushTexts {
    private val texts = mapOf(
        "ko" to mapOf(
            "file" to "파일을 보냈어요",
            "mention" to "{sender}님이 회원님을 언급했어요",
            "MEETING_REMINDER" to "「{title}」 회의가 곧 시작해요",
            "MEETING_STARTED" to "「{title}」 회의가 시작됐어요",
            "MEETING_SCHEDULED" to "「{title}」 회의가 예약됐어요",
            "TEAM_INVITATION" to "{inviterName}님이 「{teamName}」 팀에 초대했어요",
            "CHAT_ROOM_INVITED" to "{inviterName}님이 「{roomName}」 채널에 초대했어요",
            "MEETING_NOTES_READY" to "「{title}」 AI 회의록이 준비됐어요",
            "other" to "새 알림이 있어요",
        ),
        "en" to mapOf(
            "file" to "Sent a file",
            "mention" to "{sender} mentioned you",
            "MEETING_REMINDER" to "“{title}” is starting soon",
            "MEETING_STARTED" to "“{title}” has started",
            "MEETING_SCHEDULED" to "“{title}” was scheduled",
            "TEAM_INVITATION" to "{inviterName} invited you to “{teamName}”",
            "CHAT_ROOM_INVITED" to "{inviterName} added you to “{roomName}”",
            "MEETING_NOTES_READY" to "AI notes for “{title}” are ready",
            "other" to "You have a new notification",
        ),
        "ja" to mapOf(
            "file" to "ファイルを送信しました",
            "mention" to "{sender}さんがあなたをメンションしました",
            "MEETING_REMINDER" to "「{title}」がまもなく始まります",
            "MEETING_STARTED" to "「{title}」が始まりました",
            "MEETING_SCHEDULED" to "「{title}」が予約されました",
            "TEAM_INVITATION" to "{inviterName}さんが「{teamName}」に招待しました",
            "CHAT_ROOM_INVITED" to "{inviterName}さんが「{roomName}」に招待しました",
            "MEETING_NOTES_READY" to "「{title}」のAI議事録ができました",
            "other" to "新しいお知らせがあります",
        ),
        "zh" to mapOf(
            "file" to "发送了文件",
            "mention" to "{sender} 提及了你",
            "MEETING_REMINDER" to "「{title}」即将开始",
            "MEETING_STARTED" to "「{title}」已开始",
            "MEETING_SCHEDULED" to "「{title}」已预约",
            "TEAM_INVITATION" to "{inviterName} 邀请你加入「{teamName}」",
            "CHAT_ROOM_INVITED" to "{inviterName} 邀请你加入「{roomName}」",
            "MEETING_NOTES_READY" to "「{title}」的 AI 会议纪要已生成",
            "other" to "你有一条新通知",
        ),
    )

    fun text(lang: String?, key: String, params: Map<String, Any?> = emptyMap()): String {
        val table = texts[lang?.take(2)] ?: texts.getValue("ko")
        val template = table[key] ?: table.getValue("other")
        return params.entries.fold(template) { acc, (k, v) -> acc.replace("{$k}", v?.toString().orEmpty()) }
    }
}
