package com.mf.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpstreamNavDataDto(
        @JsonProperty("date")
        String date,

        @JsonProperty("nav")
        String nav
) {
}
