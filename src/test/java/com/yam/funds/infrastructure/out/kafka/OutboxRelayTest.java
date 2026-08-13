package com.yam.funds.infrastructure.out.kafka;

import com.mongodb.client.result.UpdateResult;
import com.yam.funds.config.FundsPropertiesFixture;
import com.yam.funds.infrastructure.out.persistence.mongo.document.OutboxEventDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:15:30Z");

    @Mock
    private ReactiveMongoTemplate mongoTemplate;
    @Mock
    private SubscriptionEventProducer producer;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(
                mongoTemplate, producer, FundsPropertiesFixture.defaults(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(OutboxEventDocument.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));
    }

    private static OutboxEventDocument pendingEvent(final String id, final String clientId) {
        return OutboxEventDocument.builder()
                .id(id)
                .aggregateType("Client")
                .aggregateId(clientId)
                .eventType("SubscriptionOpened")
                .partitionKey(clientId)
                .payload("{\"eventId\":\"%s\"}".formatted(id))
                .status(OutboxEventDocument.STATUS_PENDING)
                .createdAt(NOW)
                .attempts(0)
                .build();
    }

    private void givenPending(final OutboxEventDocument... events) {
        when(mongoTemplate.find(any(Query.class), eq(OutboxEventDocument.class)))
                .thenReturn(Flux.just(events));
        when(mongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(OutboxEventDocument.class)))
                .thenAnswer(invocation -> Mono.just(events[0]));
    }

    @Test
    @DisplayName("publishes a claimed event with the client id as the partition key")
    void publishesWithClientPartitionKey() {
        final OutboxEventDocument event = pendingEvent("event-1", "client-1");
        givenPending(event);
        when(producer.publish(anyString(), anyString())).thenReturn(Mono.empty());

        relay.forwardPendingEvents();

        verify(producer).publish("client-1", event.getPayload());
    }

    @Test
    @DisplayName("marks the event published once the broker has accepted it")
    void marksPublished() {
        givenPending(pendingEvent("event-1", "client-1"));
        when(producer.publish(anyString(), anyString())).thenReturn(Mono.empty());

        relay.forwardPendingEvents();

        final ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(any(Query.class), updates.capture(), eq(OutboxEventDocument.class));
        assertThat(updates.getValue().toString()).contains(OutboxEventDocument.STATUS_PUBLISHED);
    }

    @Test
    @DisplayName("releases the claim when the broker rejects the send, so the next cycle retries")
    void releasesOnFailure() {
        givenPending(pendingEvent("event-1", "client-1"));
        when(producer.publish(anyString(), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("broker unreachable")));

        relay.forwardPendingEvents();

        final ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(any(Query.class), updates.capture(), eq(OutboxEventDocument.class));
        assertThat(updates.getValue().toString())
                .contains(OutboxEventDocument.STATUS_PENDING)
                .doesNotContain(OutboxEventDocument.STATUS_PUBLISHED);
    }

    @Test
    @DisplayName("claims each event before sending it, so two instances do not both forward it")
    void claimsBeforeSending() {
        givenPending(pendingEvent("event-1", "client-1"));
        when(producer.publish(anyString(), anyString())).thenReturn(Mono.empty());

        relay.forwardPendingEvents();

        verify(mongoTemplate).findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(OutboxEventDocument.class));
    }

    @Test
    @DisplayName("skips an event another instance claimed first")
    void skipsEventClaimedElsewhere() {
        when(mongoTemplate.find(any(Query.class), eq(OutboxEventDocument.class)))
                .thenReturn(Flux.fromIterable(List.of(pendingEvent("event-1", "client-1"))));
        // An empty result means the conditional claim matched nothing: someone else won.
        when(mongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(OutboxEventDocument.class)))
                .thenReturn(Mono.empty());

        relay.forwardPendingEvents();

        verify(producer, never()).publish(anyString(), anyString());
    }

    @Test
    @DisplayName("does nothing when the outbox is empty")
    void doesNothingWhenEmpty() {
        when(mongoTemplate.find(any(Query.class), eq(OutboxEventDocument.class)))
                .thenReturn(Flux.empty());

        relay.forwardPendingEvents();

        verify(producer, never()).publish(anyString(), anyString());
        verify(mongoTemplate, times(0))
                .updateFirst(any(Query.class), any(Update.class), eq(OutboxEventDocument.class));
    }
}
