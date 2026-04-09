package com.stockproject.market.adapter.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.stockproject.market.domain.DebateRequestedEvent
import com.stockproject.market.domain.DebateEventPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture

/**
 * Kafka Producer — debate.requested 토픽 발행
 * PC-12: Market svc → AI Debate svc 방향
 *
 * Spring Kafka 3.x: KafkaTemplate.send() → CompletableFuture 직접 반환
 * .completable() 메서드 없음 → thenApply{} 로 CompletableFuture<Void> 변환
 */
@Component
class DebateKafkaProducer(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    @Value("\${market.kafka.topic.debate-requested:debate.requested}") private val topic: String
) : DebateEventPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun publishDebateRequested(event: DebateRequestedEvent): Mono<Void> {
        val payload = objectMapper.writeValueAsString(event)
        val future: CompletableFuture<Void> = kafkaTemplate
            .send(topic, event.requestId, payload)
            .thenApply { null }                  // SendResult<K,V> → Void
        return Mono.fromFuture(future)
            .doOnSuccess {
                log.info("[Kafka SEND] topic={}, requestId={}, symbol={}", topic, event.requestId, event.symbol)
            }
            .doOnError { e ->
                log.error("[Kafka SEND ERR] topic={}, requestId={}", topic, event.requestId, e)
            }
            .then()
    }
}
