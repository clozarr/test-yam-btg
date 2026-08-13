package com.yam.funds.domain.port.in;

import com.yam.funds.domain.model.event.SubscriptionOpenedEvent;
import reactor.core.publisher.Mono;

/** Delivers the notice a client receives after subscribing to a fund. */
public interface NotifySubscriptionUseCase {

    /**
     * Sends the subscription notice over the channel the client chose.
     *
     * <p>Driven by the messaging adapter rather than by the subscription flow: a
     * notification provider being down must never stop a client from subscribing, so
     * delivery happens after the money movement has already been committed.
     *
     * @param event the subscription that occurred, carrying the client's contact details
     * @return completion once the notice has been handed to the provider
     */
    Mono<Void> notifySubscription(SubscriptionOpenedEvent event);
}
