package com.mf.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record NavHistoryResponseDto(
        @JsonProperty("meta")
        NavMetaDto meta,

        @JsonProperty("data")
        List<NavPointDto> data
) {
}
