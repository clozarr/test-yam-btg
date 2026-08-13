package com.yam.funds.infrastructure.out.kafka;

import com.yam.funds.config.FundsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

/**
 * Sends recorded events to the broker.
 *
 * <p>Driven by the outbox relay, never by a use case: a use case that published here
 * directly could announce a subscription whose transaction later rolled back.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionEventProducer {

    private final KafkaSender<String, String> kafkaSender;
    private final FundsProperties properties;

    /**
     * @param partitionKey client id; events for one client must share a partition, since
     *                     Kafka only orders records within a partition
     * @param payload      serialised event
     */
    public Mono<Void> publish(final String partitionKey, final String payload) {
        final String topic = properties.kafka().topics().subscriptionNotifications();
        final SenderRecord<String, String, String> record = SenderRecord.create(
                new ProducerRecord<>(topic, partitionKey, payload), partitionKey);

        return kafkaSender.send(Mono.just(record))
                .single()
                .flatMap(result -> result.exception() == null
                        ? Mono.just(result)
                        : Mono.error(result.exception()))
                .doOnNext(result -> log.debug(
                        "[publish] [END OK] topic={} partition={} offset={} key={}",
                        topic,
                        result.recordMetadata().partition(),
                        result.recordMetadata().offset(),
                        partitionKey))
                .doOnError(error -> log.error("[publish] [END EX] topic={} key={}. Details: {}",
                        topic, partitionKey, error.getMessage()))
                .then();
    }
}
