package com.mf.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpstreamNavHistoryDto(
        @JsonProperty("meta")
        UpstreamSchemeItemDto meta,

        @JsonProperty("data")
        List<UpstreamNavDataDto> data,

        @JsonProperty("status")
        String status
) {
}
