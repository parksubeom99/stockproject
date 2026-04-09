package com.stockproject.market

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.stockproject.market.adapter.kafka.DebateKafkaProducer
import com.stockproject.market.domain.DebateRequestedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import reactor.test.StepVerifier
import java.util.concurrent.CompletableFuture

@DisplayName("DebateKafkaProducer 단위 테스트")
class DebateKafkaProducerTest {

    private val kafkaTemplate = mockk<KafkaTemplate<String, String>>()
    private val objectMapper = ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private val sut = DebateKafkaProducer(kafkaTemplate, objectMapper, "debate.requested")

    @Test
    @DisplayName("publishDebateRequested - 정상 발행 시 Mono 완료")
    fun `publishDebateRequested - 정상 발행 시 완료`() {
        val event = DebateRequestedEvent("req-1", "AAPL", "상승 예상", "user-1")
        val sendResult = mockk<SendResult<String, String>>()
        val future = CompletableFuture.completedFuture(sendResult)

        every { kafkaTemplate.send("debate.requested", "req-1", any()) } returns future

        StepVerifier.create(sut.publishDebateRequested(event))
            .verifyComplete()

        verify(exactly = 1) { kafkaTemplate.send("debate.requested", "req-1", any()) }
    }

    @Test
    @DisplayName("publishDebateRequested - Kafka 오류 시 에러 전파")
    fun `publishDebateRequested - Kafka 오류 시 에러 전파`() {
        val event = DebateRequestedEvent("req-2", "TSLA", "하락 우려", "user-2")

        val future = CompletableFuture<SendResult<String, String>>()
        future.completeExceptionally(RuntimeException("Kafka broker unavailable"))
        every { kafkaTemplate.send("debate.requested", "req-2", any()) } returns future

        StepVerifier.create(sut.publishDebateRequested(event))
            .expectError(RuntimeException::class.java)
            .verify()
    }
}
