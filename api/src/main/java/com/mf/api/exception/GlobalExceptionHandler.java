package com.mf.api.exception;

import com.mf.api.dto.ErrorResponseDto;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UpstreamGatewayException.class)
    public ResponseEntity<ErrorResponseDto> handleUpstreamGateway(final UpstreamGatewayException ex) {
        final ErrorResponseDto error = new ErrorResponseDto(
                "UPSTREAM_GATEWAY_ERROR",
                ex.getMessage(),
                Instant.now().toString()
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
    }

    @ExceptionHandler(SchemeNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleSchemeNotFound(final SchemeNotFoundException ex) {
        final ErrorResponseDto error = new ErrorResponseDto(
                "SCHEME_NOT_FOUND",
                ex.getMessage(),
                Instant.now().toString()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidDateRange(final InvalidDateRangeException ex) {
        final ErrorResponseDto error = new ErrorResponseDto(
                "INVALID_DATE_RANGE",
                ex.getMessage(),
                Instant.now().toString()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDto> handleMethodArgumentTypeMismatch(final MethodArgumentTypeMismatchException ex) {
        final ErrorResponseDto error = new ErrorResponseDto(
                "INVALID_REQUEST",
                "Invalid parameter value: " + ex.getName(),
                Instant.now().toString()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgument(final IllegalArgumentException ex) {
        final ErrorResponseDto error = new ErrorResponseDto(
                "INVALID_REQUEST",
                ex.getMessage(),
                Instant.now().toString()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGenericException(final Exception ex) {
        LOGGER.error("Unhandled internal server error occurred", ex);
        final ErrorResponseDto error = new ErrorResponseDto(
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please try again later.",
                Instant.now().toString()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
