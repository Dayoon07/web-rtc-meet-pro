package com.webrtcmeetpro.application.admin

import com.webrtcmeetpro.domain.admin.ErrorCategory
import org.hibernate.HibernateException
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.InvalidDataAccessResourceUsageException
import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.transaction.TransactionTimedOutException
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

/** 예외는 여러 겹으로 감싸여 오므로 원인을 끝까지 따라가며 분류한다. Oracle 은 errorCode 가 ORA 번호다 */
object ErrorClassifier {

    /** 없는 테이블·컬럼, 타입 불일치, 길이·자릿수 초과, 없는 시퀀스 */
    private val SCHEMA_ORA = setOf(942, 904, 932, 12899, 1438, 2289, 4043, 1735, 909)

    /** 유니크, NOT NULL, CHECK, FK */
    private val CONSTRAINT_ORA = setOf(1, 1400, 1407, 2290, 2291, 2292)

    /** 리스너·서비스 이름, 계정, DB 꺼짐·시작 중, 연결 끊김, 시간 초과 */
    private val CONNECTION_ORA = setOf(
        12541, 12514, 12505, 12170, 12537, 12528, 12519, 12520, 1017, 28000, 28001,
        1033, 1034, 1089, 3113, 3114, 3135, 17002, 17008, 17410, 18,
        1013,
    )

    private val TIMEOUT_MESSAGES = listOf("Transaction timed out", "Transaction timeout expired")

    fun classify(e: Throwable): ErrorCategory {
        val chain = causes(e)
        if (chain.any {
                it is SQLTransientConnectionException || it is SQLNonTransientConnectionException || it is SQLRecoverableException ||
                    it is CannotGetJdbcConnectionException || it is CannotCreateTransactionException
            }
        ) return ErrorCategory.DB_CONNECTION

        // Hibernate 가 트랜잭션 시간 초과를 BadJpqlGrammar 로 감싸 올 때가 있어 DB 구조 문제로 잘못 분류되지 않게 메시지도 본다
        if (chain.any { it is TransactionTimedOutException || TIMEOUT_MESSAGES.any { m -> it.message?.contains(m, ignoreCase = true) == true } }) {
            return ErrorCategory.DB_CONNECTION
        }

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

    /** 사용자가 다운로드를 취소하거나 탭을 닫은 것이라 에러로 기록하지 않는다 */
    fun isClientAbort(e: Throwable): Boolean = causes(e).any {
        it is AsyncRequestNotUsableException || it.javaClass.name.endsWith("ClientAbortException") ||
            (it is java.io.IOException && it.message?.contains("Broken pipe", ignoreCase = true) == true)
    }

    /** 순환 참조가 있을 수 있어 최대 20개까지만 */
    private fun causes(e: Throwable): List<Throwable> =
        generateSequence(e) { cur -> cur.cause?.takeIf { it !== cur } }.take(20).toList()

    fun summary(e: Throwable): String {
        val root = causes(e).last()
        val outer = "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
        return if (root === e) outer else "$outer\n← ${root.javaClass.simpleName}: ${root.message.orEmpty()}"
    }
}
