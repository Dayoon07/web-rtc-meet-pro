package com.webrtcmeetpro.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync

/** @Async (에러 기록 등) — 스프링 부트 기본 applicationTaskExecutor 를 쓴다 */
@Configuration
@EnableAsync
class AsyncConfig
