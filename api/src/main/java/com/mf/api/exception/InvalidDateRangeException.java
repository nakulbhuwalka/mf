package com.mf.api.exception;

public class InvalidDateRangeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidDateRangeException(final String message) {
        super(message);
    }

    public InvalidDateRangeException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
