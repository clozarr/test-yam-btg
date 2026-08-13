package com.yam.funds.application.usecase;

import com.yam.funds.domain.model.NotificationChannel;
import com.yam.funds.domain.model.event.SubscriptionOpenedEvent;
import com.yam.funds.domain.port.in.NotifySubscriptionUseCase;
import com.yam.funds.domain.port.out.NotificationSenderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class NotifySubscriptionService implements NotifySubscriptionUseCase {

    private final Map<NotificationChannel, NotificationSenderPort> sendersByChannel;

    /**
     * Indexes the available senders by channel, so adding a channel means adding an
     * implementation rather than extending a conditional here.
     */
    public NotifySubscriptionService(final List<NotificationSenderPort> senders) {
        this.sendersByChannel = new EnumMap<>(NotificationChannel.class);
        senders.forEach(sender -> sendersByChannel.put(sender.supportedChannel(), sender));
    }

    @Override
    public Mono<Void> notifySubscription(final SubscriptionOpenedEvent event) {
        return Mono.justOrEmpty(sendersByChannel.get(event.channel()))
                .switchIfEmpty(Mono.error(() -> new IllegalStateException(
                        "No notification sender is registered for channel %s".formatted(event.channel()))))
                .flatMap(sender -> sender.send(event))
                .doFirst(() -> log.info("[notifySubscription] [BEGIN] clientId={} channel={} fundName={}",
                        event.clientId(), event.channel(), event.fundName()))
                .doOnSuccess(ignored -> log.info("[notifySubscription] [END OK] clientId={} channel={}",
                        event.clientId(), event.channel()))
                .doOnError(error -> {
                    log.error("[notifySubscription] [END EX] clientId={} channel={}. Details: {}",
                            event.clientId(), event.channel(), error.getMessage());
                    log.warn(error.getLocalizedMessage(), error);
                })
                .then();
    }
}
