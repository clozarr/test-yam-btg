package com.yam.funds.application.usecase;

import com.yam.funds.config.FundsProperties;
import com.yam.funds.domain.exception.ClientAlreadyExistsException;
import com.yam.funds.domain.model.Client;
import com.yam.funds.domain.model.Money;
import com.yam.funds.domain.port.in.RegisterClientUseCase;
import com.yam.funds.domain.port.in.command.RegisterClientCommand;
import com.yam.funds.domain.port.out.ClientRepositoryPort;
import com.yam.funds.domain.port.out.IdentifierGeneratorPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Currency;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterClientService implements RegisterClientUseCase {

    private final ClientRepositoryPort clientRepository;
    private final IdentifierGeneratorPort identifiers;
    private final FundsProperties properties;

    @Override
    public Mono<Client> registerClient(final RegisterClientCommand command) {
        return rejectDuplicateEmail(command)
                .then(Mono.fromSupplier(() -> newClient(command)))
                .flatMap(clientRepository::save)
                .doFirst(() -> log.info("[registerClient] [BEGIN] fullName={} preference={}",
                        command.fullName(), command.notificationPreference()))
                .doOnSuccess(client -> log.info("[registerClient] [END OK] clientId={} balance={}",
                        client.id(), client.balance()))
                .doOnError(error -> {
                    log.error("[registerClient] [END EX] fullName={}. Details: {}",
                            command.fullName(), error.getMessage());
                    log.warn(error.getLocalizedMessage(), error);
                });
    }

    /**
     * The opening balance comes from configuration, never from the caller: letting a
     * request choose its own starting balance would be a way to mint money.
     */
    private Client newClient(final RegisterClientCommand command) {
        final Money openingBalance = Money.of(
                properties.client().initialBalance(), Currency.getInstance(properties.currency()));
        return Client.register(
                identifiers.nextClientId(),
                command.fullName(),
                command.email(),
                command.phone(),
                command.notificationPreference(),
                openingBalance);
    }

    private Mono<Void> rejectDuplicateEmail(final RegisterClientCommand command) {
        if (command.email() == null || command.email().isBlank()) {
            return Mono.empty();
        }
        return clientRepository.existsByEmail(command.email())
                .filter(Boolean::booleanValue)
                .flatMap(taken -> Mono.error(new ClientAlreadyExistsException(command.email())))
                .then();
    }
}
