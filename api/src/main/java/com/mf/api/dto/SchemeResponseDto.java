package com.mf.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mf.api.model.Scheme;

public record SchemeResponseDto(
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

        @JsonProperty("navSynced")
        boolean navSynced
) {
    public static SchemeResponseDto fromEntity(final Scheme scheme) {
        return new SchemeResponseDto(
                scheme.getSchemeCode(),
                scheme.getSchemeName(),
                scheme.getFundHouse(),
                scheme.getSchemeType(),
                scheme.getSchemeCategory(),
                scheme.getIsinGrowth(),
                scheme.getIsinDivReinvestment(),
                scheme.isNavSynced()
        );
    }
}
