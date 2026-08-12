package com.yam.funds.domain.model;

import java.util.UUID;

/** Identity of the {@link Client} aggregate. */
public record ClientId(String value) {

    public ClientId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
    }

    public static ClientId generate() {
        return new ClientId(UUID.randomUUID().toString());
    }

    public static ClientId of(final String value) {
        return new ClientId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
