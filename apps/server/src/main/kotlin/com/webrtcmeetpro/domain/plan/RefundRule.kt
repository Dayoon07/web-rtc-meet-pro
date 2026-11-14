package com.webrtcmeetpro.domain.plan

/** FULL_7_DAYS = 결제 7일 안이라 전액, PRORATED = 쓴 날수만큼 빼고, NONE = 환불할 수 없음 */
enum class RefundRule { FULL_7_DAYS, PRORATED, NONE }
