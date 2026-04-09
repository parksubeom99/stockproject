package com.stockproject.market.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// RedisConfig 제거 — Spring Boot AutoConfiguration이 reactiveStringRedisTemplate 자동 등록
// 커스텀 Bean 등록 시 동일 타입 2개 충돌 발생 → AutoConfig 그대로 사용

@Configuration
class JacksonConfig {

    @Bean
    fun objectMapper(): ObjectMapper = ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}
