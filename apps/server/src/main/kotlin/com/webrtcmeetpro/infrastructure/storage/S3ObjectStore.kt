package com.webrtcmeetpro.infrastructure.storage

import com.webrtcmeetpro.config.properties.StorageProperties
import com.webrtcmeetpro.domain.common.ServeAs
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ContentDisposition
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * AWS S3 (app.storage.type=s3). 버킷은 공개하지 않고, 내려받을 때마다 몇 시간짜리 서명된 주소를 만든다.
 * 같은 파일의 주소가 매번 바뀌면 브라우저 캐시가 안 먹으므로, 만든 주소를 [REUSE] 동안 다시 쓴다.
 * 나중에 CloudFront 를 붙이면 [presignGet] 만 바꾸면 된다.
 */
@Component
@ConditionalOnProperty(prefix = "app.storage", name = ["type"], havingValue = "s3")
class S3ObjectStore(props: StorageProperties) : DisposableBean {

    private val log = LoggerFactory.getLogger(javaClass)
    private val s3 = props.s3
    private val bucket = s3.bucket.also { require(it.isNotBlank()) { "app.storage.type=s3 이면 S3_BUCKET 이 필요합니다." } }
    private val prefix = s3.prefix.trim('/').let { if (it.isEmpty()) "" else "$it/" }
    private val credentials: AwsCredentialsProvider =
        if (s3.accessKey.isNotBlank()) StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.accessKey, s3.secretKey))
        else DefaultCredentialsProvider.builder().build()
    private val region = Region.of(s3.region)
    private val client: S3Client = S3Client.builder().region(region).credentialsProvider(credentials).build()
    private val presigner: S3Presigner = S3Presigner.builder().region(region).credentialsProvider(credentials).build()
    private val urls = ConcurrentHashMap<String, Pair<String, Instant>>()

    init {
        runCatching { client.headBucket(HeadBucketRequest.builder().bucket(bucket).build()) }
            .onSuccess { log.info("S3 저장소 사용: s3://{}/{}", bucket, prefix) }
            .onFailure { log.error("S3 버킷({})에 접근하지 못했습니다. 버킷 이름·권한·리전을 확인하세요.", bucket, it) }
    }

    fun put(key: String, content: InputStream, size: Long, contentType: String? = null) {
        client.putObject(request(key, contentType), RequestBody.fromInputStream(content, size))
    }

    fun putFile(key: String, file: Path, contentType: String? = null) {
        client.putObject(request(key, contentType), RequestBody.fromFile(file))
    }

    fun delete(key: String) {
        runCatching { client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(prefix + key).build()) }
            .onFailure { log.warn("S3 파일 삭제 실패: {}", key, it) }
        urls.keys.removeIf { it.startsWith("$key|") }
    }

    /** 서명된 내려받기 주소 (약 12시간). serveAs 가 있으면 파일 이름·형식·저장/바로보기를 S3 응답 헤더로 정한다 */
    fun presignGet(key: String, serveAs: ServeAs? = null): String {
        val cacheKey = "$key|${serveAs?.fileName}|${serveAs?.contentType}|${serveAs?.attachment}"
        val now = Instant.now()
        urls[cacheKey]?.takeIf { it.second.isAfter(now) }?.let { return it.first }
        val get = GetObjectRequest.builder().bucket(bucket).key(prefix + key).apply {
            if (serveAs != null) {
                val disposition = if (serveAs.attachment) ContentDisposition.attachment() else ContentDisposition.inline()
                responseContentDisposition(disposition.filename(serveAs.fileName, StandardCharsets.UTF_8).build().toString())
                responseContentType(serveAs.contentType)
            }
        }.build()
        val url = presigner.presignGetObject(
            GetObjectPresignRequest.builder().signatureDuration(VALID).getObjectRequest(get).build(),
        ).url().toString()
        if (urls.size > MAX_CACHED) urls.entries.removeIf { it.value.second.isBefore(now) }
        urls[cacheKey] = url to now.plus(REUSE)
        return url
    }

    private fun request(key: String, contentType: String?) =
        PutObjectRequest.builder().bucket(bucket).key(prefix + key).apply { contentType?.let(::contentType) }.build()

    override fun destroy() {
        presigner.close()
        client.close()
    }

    companion object {
        private val VALID: Duration = Duration.ofHours(12)
        /** 만든 주소를 다시 쓰는 시간. 남은 유효 시간이 최소 6시간 이상이 되게 */
        private val REUSE: Duration = Duration.ofHours(6)
        private const val MAX_CACHED = 20_000
    }
}
