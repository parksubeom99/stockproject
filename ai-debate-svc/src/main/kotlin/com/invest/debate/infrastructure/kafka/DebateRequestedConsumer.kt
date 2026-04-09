package com.invest.debate.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.invest.debate.application.service.DebateOrchestrationService
import com.invest.debate.domain.port.StartDebateCommand
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class DebateRequestedConsumer(
    private val orchestrationService: DebateOrchestrationService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["debate.requested"],
        groupId = "debate-svc-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consume(record: ConsumerRecord<String, String>, ack: Acknowledgment) {
        val message = record.value()
        log.info("[Kafka] DEBATE_REQUESTED 수신 offset={}: {}", record.offset(), message.take(100))

        try {
            val event = objectMapper.readValue(message, DebateRequestedMessage::class.java)

            // ticker: market-svc는 symbol 필드로 발행 → symbol 우선, 없으면 ticker 사용
            val ticker = event.symbol ?: event.ticker ?: "UNKNOWN"

            // requestId를 UUID로 변환해서 debateId로 사용 → RN 폴링 정합성 보장
            val externalId = event.requestId?.let {
                runCatching { java.util.UUID.fromString(it) }.getOrNull()
            }

            val debateId = orchestrationService.startDebate(
                StartDebateCommand(
                    userId = event.userId,
                    ticker = ticker,
                    thesis = event.thesis,
                    currentPrice = event.price
                ),
                externalId = externalId
            ).block()

            if (debateId != null) {
                log.info("[Kafka] 세션 생성 완료 debateId={}", debateId)
                ack.acknowledge()
                Thread {
                    try {
                        orchestrationService.orchestrate(debateId).block()
                        log.info("[Kafka] 오케스트레이션 완료 debateId={}", debateId)
                    } catch (e: Exception) {
                        log.error("[Kafka] 오케스트레이션 오류 debateId={}: {}", debateId, e.message)
                    }
                }.start()
            } else {
                log.error("[Kafka] 세션 생성 실패")
            }

        } catch (e: Exception) {
            log.error("[Kafka] 파싱 실패: {} | 메시지: {}", e.message, message)
            ack.acknowledge()
        }
    }
}

data class DebateRequestedMessage(
    val requestId: String? = null,   // market-svc가 발행하는 ID
    val userId: String,
    val symbol: String? = null,      // market-svc 필드명
    val ticker: String? = null,      // debate-svc 직접 호출 시 필드명
    val thesis: String,
    val price: Double? = null
)
