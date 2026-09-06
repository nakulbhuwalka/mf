package com.mf.api.exception;

public class SchemeNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SchemeNotFoundException(final String message) {
        super(message);
    }

    public SchemeNotFoundException(final int schemeCode) {
        super("Mutual fund scheme with code " + schemeCode + " was not found");
    }

    public SchemeNotFoundException(final int schemeCode, final Throwable cause) {
        super("Mutual fund scheme with code " + schemeCode + " was not found", cause);
    }

    public SchemeNotFoundException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
