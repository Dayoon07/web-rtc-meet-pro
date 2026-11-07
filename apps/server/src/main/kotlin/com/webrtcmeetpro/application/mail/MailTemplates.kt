package com.webrtcmeetpro.application.mail

import org.springframework.web.util.HtmlUtils
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 플랜 안내 메일 종류 */
enum class PlanMailKind { EXPIRING, EXPIRED, PAYMENT_FAILED }

/**
 * 메일 본문 (한국어·영어·일본어·중국어). 메일 프로그램마다 CSS 지원이 달라서 인라인 스타일만 쓴다.
 * 색은 웹과 같은 보라색(brand-600 #7c3aed).
 */
object MailTemplates {

    fun emailVerification(to: String, lang: MailLanguage, link: String, hours: Long): MailMessage {
        val t = when (lang) {
            MailLanguage.KO -> Texts(
                "[Limeet] 이메일 주소를 확인해 주세요",
                "이메일 주소 확인",
                "Limeet 에 가입해 주셔서 고마워요. 아래 버튼을 누르면 가입이 끝나요. 링크는 ${hours}시간 동안 쓸 수 있어요.",
                "이메일 확인하기",
                "직접 가입하지 않았다면 이 메일은 무시해도 돼요.",
            )
            MailLanguage.EN -> Texts(
                "[Limeet] Confirm your email address",
                "Confirm your email",
                "Thanks for signing up for Limeet. Click the button below to finish. The link works for $hours hours.",
                "Confirm email",
                "If you didn't sign up, you can ignore this email.",
            )
            MailLanguage.JA -> Texts(
                "[Limeet] メールアドレスを確認してください",
                "メールアドレスの確認",
                "Limeet にご登録いただきありがとうございます。下のボタンを押すと登録が完了します。リンクは${hours}時間有効です。",
                "メールアドレスを確認",
                "心当たりがない場合は、このメールを無視してください。",
            )
            MailLanguage.ZH -> Texts(
                "[Limeet] 请确认你的邮箱地址",
                "确认邮箱地址",
                "感谢注册 Limeet。点击下方按钮即可完成注册。链接 $hours 小时内有效。",
                "确认邮箱",
                "如果不是你本人注册，请忽略此邮件。",
            )
        }
        return build(to, t, link)
    }

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

    /** 플랜 만료 3일 전 / 만료됨 / 자동결제 실패 */
    fun planNotice(to: String, lang: MailLanguage, kind: PlanMailKind, planName: String, date: LocalDateTime?, reason: String?, link: String): MailMessage {
        val d = date?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) ?: "-"
        val why = reason?.let { " ($it)" } ?: ""
        val t = when (lang) {
            MailLanguage.KO -> when (kind) {
                PlanMailKind.EXPIRING -> Texts("[Limeet] $planName 플랜이 곧 끝나요", "$planName 플랜이 곧 끝나요",
                    "$planName 플랜 기간이 $d 에 끝나요. 자동결제가 꺼져 있어서, 계속 쓰려면 기간이 끝나기 전에 결제해 주세요.", "요금제 보기",
                    "기간이 끝나면 그 플랜 기능(녹화, 늘어난 인원 등)을 쓸 수 없어요. 만든 데이터는 지워지지 않아요.")
                PlanMailKind.EXPIRED -> Texts("[Limeet] $planName 플랜이 만료됐어요", "$planName 플랜이 만료됐어요",
                    "$planName 플랜 기간($d 까지)이 끝났어요. 지금부터는 남아 있는 다른 플랜이나 무료 플랜으로 쓰게 돼요.", "다시 결제하기",
                    "녹화 같은 플랜 기능은 다시 결제하면 바로 쓸 수 있어요.")
                PlanMailKind.PAYMENT_FAILED -> Texts("[Limeet] $planName 플랜 자동결제에 실패했어요", "자동결제에 실패했어요",
                    "$planName 플랜 자동결제가 되지 않았어요$why. $d 에 다시 시도해요. 카드를 확인하거나 다른 카드를 등록해 주세요.", "카드 확인하기",
                    "세 번 실패하면 플랜이 만료돼요.")
            }
            MailLanguage.EN -> when (kind) {
                PlanMailKind.EXPIRING -> Texts("[Limeet] Your $planName plan ends soon", "Your $planName plan ends soon",
                    "Your $planName plan ends on $d. Auto-renewal is off, so please pay before then to keep using it.", "View plans",
                    "When it ends, plan features (recording, larger limits) stop working. Your data is not deleted.")
                PlanMailKind.EXPIRED -> Texts("[Limeet] Your $planName plan has expired", "Your $planName plan has expired",
                    "Your $planName plan (until $d) has ended. You're now on your other remaining plan or the Free plan.", "Renew now",
                    "Pay again any time to get the plan features back right away.")
                PlanMailKind.PAYMENT_FAILED -> Texts("[Limeet] Auto-renewal failed for $planName", "Auto-renewal failed",
                    "We couldn't charge your card for the $planName plan$why. We'll try again on $d. Please check your card or register another one.", "Check card",
                    "After three failed attempts the plan expires.")
            }
            MailLanguage.JA -> when (kind) {
                PlanMailKind.EXPIRING -> Texts("[Limeet] $planName プランがまもなく終了します", "$planName プランがまもなく終了します",
                    "$planName プランは $d に終了します。自動決済がオフのため、続けて使うには期間内にお支払いください。", "料金プランを見る",
                    "終了するとプランの機能（録画など）は使えなくなります。データは削除されません。")
                PlanMailKind.EXPIRED -> Texts("[Limeet] $planName プランの期限が切れました", "$planName プランの期限が切れました",
                    "$planName プラン（$d まで）が終了しました。これからは残っている他のプランか無料プランになります。", "もう一度支払う",
                    "お支払いいただくとすぐにプランの機能が使えます。")
                PlanMailKind.PAYMENT_FAILED -> Texts("[Limeet] $planName プランの自動決済に失敗しました", "自動決済に失敗しました",
                    "$planName プランの自動決済ができませんでした$why。$d にもう一度試します。カードを確認するか、別のカードを登録してください。", "カードを確認",
                    "3回失敗するとプランは終了します。")
            }
            MailLanguage.ZH -> when (kind) {
                PlanMailKind.EXPIRING -> Texts("[Limeet] 你的 $planName 套餐即将到期", "$planName 套餐即将到期",
                    "你的 $planName 套餐将于 $d 到期。自动续费已关闭，如需继续使用请在到期前付款。", "查看套餐",
                    "到期后将无法使用套餐功能（录制等）。你的数据不会被删除。")
                PlanMailKind.EXPIRED -> Texts("[Limeet] 你的 $planName 套餐已到期", "$planName 套餐已到期",
                    "你的 $planName 套餐（至 $d）已结束。现在将使用你剩余的其他套餐或免费套餐。", "重新付款",
                    "重新付款后即可立即使用套餐功能。")
                PlanMailKind.PAYMENT_FAILED -> Texts("[Limeet] $planName 套餐自动续费失败", "自动续费失败",
                    "$planName 套餐自动扣款失败$why。我们将在 $d 再次尝试。请检查银行卡或绑定其他卡。", "检查银行卡",
                    "连续三次失败后套餐将到期。")
            }
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
