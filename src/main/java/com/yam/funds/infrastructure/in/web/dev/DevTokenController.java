package com.yam.funds.infrastructure.in.web.dev;

import com.yam.funds.config.FundsProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Mints bearer tokens for local development.
 *
 * <p>Restricted to the {@code local} profile, so it cannot exist in a deployed
 * environment: an unauthenticated endpoint that issues tokens for any subject would be
 * a complete authentication bypass. It stands in for the identity provider a real
 * deployment would point the resource server at.
 */
@Slf4j
@Tag(name = "Development", description = "Local-only helpers, absent from every deployed profile")
@Profile("local")
@RestController
@RequiredArgsConstructor
public class DevTokenController {

    private final JwtEncoder jwtEncoder;
    private final FundsProperties properties;
    private final Clock clock;

    @Operation(summary = "Mint a development token",
            description = "Local profile only. Use the returned value as `Authorization: Bearer <token>`.")
    @PostMapping(value = "/dev/token", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<DevToken> mintToken(
            @RequestParam final String clientId,
            @RequestParam(defaultValue = "CLIENT") final List<String> roles) {

        final Instant issuedAt = clock.instant();
        final Instant expiresAt = issuedAt.plus(properties.security().jwt().ttl());

        final JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.security().jwt().issuer())
                // The subject is what @PreAuthorize compares against the path's clientId.
                .subject(clientId)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", roles)
                .build();

        final JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return Mono.fromCallable(() -> jwtEncoder.encode(JwtEncoderParameters.from(header, claims)))
                .map(jwt -> new DevToken(jwt.getTokenValue(), "Bearer", expiresAt, clientId, roles))
                .doFirst(() -> log.warn("[mintToken] issuing a development token for {} - "
                        + "this endpoint must never be reachable outside local", clientId));
    }

    /**
     * @param accessToken the bearer token
     * @param tokenType   always {@code Bearer}
     * @param expiresAt   when the token stops being accepted
     * @param clientId    subject the token authenticates as
     * @param roles       granted roles
     */
    public record DevToken(
            String accessToken, String tokenType, Instant expiresAt, String clientId, List<String> roles) {
    }
}
