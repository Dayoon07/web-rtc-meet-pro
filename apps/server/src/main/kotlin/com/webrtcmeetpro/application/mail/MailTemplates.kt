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
                "[MQ] 이메일 주소를 확인해 주세요",
                "이메일 주소 확인",
                "MQ 에 가입해 주셔서 고마워요. 아래 버튼을 누르면 가입이 끝나요. 링크는 ${hours}시간 동안 쓸 수 있어요.",
                "이메일 확인하기",
                "직접 가입하지 않았다면 이 메일은 무시해도 돼요.",
            )
            MailLanguage.EN -> Texts(
                "[MQ] Confirm your email address",
                "Confirm your email",
                "Thanks for signing up for MQ. Click the button below to finish. The link works for $hours hours.",
                "Confirm email",
                "If you didn't sign up, you can ignore this email.",
            )
            MailLanguage.JA -> Texts(
                "[MQ] メールアドレスを確認してください",
                "メールアドレスの確認",
                "MQ にご登録いただきありがとうございます。下のボタンを押すと登録が完了します。リンクは${hours}時間有効です。",
                "メールアドレスを確認",
                "心当たりがない場合は、このメールを無視してください。",
            )
            MailLanguage.ZH -> Texts(
                "[MQ] 请确认你的邮箱地址",
                "确认邮箱地址",
                "感谢注册 MQ。点击下方按钮即可完成注册。链接 $hours 小时内有效。",
                "确认邮箱",
                "如果不是你本人注册，请忽略此邮件。",
            )
        }
        return build(to, t, link)
    }

    fun passwordReset(to: String, lang: MailLanguage, link: String, minutes: Long): MailMessage {
        val t = when (lang) {
            MailLanguage.KO -> Texts(
                "[MQ] 비밀번호 재설정",
                "비밀번호 재설정",
                "아래 버튼을 눌러 새 비밀번호를 정해 주세요. 링크는 ${minutes}분 동안, 한 번만 쓸 수 있어요.",
                "비밀번호 재설정하기",
                "직접 요청하지 않았다면 이 메일은 무시해도 돼요. 비밀번호는 바뀌지 않아요.",
            )
            MailLanguage.EN -> Texts(
                "[MQ] Reset your password",
                "Reset your password",
                "Click the button below to choose a new password. The link works once and expires in $minutes minutes.",
                "Reset password",
                "If you didn't request this, you can ignore this email. Your password won't change.",
            )
            MailLanguage.JA -> Texts(
                "[MQ] パスワードの再設定",
                "パスワードの再設定",
                "下のボタンから新しいパスワードを設定してください。リンクは${minutes}分間、1回だけ使えます。",
                "パスワードを再設定",
                "心当たりがない場合は、このメールを無視してください。パスワードは変更されません。",
            )
            MailLanguage.ZH -> Texts(
                "[MQ] 重置密码",
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
                "[MQ] $inviterName 님이 「$teamName」 팀에 초대했어요",
                "「$teamName」 팀 초대",
                "$inviterName 님이 MQ 의 「$teamName」 팀에 초대했어요. " +
                    if (registered) "로그인한 뒤 초대 목록에서 수락해 주세요." else "이 이메일 주소로 가입하면 초대 목록에서 수락할 수 있어요.",
                if (registered) "초대 확인하기" else "가입하고 초대 확인하기",
                "초대는 7일 뒤에 만료돼요. 모르는 사람이 보낸 초대라면 무시해도 돼요.",
            )
            MailLanguage.EN -> Texts(
                "[MQ] $inviterName invited you to \"$teamName\"",
                "Join \"$teamName\"",
                "$inviterName invited you to the team \"$teamName\" on MQ. " +
                    if (registered) "Log in and accept it from your invitations." else "Sign up with this email address to accept it from your invitations.",
                if (registered) "View invitation" else "Sign up and view invitation",
                "The invitation expires in 7 days. If you don't know the sender, you can ignore this email.",
            )
            MailLanguage.JA -> Texts(
                "[MQ] ${inviterName} さんが「$teamName」に招待しました",
                "「$teamName」への招待",
                "${inviterName} さんが MQ のチーム「$teamName」に招待しました。" +
                    if (registered) "ログインして招待一覧から承認してください。" else "このメールアドレスで登録すると、招待一覧から承認できます。",
                if (registered) "招待を確認" else "登録して招待を確認",
                "招待は7日後に期限切れになります。心当たりがない場合は無視してください。",
            )
            MailLanguage.ZH -> Texts(
                "[MQ] $inviterName 邀请你加入「$teamName」",
                "加入「$teamName」",
                "$inviterName 邀请你加入 MQ 的团队「$teamName」。" +
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
                PlanMailKind.EXPIRING -> Texts("[MQ] $planName 플랜이 곧 끝나요", "$planName 플랜이 곧 끝나요",
                    "$planName 플랜 기간이 $d 에 끝나요. 자동결제가 꺼져 있어서, 계속 쓰려면 기간이 끝나기 전에 결제해 주세요.", "요금제 보기",
                    "기간이 끝나면 그 플랜 기능(녹화, 늘어난 인원 등)을 쓸 수 없어요. 만든 데이터는 지워지지 않아요.")
                PlanMailKind.EXPIRED -> Texts("[MQ] $planName 플랜이 만료됐어요", "$planName 플랜이 만료됐어요",
                    "$planName 플랜 기간($d 까지)이 끝났어요. 지금부터는 남아 있는 다른 플랜이나 무료 플랜으로 쓰게 돼요.", "다시 결제하기",
                    "녹화 같은 플랜 기능은 다시 결제하면 바로 쓸 수 있어요.")
                PlanMailKind.PAYMENT_FAILED -> Texts("[MQ] $planName 플랜 자동결제에 실패했어요", "자동결제에 실패했어요",
                    "$planName 플랜 자동결제가 되지 않았어요$why. $d 에 다시 시도해요. 카드를 확인하거나 다른 카드를 등록해 주세요.", "카드 확인하기",
                    "세 번 실패하면 플랜이 만료돼요.")
            }
            MailLanguage.EN -> when (kind) {
                PlanMailKind.EXPIRING -> Texts("[MQ] Your $planName plan ends soon", "Your $planName plan ends soon",
                    "Your $planName plan ends on $d. Auto-renewal is off, so please pay before then to keep using it.", "View plans",
                    "When it ends, plan features (recording, larger limits) stop working. Your data is not deleted.")
                PlanMailKind.EXPIRED -> Texts("[MQ] Your $planName plan has expired", "Your $planName plan has expired",
                    "Your $planName plan (until $d) has ended. You're now on your other remaining plan or the Free plan.", "Renew now",
                    "Pay again any time to get the plan features back right away.")
                PlanMailKind.PAYMENT_FAILED -> Texts("[MQ] Auto-renewal failed for $planName", "Auto-renewal failed",
                    "We couldn't charge your card for the $planName plan$why. We'll try again on $d. Please check your card or register another one.", "Check card",
                    "After three failed attempts the plan expires.")
            }
            MailLanguage.JA -> when (kind) {
                PlanMailKind.EXPIRING -> Texts("[MQ] $planName プランがまもなく終了します", "$planName プランがまもなく終了します",
                    "$planName プランは $d に終了します。自動決済がオフのため、続けて使うには期間内にお支払いください。", "料金プランを見る",
                    "終了するとプランの機能（録画など）は使えなくなります。データは削除されません。")
                PlanMailKind.EXPIRED -> Texts("[MQ] $planName プランの期限が切れました", "$planName プランの期限が切れました",
                    "$planName プラン（$d まで）が終了しました。これからは残っている他のプランか無料プランになります。", "もう一度支払う",
                    "お支払いいただくとすぐにプランの機能が使えます。")
                PlanMailKind.PAYMENT_FAILED -> Texts("[MQ] $planName プランの自動決済に失敗しました", "自動決済に失敗しました",
                    "$planName プランの自動決済ができませんでした$why。$d にもう一度試します。カードを確認するか、別のカードを登録してください。", "カードを確認",
                    "3回失敗するとプランは終了します。")
            }
            MailLanguage.ZH -> when (kind) {
                PlanMailKind.EXPIRING -> Texts("[MQ] 你的 $planName 套餐即将到期", "$planName 套餐即将到期",
                    "你的 $planName 套餐将于 $d 到期。自动续费已关闭，如需继续使用请在到期前付款。", "查看套餐",
                    "到期后将无法使用套餐功能（录制等）。你的数据不会被删除。")
                PlanMailKind.EXPIRED -> Texts("[MQ] 你的 $planName 套餐已到期", "$planName 套餐已到期",
                    "你的 $planName 套餐（至 $d）已结束。现在将使用你剩余的其他套餐或免费套餐。", "重新付款",
                    "重新付款后即可立即使用套餐功能。")
                PlanMailKind.PAYMENT_FAILED -> Texts("[MQ] $planName 套餐自动续费失败", "自动续费失败",
                    "$planName 套餐自动扣款失败$why。我们将在 $d 再次尝试。请检查银行卡或绑定其他卡。", "检查银行卡",
                    "连续三次失败后套餐将到期。")
            }
        }
        return build(to, t, link)
    }

    /**
     * 멤버가 플랜 한도를 넘음 (소유자에게, 하루 한 번까지).
     * @param chargeDate 초과 요금이 들어갈 결제일, perMember 1명당 한 달 요금
     */
    fun memberOverage(
        to: String, lang: MailLanguage, teamName: String, members: Long, limit: Int, excess: Long, perMember: Long,
        chargeDate: LocalDateTime?, link: String,
    ): MailMessage {
        val d = chargeDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) ?: "-"
        val won = { v: Long -> "%,d".format(v) }
        val extra = won(excess * perMember)
        val t = when (lang) {
            MailLanguage.KO -> Texts("[MQ] 「$teamName」 멤버가 플랜 한도를 넘었어요", "멤버가 플랜 한도를 넘었어요",
                "「$teamName」에 멤버가 들어와 내 모든 팀 인원이 ${members}명으로, 플랜 한도(${limit}명)보다 ${excess}명 많아요. " +
                    "동의하신 대로 다음 결제($d)에 초과 인원 요금 ${won(perMember)}원 × ${excess}명 = ${extra}원(부가세 포함)이 더해져요. " +
                    "결제일의 멤버 수로 계산하니, 그 전에 멤버를 정리하면 청구되지 않아요.", "요금제 보기",
                "결제 3일 전에 금액을 다시 알려 드려요. 초과 허용은 팀 요금제 화면에서 끌 수 있어요 (초과한 멤버가 없을 때).")
            MailLanguage.EN -> Texts("[MQ] \"$teamName\" is over its member limit", "Your team is over its member limit",
                "With the new member in \"$teamName\", your teams now have $members members in total, $excess more than the plan limit of $limit. " +
                    "As you agreed, your next payment ($d) will include ${won(perMember)} KRW × $excess = $extra KRW (VAT included). " +
                    "We count members on the payment date, so removing members before then means no extra charge.", "View billing",
                "We'll remind you of the amount 3 days before the payment. You can turn this off on the billing page when no one is over the limit.")
            MailLanguage.JA -> Texts("[MQ]「$teamName」のメンバーがプランの上限を超えました", "メンバーがプランの上限を超えました",
                "「$teamName」にメンバーが加わり、全チームの合計が${members}人になり、プランの上限（${limit}人）より${excess}人多くなっています。" +
                    "ご同意のとおり、次回のお支払い（$d）に超過料金 ${won(perMember)}ウォン × ${excess}人 = ${extra}ウォン（税込）が加算されます。" +
                    "支払日のメンバー数で計算するため、それまでに整理すれば請求されません。", "料金プランを見る",
                "お支払いの3日前に金額をもう一度お知らせします。")
            MailLanguage.ZH -> Texts("[MQ]「$teamName」成员已超出套餐上限", "成员已超出套餐上限",
                "「$teamName」加入新成员后，你所有团队合计 $members 名成员，比套餐上限（$limit 人）多 $excess 人。" +
                    "按照你的同意，下次付款（$d）将加收超额费用 ${won(perMember)} 韩元 × $excess = $extra 韩元（含税）。" +
                    "按付款当天的成员数计算，在此之前减少成员则不会收费。", "查看套餐",
                "付款前 3 天会再次通知金额。")
        }
        return build(to, t, link)
    }

    /** 3일 뒤 결제에 초과 인원 요금이 들어감 (전자상거래: 증액 결제 전 고지) */
    fun overageChargeSoon(
        to: String, lang: MailLanguage, teamName: String, planLabel: String?, planAmount: Long, excess: Long, perMember: Long,
        chargeDate: LocalDateTime, link: String,
    ): MailMessage {
        val d = chargeDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val won = { v: Long -> "%,d".format(v) }
        val total = won(planAmount + excess * perMember)
        val t = when (lang) {
            MailLanguage.KO -> Texts("[MQ] $d 결제 예정 금액 안내 (「$teamName」)", "3일 뒤 결제 예정이에요",
                (if (planLabel != null) "$planLabel ${won(planAmount)}원 + " else "") +
                    "초과 인원 ${excess}명 × ${won(perMember)}원 = 합계 ${total}원(부가세 포함)이 $d 에 등록된 카드로 결제돼요. " +
                    "초과 인원은 결제일의 멤버 수로 다시 계산해요.", "결제 정보 확인",
                "금액이 다르거나 결제를 원하지 않으면 결제일 전에 요금제 화면에서 자동결제를 끄거나 멤버를 정리해 주세요.")
            MailLanguage.EN -> Texts("[MQ] Upcoming payment on $d (\"$teamName\")", "Payment in 3 days",
                (if (planLabel != null) "$planLabel ${won(planAmount)} KRW + " else "") +
                    "$excess extra members × ${won(perMember)} KRW = $total KRW (VAT included) will be charged to your card on $d. " +
                    "Extra members are counted again on the payment date.", "Check billing",
                "To avoid the charge, turn off auto-renewal or remove members before the payment date.")
            MailLanguage.JA -> Texts("[MQ] $d のお支払い予定（「$teamName」）", "3日後にお支払いがあります",
                (if (planLabel != null) "$planLabel ${won(planAmount)}ウォン + " else "") +
                    "超過メンバー${excess}人 × ${won(perMember)}ウォン = 合計${total}ウォン（税込）が $d に登録カードで決済されます。", "お支払い情報を確認",
                "決済を望まない場合は、支払日までに自動決済をオフにするかメンバーを整理してください。")
            MailLanguage.ZH -> Texts("[MQ] $d 付款预告（「$teamName」）", "3 天后将付款",
                (if (planLabel != null) "$planLabel ${won(planAmount)} 韩元 + " else "") +
                    "超额成员 $excess 人 × ${won(perMember)} 韩元 = 合计 $total 韩元（含税）将于 $d 从已绑定的卡扣款。", "查看付款信息",
                "如不希望扣款，请在付款日前关闭自动续费或减少成员。")
        }
        return build(to, t, link)
    }

    private data class Texts(val subject: String, val title: String, val body: String, val button: String, val footer: String)

    private fun build(to: String, t: Texts, link: String): MailMessage {
        val text = "${t.title}\n\n${t.body}\n\n${t.button}: $link\n\n${t.footer}\n\n— MQ"
        val e = { s: String -> HtmlUtils.htmlEscape(s) }
        val html = """
            <div style="background:#f5f3ff;padding:32px 16px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Apple SD Gothic Neo','Malgun Gothic',sans-serif">
              <div style="max-width:480px;margin:0 auto;background:#ffffff;border-radius:16px;padding:32px;border:1px solid #ede9fe">
                <div style="font-size:20px;font-weight:800;color:#7c3aed;margin-bottom:24px">MQ</div>
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
