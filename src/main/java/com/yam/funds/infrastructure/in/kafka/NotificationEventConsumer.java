package com.yam.funds.infrastructure.in.kafka;

import com.yam.funds.domain.port.in.NotifySubscriptionUseCase;
import com.yam.funds.infrastructure.out.persistence.mongo.outbox.SubscriptionOpenedPayload;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * Delivers subscription notices from the broker.
 *
 * <p>Offsets are acknowledged only after the notice has been handled, so a crash
 * mid-processing replays the message rather than skipping it. That makes delivery
 * at-least-once, which pairs with the outbox relay's own at-least-once guarantee: a
 * client may in principle be notified twice, and that is the deliberate trade against
 * never being notified at all.
 *
 * <p>A message that cannot be parsed is acknowledged and dropped instead of retried
 * forever — a payload that is not valid JSON will not become valid on the next attempt,
 * and blocking the partition on it would stop every later notification.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final KafkaReceiver<String, String> kafkaReceiver;
    private final NotifySubscriptionUseCase notifySubscriptionUseCase;
    private final ObjectMapper objectMapper;

    private Disposable subscription;

    @PostConstruct
    public void start() {
        subscription = kafkaReceiver.receive()
                .concatMap(this::handle)
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(signal -> log.warn(
                                "[start] consumer stream failed, reconnecting. Details: {}",
                                signal.failure().getMessage())))
                .subscribe();
        log.info("[start] [END OK] subscription notification consumer is running");
    }

    @PreDestroy
    public void stop() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            log.info("[stop] [END OK] subscription notification consumer stopped");
        }
    }

    private Mono<Void> handle(final ReceiverRecord<String, String> record) {
        return Mono.fromCallable(() -> objectMapper.readValue(
                        record.value(), SubscriptionOpenedPayload.class))
                .flatMap(payload -> notifySubscriptionUseCase.notifySubscription(payload.toEvent()))
                .doOnSuccess(ignored -> log.info(
                        "[handle] [END OK] partition={} offset={} key={}",
                        record.partition(), record.offset(), record.key()))
                .onErrorResume(error -> {
                    log.error("[handle] [END EX] partition={} offset={} key={}. Details: {}",
                            record.partition(), record.offset(), record.key(), error.getMessage());
                    log.warn(error.getLocalizedMessage(), error);
                    return Mono.empty();
                })
                // Acknowledged in both paths: a poisoned message must not stall the
                // partition behind it.
                .doFinally(signal -> record.receiverOffset().acknowledge());
    }
}
