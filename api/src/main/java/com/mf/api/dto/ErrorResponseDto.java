package com.mf.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ErrorResponseDto(
        @JsonProperty("code")
        String code,

        @JsonProperty("message")
        String message,

        @JsonProperty("timestamp")
        String timestamp
) {
}
