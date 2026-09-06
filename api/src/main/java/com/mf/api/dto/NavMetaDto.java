package com.mf.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NavMetaDto(
        @JsonProperty("schemeCode")
        Integer schemeCode,

        @JsonProperty("schemeName")
        String schemeName,

        @JsonProperty("fundHouse")
        String fundHouse,

        @JsonProperty("schemeType")
        String schemeType,

        @JsonProperty("schemeCategory")
        String schemeCategory,

        @JsonProperty("isinGrowth")
        String isinGrowth,

        @JsonProperty("isinDivReinvestment")
        String isinDivReinvestment,

        @JsonProperty("totalPoints")
        int totalPoints,

        @JsonProperty("startDate")
        String startDate,

        @JsonProperty("endDate")
        String endDate
) {
}
