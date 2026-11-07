package com.webrtcmeetpro.common.error

import org.springframework.http.HttpStatus

/**
 * 클라이언트는 code 로 번역 문구를 찾는다(i18n: errors.<CODE>). message 는 번역이 없을 때의 한국어 대체 문구.
 */
enum class ErrorCode(val status: HttpStatus, val message: String) {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // auth / user
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
    USER_NOT_ACTIVE(HttpStatus.FORBIDDEN, "사용할 수 없는 계정입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    SOCIAL_ACCOUNT_ONLY(HttpStatus.UNAUTHORIZED, "구글로 가입한 계정입니다. 구글로 로그인해 주세요."),
    TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "필수 약관에 동의해 주세요."),
    OAUTH_CODE_INVALID(HttpStatus.UNAUTHORIZED, "로그인 시간이 만료되었습니다. 다시 시도해 주세요."),
    OAUTH_EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증이 완료된 구글 계정만 사용할 수 있습니다."),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "JPG, PNG, WEBP, GIF 이미지(5MB 이하)만 올릴 수 있습니다."),
    INVALID_NAME(HttpStatus.BAD_REQUEST, "이름은 1~50자로 입력해 주세요."),
    INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "닉네임은 1~50자로 입력해 주세요."),
    INVALID_PHONE(HttpStatus.BAD_REQUEST, "전화번호 형식이 올바르지 않습니다."),
    INVALID_COUNTRY(HttpStatus.BAD_REQUEST, "국가 코드가 올바르지 않습니다."),
    UNDERAGE(HttpStatus.BAD_REQUEST, "만 14세 이상만 가입할 수 있습니다."),

    // plan
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "플랜을 찾을 수 없습니다."),
    PAID_PLAN_REQUIRES_PAYMENT(HttpStatus.BAD_REQUEST, "유료 플랜은 결제를 진행해 주세요."),
    PLAN_DOWNGRADE_BLOCKED(HttpStatus.FORBIDDEN, "소유한 팀이 많아 이 플랜으로 바꿀 수 없습니다."),
    PLAN_TEAM_LIMIT(HttpStatus.FORBIDDEN, "현재 플랜에서 만들 수 있는 팀 수를 넘었습니다."),
    PLAN_MEMBER_LIMIT(HttpStatus.FORBIDDEN, "현재 플랜에서 초대할 수 있는 팀원 수를 넘었습니다."),
    PLAN_GROUP_ROOM_LIMIT(HttpStatus.FORBIDDEN, "현재 플랜에서 만들 수 있는 그룹 채팅방 수를 넘었습니다."),

    // team
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "팀을 찾을 수 없습니다."),
    NOT_TEAM_MEMBER(HttpStatus.FORBIDDEN, "이 팀의 팀원이 아닙니다."),
    NOT_TEAM_OWNER(HttpStatus.FORBIDDEN, "팀을 만든 사람만 할 수 있습니다."),
    CANNOT_INVITE(HttpStatus.FORBIDDEN, "팀원을 초대할 권한이 없습니다."),
    CANNOT_INVITE_SELF(HttpStatus.BAD_REQUEST, "자기 자신은 초대할 수 없습니다."),
    ALREADY_TEAM_MEMBER(HttpStatus.CONFLICT, "이미 팀원입니다."),
    INVITATION_ALREADY_PENDING(HttpStatus.CONFLICT, "이미 초대를 보낸 이메일입니다."),
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "초대를 찾을 수 없거나 만료되었습니다."),
    OWNER_ROLE_FIXED(HttpStatus.BAD_REQUEST, "팀을 만든 사람의 역할은 바꿀 수 없습니다."),
    CANNOT_CHANGE_OWN_ROLE(HttpStatus.BAD_REQUEST, "자기 역할은 바꿀 수 없습니다."),

    // chat
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."),
    NOT_ROOM_MEMBER(HttpStatus.FORBIDDEN, "이 채팅방의 참여자가 아닙니다."),
    CANNOT_DIRECT_SELF(HttpStatus.BAD_REQUEST, "자기 자신과는 1:1 채팅을 할 수 없습니다."),
    DIRECT_ROOM_INVITE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "1:1 채팅방에는 초대할 수 없습니다."),
    EMPTY_MESSAGE(HttpStatus.BAD_REQUEST, "메시지를 입력해 주세요."),
    TOO_MANY_FILES(HttpStatus.BAD_REQUEST, "파일은 한 번에 {{max}}개까지 보낼 수 있습니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "파일은 하나에 {{max}}MB, 한 번에 합계 {{total}}MB까지 보낼 수 있습니다."),
    EMPTY_FILE(HttpStatus.BAD_REQUEST, "빈 파일은 보낼 수 없습니다."),
    BLOCKED_FILE_TYPE(HttpStatus.BAD_REQUEST, "보안을 위해 .{{ext}} 파일은 보낼 수 없습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없거나 링크가 만료되었습니다. 채팅방을 새로고침해 주세요."),

    // meeting
    MEETING_NOT_FOUND(HttpStatus.NOT_FOUND, "회의를 찾을 수 없습니다."),
    NOT_MEETING_HOST(HttpStatus.FORBIDDEN, "회의 주최자나 팀 관리자만 할 수 있습니다."),
    MEETING_NOT_JOINABLE(HttpStatus.BAD_REQUEST, "종료되었거나 취소된 회의입니다."),
    MEETING_NOT_STARTED_YET(HttpStatus.BAD_REQUEST, "회의 시작 10분 전부터 입장할 수 있습니다."),
    MEETING_TIME_IN_PAST(HttpStatus.BAD_REQUEST, "지난 시간으로는 예약할 수 없습니다."),
}
