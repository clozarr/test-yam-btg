package com.yam.funds.infrastructure.in.web;

import com.yam.funds.domain.exception.AlreadySubscribedException;
import com.yam.funds.domain.exception.ClientAlreadyExistsException;
import com.yam.funds.domain.exception.ClientNotFoundException;
import com.yam.funds.domain.exception.ConcurrentAggregateUpdateException;
import com.yam.funds.domain.exception.DomainException;
import com.yam.funds.domain.exception.FundNotAvailableException;
import com.yam.funds.domain.exception.FundNotFoundException;
import com.yam.funds.domain.exception.IdempotencyConflictException;
import com.yam.funds.domain.exception.InsufficientBalanceException;
import com.yam.funds.domain.exception.MinimumAmountNotMetException;
import com.yam.funds.domain.exception.OperationInProgressException;
import com.yam.funds.domain.exception.SubscriptionNotFoundException;
import com.yam.funds.infrastructure.in.web.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Turns every failure into the same error body.
 *
 * <p>Business failures are mapped by their domain error code rather than by catching
 * each exception type separately, so adding a rule means adding one entry here instead
 * of another handler method.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * How each business rule violation surfaces over HTTP.
     *
     * <p>404 for something that does not exist, 409 for a state conflict the caller can
     * resolve by looking at the current state, 422 for a well-formed request the rules
     * refuse. Insufficient balance is 422 rather than 400: the request is perfectly
     * valid, the account simply cannot cover it.
     */
    private static final Map<String, HttpStatus> STATUS_BY_ERROR_CODE = Map.ofEntries(
            Map.entry(ClientNotFoundException.ERROR_CODE, HttpStatus.NOT_FOUND),
            Map.entry(FundNotFoundException.ERROR_CODE, HttpStatus.NOT_FOUND),
            Map.entry(SubscriptionNotFoundException.ERROR_CODE, HttpStatus.NOT_FOUND),
            Map.entry(AlreadySubscribedException.ERROR_CODE, HttpStatus.CONFLICT),
            Map.entry(ClientAlreadyExistsException.ERROR_CODE, HttpStatus.CONFLICT),
            Map.entry(FundNotAvailableException.ERROR_CODE, HttpStatus.CONFLICT),
            Map.entry(OperationInProgressException.ERROR_CODE, HttpStatus.CONFLICT),
            Map.entry(ConcurrentAggregateUpdateException.ERROR_CODE, HttpStatus.CONFLICT),
            Map.entry(InsufficientBalanceException.ERROR_CODE, HttpStatus.UNPROCESSABLE_CONTENT),
            Map.entry(MinimumAmountNotMetException.ERROR_CODE, HttpStatus.UNPROCESSABLE_CONTENT),
            Map.entry(IdempotencyConflictException.ERROR_CODE, HttpStatus.UNPROCESSABLE_CONTENT));

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(
            final DomainException ex, final ServerWebExchange exchange) {

        final HttpStatus status = STATUS_BY_ERROR_CODE
                .getOrDefault(ex.errorCode(), HttpStatus.UNPROCESSABLE_CONTENT);

        log.warn("[handleDomainException] {} on {}. Details: {}",
                ex.errorCode(), exchange.getRequest().getPath().value(), ex.getMessage());

        final ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (ex instanceof OperationInProgressException) {
            // The caller's own earlier request is still running; tell them when to retry
            // rather than leaving them to guess.
            response.header("Retry-After", "1");
        }
        return response.body(body(status, ex.errorCode(), List.of(ex.getMessage()), exchange));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            final WebExchangeBindException ex, final ServerWebExchange exchange) {

        final List<String> messages = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> "%s: %s".formatted(error.getField(), error.getDefaultMessage()))
                .toList();

        return badRequest("VALIDATION_FAILED", messages, exchange);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            final ConstraintViolationException ex, final ServerWebExchange exchange) {

        final List<String> messages = ex.getConstraintViolations().stream()
                .map(violation -> "%s: %s".formatted(violation.getPropertyPath(), violation.getMessage()))
                .toList();

        return badRequest("VALIDATION_FAILED", messages, exchange);
    }

    @ExceptionHandler(TransactionCursorCodec.InvalidCursorException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCursor(
            final TransactionCursorCodec.InvalidCursorException ex, final ServerWebExchange exchange) {
        return badRequest("INVALID_CURSOR", List.of(ex.getMessage()), exchange);
    }

    @ExceptionHandler({ServerWebInputException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadInput(
            final Exception ex, final ServerWebExchange exchange) {
        return badRequest("BAD_REQUEST", List.of(ex.getMessage()), exchange);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            final AccessDeniedException ex, final ServerWebExchange exchange) {

        log.warn("[handleAccessDenied] denied access to {}", exchange.getRequest().getPath().value());

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(body(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                        List.of("You are not allowed to access this resource"), exchange));
    }

    /**
     * Last resort. The cause is logged in full but never returned: an unexpected failure
     * can carry connection strings or internal identifiers, and the client can quote the
     * correlation id instead.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            final Exception ex, final ServerWebExchange exchange) {

        log.error("[handleUnexpected] [END EX] unhandled failure on {}. Details: {}",
                exchange.getRequest().getPath().value(), ex.getMessage());
        log.warn(ex.getLocalizedMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                        List.of("An unexpected error occurred"), exchange));
    }

    private ResponseEntity<ErrorResponse> badRequest(
            final String errorCode, final List<String> messages, final ServerWebExchange exchange) {
        return ResponseEntity.badRequest()
                .body(body(HttpStatus.BAD_REQUEST, errorCode, messages, exchange));
    }

    private ErrorResponse body(
            final HttpStatus status,
            final String errorCode,
            final List<String> messages,
            final ServerWebExchange exchange) {

        return ErrorResponse.builder()
                .code(status.value())
                .reason(status.getReasonPhrase())
                .errorCode(errorCode)
                .messages(messages)
                .date(LocalDateTime.now().format(FORMATTER))
                .path(URI.create(exchange.getRequest().getPath().value()))
                .correlationId(CorrelationIdWebFilter.currentCorrelationId(exchange))
                .build();
    }
}
