package com.yam.funds.domain.port.out;

import com.yam.funds.domain.model.NotificationChannel;
import com.yam.funds.domain.model.event.SubscriptionOpenedEvent;
import reactor.core.publisher.Mono;

/**
 * Delivers a notice to a client over one channel.
 *
 * <p>One implementation per {@link NotificationChannel}; the use case picks by
 * {@link #supportedChannel()}, so supporting a new channel means adding an
 * implementation rather than editing a conditional.
 */
public interface NotificationSenderPort {

    /**
     * @return the channel this implementation delivers on
     */
    NotificationChannel supportedChannel();

    /**
     * Sends the subscription notice.
     *
     * @param event the subscription that occurred, carrying the client's contact details
     * @return completion once handed to the provider
     */
    Mono<Void> send(SubscriptionOpenedEvent event);
}
