package com.yam.funds.domain.port.out;

import com.yam.funds.domain.model.event.SubscriptionOpenedEvent;
import reactor.core.publisher.Mono;

/** Publishes domain events for downstream consumers. */
public interface EventPublisherPort {

    /**
     * Records the event for publication.
     *
     * <p>Implemented with the transactional outbox pattern: called inside the same
     * transaction as the state change, it stores the event alongside it and a separate
     * relay forwards it to the broker. That is what keeps "money moved" and "event
     * published" from drifting apart — publishing straight to the broker here would
     * either emit events for rolled-back transactions or lose them when the broker is
     * unreachable.
     *
     * @param event the event to publish
     * @return completion once the event is durably recorded — not once it reaches the broker
     */
    Mono<Void> publish(SubscriptionOpenedEvent event);
}
