package com.mf.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SchemePageResponseDto(
        @JsonProperty("content")
        List<SchemeResponseDto> content,

        @JsonProperty("page")
        PageMetadataDto page
) {
}
