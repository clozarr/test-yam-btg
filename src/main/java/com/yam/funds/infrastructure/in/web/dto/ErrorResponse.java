package com.yam.funds.infrastructure.in.web.dto;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.net.URI;
import java.util.List;

/** Standard error body returned by every failing endpoint. */
@Data
@Builder
@ToString
public class ErrorResponse {

    private Integer code;

    private String reason;

    /** Stable, transport-independent discriminator, e.g. {@code INSUFFICIENT_BALANCE}. */
    private String errorCode;

    private List<String> messages;

    private String date;

    private URI path;

    /** Echoes the request's correlation id so a client can quote it in a support ticket. */
    private String correlationId;
}
