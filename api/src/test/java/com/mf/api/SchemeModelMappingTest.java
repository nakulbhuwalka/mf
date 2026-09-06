package com.mf.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mf.api.dto.UpstreamSchemeItemDto;
import com.mf.api.model.Scheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemeModelMappingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should deserialize upstream JSON with camelCase fields")
    void shouldDeserializeCamelCaseJson() throws Exception {
        final String json = """
                {
                    "schemeCode": 125497,
                    "schemeName": "SBI SMALL CAP FUND - Direct Plan - Growth",
                    "fundHouse": "SBI Mutual Fund",
                    "schemeType": "Open Ended Schemes",
                    "schemeCategory": "Equity Scheme - Small Cap Fund",
                    "isinGrowth": "INF200K01T51",
                    "isinDivReinvestment": null
                }
                """;

        final UpstreamSchemeItemDto dto = objectMapper.readValue(json, UpstreamSchemeItemDto.class);

        assertNotNull(dto);
        assertEquals(125497, dto.schemeCode());
        assertEquals("SBI SMALL CAP FUND - Direct Plan - Growth", dto.schemeName());
        assertEquals("SBI Mutual Fund", dto.fundHouse());
        assertEquals("Open Ended Schemes", dto.schemeType());
        assertEquals("Equity Scheme - Small Cap Fund", dto.schemeCategory());
        assertEquals("INF200K01T51", dto.isinGrowth());
        assertNull(dto.isinDivReinvestment());
    }

    @Test
    @DisplayName("Should deserialize upstream JSON with snake_case fields")
    void shouldDeserializeSnakeCaseJson() throws Exception {
        final String json = """
                {
                    "scheme_code": 100027,
                    "scheme_name": "Grindlays Super Saver Income Fund-GSSIF-Half Yearly Dividend",
                    "fund_house": "Standard Chartered Mutual Fund",
                    "scheme_type": "Open Ended Schemes",
                    "scheme_category": "Income",
                    "isin_growth": null,
                    "isin_div_reinvestment": "INF700K01123"
                }
                """;

        final UpstreamSchemeItemDto dto = objectMapper.readValue(json, UpstreamSchemeItemDto.class);

        assertNotNull(dto);
        assertEquals(100027, dto.schemeCode());
        assertEquals("Grindlays Super Saver Income Fund-GSSIF-Half Yearly Dividend", dto.schemeName());
        assertEquals("Standard Chartered Mutual Fund", dto.fundHouse());
        assertEquals("Open Ended Schemes", dto.schemeType());
        assertEquals("Income", dto.schemeCategory());
        assertNull(dto.isinGrowth());
        assertEquals("INF700K01123", dto.isinDivReinvestment());
    }

    @Test
    @DisplayName("Should map entity correctly and verify equality and getters/setters")
    void shouldMapEntityCorrectly() {
        final Scheme scheme1 = new Scheme(
                125497,
                "SBI SMALL CAP FUND - Direct Plan - Growth",
                "SBI Mutual Fund",
                "Open Ended Schemes",
                "Equity Scheme - Small Cap Fund",
                "INF200K01T51",
                null,
                false
        );

        final Scheme scheme2 = new Scheme();
        scheme2.setSchemeCode(125497);
        scheme2.setSchemeName("SBI SMALL CAP FUND - Direct Plan - Growth");
        scheme2.setFundHouse("SBI Mutual Fund");
        scheme2.setSchemeType("Open Ended Schemes");
        scheme2.setSchemeCategory("Equity Scheme - Small Cap Fund");
        scheme2.setIsinGrowth("INF200K01T51");
        scheme2.setIsinDivReinvestment(null);
        scheme2.setNavSynced(false);

        assertEquals(scheme1, scheme2);
        assertEquals(scheme1.hashCode(), scheme2.hashCode());
        assertEquals(125497, scheme1.getSchemeCode());
        assertEquals("SBI SMALL CAP FUND - Direct Plan - Growth", scheme1.getSchemeName());
        assertEquals("SBI Mutual Fund", scheme1.getFundHouse());
        assertEquals("Open Ended Schemes", scheme1.getSchemeType());
        assertEquals("Equity Scheme - Small Cap Fund", scheme1.getSchemeCategory());
        assertEquals("INF200K01T51", scheme1.getIsinGrowth());
        assertNull(scheme1.getIsinDivReinvestment());
        assertFalse(scheme1.isNavSynced());

        scheme1.setNavSynced(true);
        assertTrue(scheme1.isNavSynced());

        final Scheme schemeDifferent = new Scheme(999, "Other", null, null, null, null, null, false);
        assertNotEquals(scheme1, schemeDifferent);
        assertNotEquals(scheme1, null);
        assertNotEquals(scheme1, new Object());
    }
}
