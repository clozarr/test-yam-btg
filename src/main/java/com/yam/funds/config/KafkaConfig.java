package com.yam.funds.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reactive Kafka wiring, built on reactor-kafka directly.
 *
 * <p>Spring Kafka's reactive templates were removed in its 4.x line, and its Boot
 * auto-configuration lives in a module this project does not depend on. Constructing
 * {@link KafkaSender} and {@link KafkaReceiver} here keeps the dependency list unchanged
 * and makes every broker setting that matters on a money flow explicit rather than
 * inherited from a default.
 */
@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

    private final FundsProperties properties;

    /**
     * Idempotent and fully acknowledged: on a money flow, neither a silently dropped
     * write nor a duplicate produced by an internal retry is acceptable.
     */
    @Bean
    public KafkaSender<String, String> kafkaSender() {
        final Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.kafka().bootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        return KafkaSender.create(SenderOptions.create(config));
    }

    /**
     * Auto-commit is off: offsets must advance only after a message has been handled, or
     * a crash between poll and processing would move past a notification never delivered.
     */
    @Bean
    public KafkaReceiver<String, String> kafkaReceiver() {
        final Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.kafka().bootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, properties.kafka().consumerGroupId());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        final ReceiverOptions<String, String> receiverOptions = ReceiverOptions
                .<String, String>create(config)
                .subscription(List.of(properties.kafka().topics().subscriptionNotifications()));

        return KafkaReceiver.create(receiverOptions);
    }
}
