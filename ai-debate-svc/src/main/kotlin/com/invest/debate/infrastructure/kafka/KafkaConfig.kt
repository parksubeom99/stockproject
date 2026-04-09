package com.invest.debate.infrastructure.kafka

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.ContainerProperties

@Configuration
class KafkaConfig(
    @Value("\${spring.kafka.bootstrap-servers}") private val bootstrapServers: String,
    @Value("\${spring.kafka.consumer.group-id}") private val groupId: String
) {

    // ─── Consumer ───────────────────────────────────────

    @Bean
    fun consumerFactory(): ConsumerFactory<String, String> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            // debateId 기반 순서 보장 (Gate 2 Kafka partition 대응)
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 1
        )
        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String> {
        return ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            consumerFactory = consumerFactory()
            // 수동 커밋 — 오케스트레이션 완료 후 커밋
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
            setConcurrency(3)
        }
    }

    // ─── Producer ───────────────────────────────────────

    @Bean
    fun producerFactory(): ProducerFactory<String, String> {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.RETRIES_CONFIG to 3,
            // debateId를 파티션 키로 사용 → 동일 debate 순서 보장
            ProducerConfig.PARTITIONER_CLASS_CONFIG to
                "org.apache.kafka.clients.producer.internals.DefaultPartitioner"
        )
        return DefaultKafkaProducerFactory(props)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, String> =
        KafkaTemplate(producerFactory())
}
