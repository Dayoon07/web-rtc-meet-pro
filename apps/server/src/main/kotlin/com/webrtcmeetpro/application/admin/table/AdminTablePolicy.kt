package com.webrtcmeetpro.application.admin.table

/**
 * 관리자 사이트 "데이터 관리"의 규칙. 테이블·컬럼 구조는 DB(Oracle 사전)에서 바로 읽으므로,
 * 운영 중에 테이블을 새로 만들어도 여기를 고치지 않아도 화면에 나타나고 고칠 수 있다.
 * 여기에는 "기본값과 다르게 다뤄야 하는 것"만 적는다.
 *
 * 기본 규칙
 *   - PK·IDENTITY·공통 컬럼(created_*, edited_*)은 읽기 전용, deleted_yn 은 고칠 수 있다 (소프트 삭제/복구)
 *   - 비밀값 컬럼(아래 isSecret)은 값을 보여 주지 않고 고칠 수도 없다
 *   - 행 삭제(DELETE)는 없다. deleted_yn = 'Y' 로 숨긴다
 *   - 행 추가는 insertableTables 에 있는 테이블만 (회원처럼 서버 로직(비밀번호 암호화 등)을 거쳐야 하는 테이블은 막는다)
 */
object AdminTablePolicy {

    data class TableInfo(val group: String, val label: String, val description: String? = null)

    /** 화면에 보일 이름과 묶음. 없는 테이블은 DB 주석(COMMENT ON TABLE) → 테이블 이름 순으로 쓴다 */
    val tables: Map<String, TableInfo> = linkedMapOf(
        "users" to TableInfo("회원/인증", "회원", "탈퇴한 회원은 status = DELETED, deleted_yn = Y"),
        "user_social_accounts" to TableInfo("회원/인증", "구글 연결"),
        "refresh_tokens" to TableInfo("회원/인증", "로그인 세션", "revoked_at 이 비어 있으면 아직 로그인 상태"),
        "login_histories" to TableInfo("회원/인증", "로그인 기록"),
        "plans" to TableInfo("플랜/결제", "플랜", "가격·한도를 바꾸면 바로 적용된다"),
        "subscriptions" to TableInfo("플랜/결제", "구독"),
        "billing_keys" to TableInfo("플랜/결제", "카드(빌링키)"),
        "payments" to TableInfo("플랜/결제", "결제"),
        "teams" to TableInfo("팀", "팀"),
        "team_members" to TableInfo("팀", "팀원"),
        "team_invitations" to TableInfo("팀", "팀 초대"),
        "chat_rooms" to TableInfo("채팅", "채팅방"),
        "chat_room_members" to TableInfo("채팅", "채팅방 참여자"),
        "chat_messages" to TableInfo("채팅", "채팅 메시지", "부적절한 메시지는 deleted_yn = Y 로 숨긴다"),
        "chat_message_files" to TableInfo("채팅", "채팅 첨부파일"),
        "meetings" to TableInfo("화상회의", "회의"),
        "meeting_invitees" to TableInfo("화상회의", "회의 초대 대상"),
        "meeting_participants" to TableInfo("화상회의", "회의 참가 기록"),
        "meeting_events" to TableInfo("화상회의", "회의 이벤트"),
        "meeting_recordings" to TableInfo("화상회의", "회의 녹화"),
        "notifications" to TableInfo("알림", "알림"),
        "admin_error_logs" to TableInfo("관리자", "에러 기록", "자세히 보기는 왼쪽 메뉴의 에러 기록 화면이 편하다"),
        "admin_data_changes" to TableInfo("관리자", "데이터 변경 기록", "이 화면에서 고친 내용이 모두 남는다"),
    )

    /** 보기만 하는 테이블: 기록·결제처럼 고치면 안 되거나, 서버만 쓰는 테이블 */
    val readOnlyTables = setOf(
        "refresh_tokens", "login_histories", "billing_keys", "payments", "meeting_events", "admin_data_changes",
    )

    /** 행 추가를 허용하는 테이블 */
    val insertableTables = setOf("plans")

    /** 어떤 테이블에서도 고칠 수 없는 컬럼 (공통 컬럼 중 생성·수정 기록) */
    val auditColumns = setOf("created_dt", "created_us", "edited_dt", "edited_us")

    /** 비밀값: 값 대신 "있음/없음"만 보여 주고, 검색·수정·내보내기에서 뺀다 */
    fun isSecret(column: String): Boolean =
        column in setOf("password_hash", "token_hash", "token", "billing_key", "customer_key", "payment_key") ||
            column.endsWith("_hash") || "secret" in column || "password" in column

    /** 테이블별로 따로 막는 컬럼 */
    val readOnlyColumns: Map<String, Set<String>> = mapOf(
        // 탈퇴 처리(개인정보 지우기)는 서버 로직으로만. 상태만 바꾸면 반쯤 탈퇴한 계정이 생긴다
        "users" to setOf("last_login_at"),
        "chat_messages" to setOf("room_id", "sender_id"),
    )

    fun infoOf(table: String): TableInfo? = tables[table]

    /** 같은 묶음끼리, 묶음 안에서는 위 목록 순서대로. 목록에 없는 새 테이블은 "기타" 묶음 끝에 */
    fun order(table: String): Int = tables.keys.indexOf(table).let { if (it < 0) Int.MAX_VALUE else it }
}
