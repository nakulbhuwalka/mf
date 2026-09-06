package com.mf.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NavPointDto(
        @JsonProperty("date")
        String date,

        @JsonProperty("nav")
        Float nav
) {
}
