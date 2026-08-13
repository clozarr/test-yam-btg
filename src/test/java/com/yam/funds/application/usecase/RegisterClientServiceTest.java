package com.yam.funds.application.usecase;

import com.yam.funds.config.FundsProperties;
import com.yam.funds.config.FundsPropertiesFixture;
import com.yam.funds.domain.exception.ClientAlreadyExistsException;
import com.yam.funds.domain.model.Client;
import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.Money;
import com.yam.funds.domain.model.NotificationChannel;
import com.yam.funds.domain.port.in.command.RegisterClientCommand;
import com.yam.funds.domain.port.out.ClientRepositoryPort;
import com.yam.funds.domain.port.out.IdentifierGeneratorPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegisterClientServiceTest {

    @Mock
    private ClientRepositoryPort clientRepository;
    @Mock
    private IdentifierGeneratorPort identifiers;

    private RegisterClientService service;

    @BeforeEach
    void setUp() {
        final FundsProperties properties = FundsPropertiesFixture.defaults();
        service = new RegisterClientService(clientRepository, identifiers, properties);

        when(identifiers.nextClientId()).thenReturn(ClientId.of("client-1"));
        when(clientRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(clientRepository.existsByEmail(anyString())).thenReturn(Mono.just(false));
    }

    private static RegisterClientCommand aCommand() {
        return new RegisterClientCommand(
                "Ada Lovelace", "ada@example.com", "+573001112233", NotificationChannel.EMAIL);
    }

    @Test
    @DisplayName("registers the client with the opening balance from configuration")
    void appliesConfiguredOpeningBalance() {
        StepVerifier.create(service.registerClient(aCommand()))
                .assertNext(client -> {
                    assertThat(client.id()).isEqualTo(ClientId.of("client-1"));
                    assertThat(client.balance()).isEqualTo(Money.cop(500_000));
                    assertThat(client.activeSubscriptions()).isEmpty();
                    assertThat(client.notificationPreference()).isEqualTo(NotificationChannel.EMAIL);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("rejects an email address that is already registered")
    void rejectsDuplicateEmail() {
        when(clientRepository.existsByEmail("ada@example.com")).thenReturn(Mono.just(true));

        StepVerifier.create(service.registerClient(aCommand()))
                .expectError(ClientAlreadyExistsException.class)
                .verify();

        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("registers an SMS-only client without checking for a duplicate email")
    void skipsEmailCheckWhenAbsent() {
        final RegisterClientCommand smsOnly = new RegisterClientCommand(
                "Grace Hopper", null, "+573004445566", NotificationChannel.SMS);

        StepVerifier.create(service.registerClient(smsOnly))
                .assertNext(client -> assertThat(client.notificationPreference())
                        .isEqualTo(NotificationChannel.SMS))
                .verifyComplete();

        verify(clientRepository, never()).existsByEmail(any());
    }

    @Test
    @DisplayName("stores exactly the aggregate it returns")
    void storesTheRegisteredClient() {
        final Client registered = service.registerClient(aCommand()).block();

        assertThat(registered).isNotNull();
        verify(clientRepository).save(registered);
    }
}
