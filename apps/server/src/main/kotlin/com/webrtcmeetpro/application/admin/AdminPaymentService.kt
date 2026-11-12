package com.webrtcmeetpro.application.admin

import com.webrtcmeetpro.config.properties.AdminProperties
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/** 관리자 "결제·환불" 목록 한 줄 */
data class AdminPaymentView(
    val orderId: String,
    val orderName: String,
    val amount: Long,
    val status: String,
    val paymentKind: String,
    val billingCycle: String?,
    val userId: Long,
    val userEmail: String?,
    val teamName: String?,
    val approvedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val refundedAmount: Long,
    /** 환불 시각 (canceled_at) */
    val refundedAt: LocalDateTime?,
    val refundReason: String?,
    val hasPaymentKey: Boolean,
)

/** 관리자 사이트 "결제·환불": 최근 결제 (주문번호·이메일·주문명·팀 이름으로 찾기) */
@Service
class AdminPaymentService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val props: AdminProperties,
) {
    /** 최근 결제 200건. q = 주문번호·이메일·주문명·팀 이름 일부, status = DONE | CANCELED | FAILED ... */
    fun recent(q: String?, status: String?): List<AdminPaymentView> {
        val params = MapSqlParameterSource()
        val conds = mutableListOf<String>()
        q?.trim()?.takeIf { it.isNotEmpty() }?.let {
            params.addValue("q", "%" + it.uppercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%")
            conds += "(UPPER(p.order_id) LIKE :q ESCAPE '\\' OR UPPER(u.email) LIKE :q ESCAPE '\\' OR UPPER(p.order_name) LIKE :q ESCAPE '\\' OR UPPER(t.name) LIKE :q ESCAPE '\\')"
        }
        status?.trim()?.takeIf { it.matches(Regex("[A-Z_]{2,20}")) }?.let {
            params.addValue("status", it)
            conds += "p.status = :status"
        }
        val where = if (conds.isEmpty()) "" else " WHERE " + conds.joinToString(" AND ")
        val prefix = props.tablePrefix
        return jdbc.query(
            """
            SELECT p.order_id, p.order_name, p.amount, p.status, p.payment_kind, p.billing_cycle, p.user_id, u.email, t.name team_name,
                   p.approved_at, p.created_dt, p.refunded_amount, p.canceled_at, p.refund_reason, p.payment_key
            FROM ${prefix}payments p
            LEFT JOIN ${prefix}users u ON u.user_id = p.user_id
            LEFT JOIN ${prefix}teams t ON t.team_id = p.team_id$where
            ORDER BY p.payment_id DESC FETCH FIRST 200 ROWS ONLY
            """.trimIndent(),
            params,
        ) { rs, _ ->
            AdminPaymentView(
                orderId = rs.getString("order_id"),
                orderName = rs.getString("order_name"),
                amount = rs.getLong("amount"),
                status = rs.getString("status"),
                paymentKind = rs.getString("payment_kind"),
                billingCycle = rs.getString("billing_cycle"),
                userId = rs.getLong("user_id"),
                userEmail = rs.getString("email"),
                teamName = rs.getString("team_name"),
                approvedAt = rs.getTimestamp("approved_at")?.toLocalDateTime(),
                createdAt = rs.getTimestamp("created_dt").toLocalDateTime(),
                refundedAmount = rs.getLong("refunded_amount"),
                refundedAt = rs.getTimestamp("canceled_at")?.toLocalDateTime(),
                refundReason = rs.getString("refund_reason"),
                hasPaymentKey = rs.getString("payment_key") != null,
            )
        }
    }
}
