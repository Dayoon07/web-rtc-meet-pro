package com.webrtcmeetpro.domain.admin

import com.webrtcmeetpro.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import org.hibernate.type.YesNoConverter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/** 어디서 난 에러인지 */
enum class ErrorSource { SERVER, WEB, APP }

/** 원인 분류. 관리자 화면에서 "테이블 구조 문제인지, 소스 코드 문제인지" 를 바로 알아보려고 */
enum class ErrorCategory {
    /** 테이블·컬럼 구조와 코드가 맞지 않음 (없는 테이블·컬럼, 길이 초과, 타입 불일치) */
    DB_SCHEMA,
    /** 중복·필수값·참조·체크 규칙 위반 */
    DB_CONSTRAINT,
    /** DB 에 연결하지 못함 (DB 꺼짐, 계정·접속 정보 오류, 커넥션 부족) */
    DB_CONNECTION,
    /** 그 밖의 DB 오류 */
    DB_ETC,
    /** 외부 서비스(토스페이먼츠·구글·미디어 서버 등) 연동 실패 */
    EXTERNAL,
    /** 서버 소스 코드 버그 */
    CODE,
    /** 웹·앱 화면 코드 오류 */
    CLIENT,
    /** 웹·앱이 서버에 연결하지 못함 */
    NETWORK,
}

/**
 * 관리자 전용 에러 기록 (예상하지 못한 에러 1건 = 1행).
 * 사용자 화면에는 errorCode 와 대략적인 원인(category)만 보여 주고, 예외 내용·스택은 여기에만 남긴다.
 * userId 는 에러를 만난 사용자 (로그인 전이면 null). 기록이 실패하지 않도록 FK 를 걸지 않는다.
 */
@Entity
@Table(name = "admin_error_logs")
class AdminErrorLog(
    @Column(nullable = false, unique = true)
    val errorCode: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val source: ErrorSource,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val category: ErrorCategory,

    exceptionClass: String?,
    message: String?,
    stackTrace: String?,
    httpMethod: String?,
    requestPath: String?,
    val userId: Long?,
    ipAddress: String?,
    userAgent: String?,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "error_log_id")
    var id: Long? = null
        protected set

    // 컬럼 길이에 맞춰 자른다 (기록하다가 또 에러가 나지 않게)
    val exceptionClass: String? = exceptionClass?.take(300)
    val message: String? = message?.take(1000)

    @Lob
    @Column(name = "stack_trace")
    val stackTrace: String? = stackTrace?.take(MAX_STACK_LENGTH)

    val httpMethod: String? = httpMethod?.take(10)
    val requestPath: String? = requestPath?.take(1000)
    val ipAddress: String? = ipAddress?.take(45)
    val userAgent: String? = userAgent?.take(500)

    /** 관리자가 확인·해결 표시 */
    @Convert(converter = YesNoConverter::class)
    @Column(name = "resolved_yn", nullable = false)
    var resolved: Boolean = false
        protected set

    fun resolve() {
        resolved = true
    }

    companion object {
        const val MAX_STACK_LENGTH = 32_000

        /** 에러 코드 형식: ERR-YYYYMMDD-(UUID 앞 20자리, 대문자) */
        val ERROR_CODE = Regex("^ERR-\\d{8}-[0-9A-F]{20}$")

        /** 예: ERR-20261107-3F2A9C4B1D7E4F60A8B2. 사용자가 문의할 때 알려 주는 값 */
        fun newErrorCode(today: LocalDate = LocalDate.now()): String =
            "ERR-${today.format(DateTimeFormatter.BASIC_ISO_DATE)}-${UUID.randomUUID().toString().replace("-", "").take(20).uppercase()}"
    }
}

interface AdminErrorLogRepository {
    fun save(log: AdminErrorLog): AdminErrorLog
}
