package com.yam.funds.infrastructure.in.web.dto;

import com.yam.funds.domain.model.NotificationChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Details needed to register a client")
public record RegisterClientRequest(

        @Schema(example = "Ada Lovelace")
        @NotBlank(message = "fullName is required")
        @Size(max = 120, message = "fullName must not exceed 120 characters")
        String fullName,

        @Schema(example = "ada@example.com", description = "Required when the preference is EMAIL")
        @Email(message = "email must be a valid address")
        @Size(max = 180, message = "email must not exceed 180 characters")
        String email,

        @Schema(example = "+573001112233", description = "Required when the preference is SMS")
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "phone must be 7 to 15 digits, optionally prefixed with +")
        String phone,

        @Schema(example = "EMAIL")
        @NotNull(message = "notificationPreference is required")
        NotificationChannel notificationPreference) {

    /**
     * The chosen channel must have a usable destination. Expressed here rather than as a
     * bean-validation annotation because it spans two fields.
     */
    public boolean hasContactForPreference() {
        return switch (notificationPreference) {
            case EMAIL -> email != null && !email.isBlank();
            case SMS -> phone != null && !phone.isBlank();
        };
    }
}
