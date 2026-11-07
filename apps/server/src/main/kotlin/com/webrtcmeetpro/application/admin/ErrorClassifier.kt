package com.webrtcmeetpro.application.admin

import com.webrtcmeetpro.domain.admin.ErrorCategory
import org.hibernate.HibernateException
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.InvalidDataAccessResourceUsageException
import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.web.client.RestClientException
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.http.HttpTimeoutException
import java.sql.SQLException
import java.sql.SQLNonTransientConnectionException
import java.sql.SQLRecoverableException
import java.sql.SQLTransientConnectionException

/**
 * 예외를 원인 분류([ErrorCategory])로 나눈다. 예외는 여러 겹으로 감싸여 오므로 원인(cause)을 끝까지 따라가며 본다.
 * Oracle 은 SQLException.errorCode 가 ORA-번호다 (예: ORA-00942 → 942).
 */
object ErrorClassifier {

    /** 테이블·컬럼 구조 문제: 없는 테이블/컬럼, 타입 불일치, 값이 컬럼보다 큼, 숫자 자릿수 초과, 없는 시퀀스·객체 */
    private val SCHEMA_ORA = setOf(942, 904, 932, 12899, 1438, 2289, 4043, 1735, 909)

    /** 규칙 위반: 유니크, NULL 불가, 체크 제약, 부모 키 없음, 자식 행 있음 */
    private val CONSTRAINT_ORA = setOf(1, 1400, 1407, 2290, 2291, 2292)

    /** 연결 문제: 리스너 없음, 서비스 이름 오류, 계정·비밀번호, DB 꺼짐/시작 중, 연결 끊김, 시간 초과 */
    private val CONNECTION_ORA = setOf(
        12541, 12514, 12505, 12170, 12537, 12528, 12519, 12520, 1017, 28000, 28001,
        1033, 1034, 1089, 3113, 3114, 3135, 17002, 17008, 17410, 18,
    )

    fun classify(e: Throwable): ErrorCategory {
        val chain = causes(e)
        if (chain.any {
                it is SQLTransientConnectionException || it is SQLNonTransientConnectionException || it is SQLRecoverableException ||
                    it is CannotGetJdbcConnectionException || it is CannotCreateTransactionException
            }
        ) return ErrorCategory.DB_CONNECTION

        chain.filterIsInstance<SQLException>().firstOrNull { it.errorCode != 0 || it.sqlState != null }?.let { sql ->
            return when {
                sql.errorCode in CONNECTION_ORA || sql.sqlState?.startsWith("08") == true -> ErrorCategory.DB_CONNECTION
                sql.errorCode in SCHEMA_ORA -> ErrorCategory.DB_SCHEMA
                sql.errorCode in CONSTRAINT_ORA || sql.sqlState?.startsWith("23") == true -> ErrorCategory.DB_CONSTRAINT
                else -> ErrorCategory.DB_ETC
            }
        }

        return when {
            chain.any { it is DataAccessResourceFailureException } -> ErrorCategory.DB_CONNECTION
            chain.any { it is DataIntegrityViolationException } -> ErrorCategory.DB_CONSTRAINT
            chain.any { it is InvalidDataAccessResourceUsageException } -> ErrorCategory.DB_SCHEMA
            chain.any { it is DataAccessException || it is HibernateException || it is jakarta.persistence.PersistenceException } -> ErrorCategory.DB_ETC
            chain.any {
                it is RestClientException || it is ConnectException || it is UnknownHostException ||
                    it is SocketTimeoutException || it is HttpTimeoutException
            } -> ErrorCategory.EXTERNAL
            else -> ErrorCategory.CODE
        }
    }

    /** 사용자가 내려받기를 취소하거나 탭을 닫아 응답을 못 보낸 경우. 에러로 기록하지 않는다 */
    fun isClientAbort(e: Throwable): Boolean = causes(e).any {
        it is AsyncRequestNotUsableException || it.javaClass.name.endsWith("ClientAbortException") ||
            (it is java.io.IOException && it.message?.contains("Broken pipe", ignoreCase = true) == true)
    }

    /** 가장 바깥 예외부터 원인까지 (순환 참조 방지로 최대 20개) */
    private fun causes(e: Throwable): List<Throwable> =
        generateSequence(e) { cur -> cur.cause?.takeIf { it !== cur } }.take(20).toList()

    /** 관리자 화면용 한 줄 요약: 바깥 예외 메시지 + 가장 안쪽 원인 */
    fun summary(e: Throwable): String {
        val root = causes(e).last()
        val outer = "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
        return if (root === e) outer else "$outer\n← ${root.javaClass.simpleName}: ${root.message.orEmpty()}"
    }
}
