package com.mf.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PageMetadataDto(
        @JsonProperty("number")
        int number,

        @JsonProperty("size")
        int size,

        @JsonProperty("totalElements")
        long totalElements,

        @JsonProperty("totalPages")
        int totalPages
) {
}
