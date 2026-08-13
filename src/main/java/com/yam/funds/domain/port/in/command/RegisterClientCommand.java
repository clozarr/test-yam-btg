package com.yam.funds.domain.port.in.command;

import com.yam.funds.domain.model.NotificationChannel;

import java.util.Objects;

/**
 * Request to register a new client.
 *
 * <p>The opening balance is not part of the command: it is fixed by the business rules
 * and applied by the use case, so a caller cannot choose how much money to start with.
 *
 * @param fullName               client's full name
 * @param email                  address used when the preferred channel is EMAIL
 * @param phone                  number used when the preferred channel is SMS
 * @param notificationPreference channel the client wants subscription notices on
 */
public record RegisterClientCommand(
        String fullName, String email, String phone, NotificationChannel notificationPreference) {

    public RegisterClientCommand {
        Objects.requireNonNull(notificationPreference, "notificationPreference must not be null");
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("fullName must not be blank");
        }
    }
}
