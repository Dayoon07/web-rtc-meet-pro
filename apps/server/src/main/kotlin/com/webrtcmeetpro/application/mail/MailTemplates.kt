package com.webrtcmeetpro.application.mail

import org.springframework.web.util.HtmlUtils

/**
 * 메일 본문 (한국어·영어·일본어·중국어). 메일 프로그램마다 CSS 지원이 달라서 인라인 스타일만 쓴다.
 * 색은 웹과 같은 보라색(brand-600 #7c3aed).
 */
object MailTemplates {

    fun passwordReset(to: String, lang: MailLanguage, link: String, minutes: Long): MailMessage {
        val t = when (lang) {
            MailLanguage.KO -> Texts(
                "[Limeet] 비밀번호 재설정",
                "비밀번호 재설정",
                "아래 버튼을 눌러 새 비밀번호를 정해 주세요. 링크는 ${minutes}분 동안, 한 번만 쓸 수 있어요.",
                "비밀번호 재설정하기",
                "직접 요청하지 않았다면 이 메일은 무시해도 돼요. 비밀번호는 바뀌지 않아요.",
            )
            MailLanguage.EN -> Texts(
                "[Limeet] Reset your password",
                "Reset your password",
                "Click the button below to choose a new password. The link works once and expires in $minutes minutes.",
                "Reset password",
                "If you didn't request this, you can ignore this email. Your password won't change.",
            )
            MailLanguage.JA -> Texts(
                "[Limeet] パスワードの再設定",
                "パスワードの再設定",
                "下のボタンから新しいパスワードを設定してください。リンクは${minutes}分間、1回だけ使えます。",
                "パスワードを再設定",
                "心当たりがない場合は、このメールを無視してください。パスワードは変更されません。",
            )
            MailLanguage.ZH -> Texts(
                "[Limeet] 重置密码",
                "重置密码",
                "请点击下方按钮设置新密码。链接仅可使用一次，${minutes} 分钟内有效。",
                "重置密码",
                "如果不是你本人操作，请忽略此邮件，你的密码不会改变。",
            )
        }
        return build(to, t, link)
    }

    fun teamInvitation(to: String, lang: MailLanguage, teamName: String, inviterName: String, link: String, registered: Boolean): MailMessage {
        val t = when (lang) {
            MailLanguage.KO -> Texts(
                "[Limeet] $inviterName 님이 「$teamName」 팀에 초대했어요",
                "「$teamName」 팀 초대",
                "$inviterName 님이 Limeet 의 「$teamName」 팀에 초대했어요. " +
                    if (registered) "로그인한 뒤 초대 목록에서 수락해 주세요." else "이 이메일 주소로 가입하면 초대 목록에서 수락할 수 있어요.",
                if (registered) "초대 확인하기" else "가입하고 초대 확인하기",
                "초대는 7일 뒤에 만료돼요. 모르는 사람이 보낸 초대라면 무시해도 돼요.",
            )
            MailLanguage.EN -> Texts(
                "[Limeet] $inviterName invited you to \"$teamName\"",
                "Join \"$teamName\"",
                "$inviterName invited you to the team \"$teamName\" on Limeet. " +
                    if (registered) "Log in and accept it from your invitations." else "Sign up with this email address to accept it from your invitations.",
                if (registered) "View invitation" else "Sign up and view invitation",
                "The invitation expires in 7 days. If you don't know the sender, you can ignore this email.",
            )
            MailLanguage.JA -> Texts(
                "[Limeet] ${inviterName} さんが「$teamName」に招待しました",
                "「$teamName」への招待",
                "${inviterName} さんが Limeet のチーム「$teamName」に招待しました。" +
                    if (registered) "ログインして招待一覧から承認してください。" else "このメールアドレスで登録すると、招待一覧から承認できます。",
                if (registered) "招待を確認" else "登録して招待を確認",
                "招待は7日後に期限切れになります。心当たりがない場合は無視してください。",
            )
            MailLanguage.ZH -> Texts(
                "[Limeet] $inviterName 邀请你加入「$teamName」",
                "加入「$teamName」",
                "$inviterName 邀请你加入 Limeet 的团队「$teamName」。" +
                    if (registered) "请登录后在邀请列表中接受。" else "使用此邮箱注册后，即可在邀请列表中接受。",
                if (registered) "查看邀请" else "注册并查看邀请",
                "邀请将在 7 天后过期。如果你不认识发送者，请忽略此邮件。",
            )
        }
        return build(to, t, link)
    }

    private data class Texts(val subject: String, val title: String, val body: String, val button: String, val footer: String)

    private fun build(to: String, t: Texts, link: String): MailMessage {
        val text = "${t.title}\n\n${t.body}\n\n${t.button}: $link\n\n${t.footer}\n\n— Limeet"
        val e = { s: String -> HtmlUtils.htmlEscape(s) }
        val html = """
            <div style="background:#f5f3ff;padding:32px 16px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Apple SD Gothic Neo','Malgun Gothic',sans-serif">
              <div style="max-width:480px;margin:0 auto;background:#ffffff;border-radius:16px;padding:32px;border:1px solid #ede9fe">
                <div style="font-size:20px;font-weight:800;color:#7c3aed;margin-bottom:24px">Limeet</div>
                <h1 style="font-size:20px;margin:0 0 12px;color:#111827">${e(t.title)}</h1>
                <p style="font-size:15px;line-height:1.6;color:#374151;margin:0 0 24px">${e(t.body)}</p>
                <a href="${e(link)}" style="display:inline-block;background:#7c3aed;color:#ffffff;text-decoration:none;font-weight:600;font-size:15px;padding:12px 20px;border-radius:10px">${e(t.button)}</a>
                <p style="font-size:12px;line-height:1.6;color:#6b7280;margin:24px 0 0;word-break:break-all">${e(link)}</p>
                <p style="font-size:13px;line-height:1.6;color:#6b7280;margin:16px 0 0">${e(t.footer)}</p>
              </div>
            </div>
        """.trimIndent()
        return MailMessage(to, t.subject, text, html)
    }
}
