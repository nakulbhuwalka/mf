package com.mf.api.exception;

public class UpstreamGatewayException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UpstreamGatewayException(final String message) {
        super(message);
    }

    public UpstreamGatewayException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
