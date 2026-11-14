plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.3.21"
}

group = "com.webrtcmeetpro"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	// 조건이 3개 이상인 조회·관리자 화면 SQL 은 MyBatis mapper (resources/mapper/**), 나머지는 JPA
	implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:4.1.0")
	implementation("org.springframework.boot:spring-boot-starter-security")
	// JWT 발급/검증 (HS256, Nimbus)
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
	// 구글 로그인 (Authorization Code, 서버 리다이렉트 방식)
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	// 메일 발송 (비밀번호 재설정·팀 초대). MAIL_HOST 가 없으면 보내지 않고 서버 로그에만 남긴다
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	// 파일 저장소 S3 (app.storage.type=s3 일 때만 쓴다. 기본은 서버 디스크)
	implementation(platform("software.amazon.awssdk:bom:2.56.0"))
	implementation("software.amazon.awssdk:s3")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	// AI 회의록 요약·실시간 자막 번역 (Claude API). ANTHROPIC_API_KEY 가 없으면 AI 기능을 끈다
	implementation("com.anthropic:anthropic-java:3.5.0")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	runtimeOnly("com.oracle.database.jdbc:ojdbc17")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-websocket-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
		// MyBatis 가 @Param 없이 #{파라미터 이름} 을 쓸 수 있게 (도메인 인터페이스에 MyBatis 의존을 두지 않으려고)
		javaParameters = true
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
