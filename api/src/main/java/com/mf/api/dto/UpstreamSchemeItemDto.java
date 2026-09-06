package com.mf.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpstreamSchemeItemDto(
        @JsonProperty("schemeCode")
        @JsonAlias({"scheme_code", "code"})
        Integer schemeCode,

        @JsonProperty("schemeName")
        @JsonAlias({"scheme_name", "name"})
        String schemeName,

        @JsonProperty("fundHouse")
        @JsonAlias({"fund_house", "fund_family"})
        String fundHouse,

        @JsonProperty("schemeType")
        @JsonAlias("scheme_type")
        String schemeType,

        @JsonProperty("schemeCategory")
        @JsonAlias("scheme_category")
        String schemeCategory,

        @JsonProperty("isinGrowth")
        @JsonAlias("isin_growth")
        String isinGrowth,

        @JsonProperty("isinDivReinvestment")
        @JsonAlias("isin_div_reinvestment")
        String isinDivReinvestment
) {
}
