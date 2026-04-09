package com.invest.debate.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.invest.debate.domain.event.*
import com.invest.debate.domain.port.EventPublisherPort
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class KafkaEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) : EventPublisherPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun publish(event: DebateDomainEvent): Mono<Void> {
        return Mono.fromCallable {
            val (topic, key, payload) = when (event) {
                is DebateCompletedEvent -> Triple(
                    "debate.completed",
                    event.debateId.toString(),
                    objectMapper.writeValueAsString(
                        mapOf(
                            "debateId" to event.debateId,
                            "userId" to event.userId,
                            "successProbability" to event.successProbability,
                            "occurredAt" to event.occurredAt
                        )
                    )
                )
                is DebateFailedEvent -> Triple(
                    "debate.failed",
                    event.debateId.toString(),
                    objectMapper.writeValueAsString(
                        mapOf(
                            "debateId" to event.debateId,
                            "reason" to event.reason,
                            "occurredAt" to event.occurredAt
                        )
                    )
                )
                is PhaseCompletedEvent -> Triple(
                    "debate.phase.completed",
                    event.debateId.toString(),
                    objectMapper.writeValueAsString(event)
                )
                else -> return@fromCallable Unit
            }

            kafkaTemplate.send(topic, key, payload)
                .whenComplete { result, ex ->
                    if (ex != null) {
                        log.error("[Kafka] 발행 실패 topic={}: {}", topic, ex.message)
                    } else {
                        log.info("[Kafka] 발행 완료 topic={} offset={}", topic,
                            result.recordMetadata.offset())
                    }
                }
        }.then()
    }
}
