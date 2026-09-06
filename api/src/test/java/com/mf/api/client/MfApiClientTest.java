package com.mf.api.client;

import com.mf.api.dto.UpstreamNavHistoryDto;
import com.mf.api.dto.UpstreamSchemeItemDto;
import com.mf.api.exception.SchemeNotFoundException;
import com.mf.api.exception.UpstreamGatewayException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MfApiClientTest {

    private MockRestServiceServer mockServer;
    private MfApiClient mfApiClient;

    @BeforeEach
    void setUp() {
        final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mfapi.in");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        mfApiClient = new MfApiClient(builder.build());
    }

    @Test
    @DisplayName("Should successfully fetch schemes from /mf/latest")
    void shouldFetchSchemesSuccessfully() {
        final String jsonResponse = """
                [
                    {
                        "schemeCode": 125497,
                        "schemeName": "SBI SMALL CAP FUND - Direct Plan - Growth",
                        "fundHouse": "SBI Mutual Fund",
                        "schemeType": "Open Ended Schemes",
                        "schemeCategory": "Equity Scheme - Small Cap Fund",
                        "isinGrowth": "INF200K01T51",
                        "isinDivReinvestment": null
                    },
                    {
                        "schemeCode": 100027,
                        "schemeName": "Grindlays Super Saver Income Fund",
                        "fundHouse": "Standard Chartered Mutual Fund",
                        "schemeType": "Open Ended Schemes",
                        "schemeCategory": "Income",
                        "isinGrowth": null,
                        "isinDivReinvestment": "INF700K01123"
                    }
                ]
                """;

        mockServer.expect(requestTo("https://api.mfapi.in/mf/latest"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        final List<UpstreamSchemeItemDto> schemes = mfApiClient.fetchLatestSchemes();

        assertNotNull(schemes);
        assertEquals(2, schemes.size());
        assertEquals(125497, schemes.get(0).schemeCode());
        assertEquals("SBI SMALL CAP FUND - Direct Plan - Growth", schemes.get(0).schemeName());
        assertEquals(100027, schemes.get(1).schemeCode());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should fallback to /mf if /mf/latest returns 404")
    void shouldFallbackToMfOn404() {
        final String jsonResponse = """
                [
                    {
                        "schemeCode": 125497,
                        "schemeName": "SBI SMALL CAP FUND"
                    }
                ]
                """;

        mockServer.expect(requestTo("https://api.mfapi.in/mf/latest"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        mockServer.expect(requestTo("https://api.mfapi.in/mf"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        final List<UpstreamSchemeItemDto> schemes = mfApiClient.fetchLatestSchemes();

        assertNotNull(schemes);
        assertEquals(1, schemes.size());
        assertEquals(125497, schemes.get(0).schemeCode());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw UpstreamGatewayException on HTTP 500 error")
    void shouldThrowExceptionOnServerError() {
        mockServer.expect(requestTo("https://api.mfapi.in/mf/latest"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        final UpstreamGatewayException ex = assertThrows(
                UpstreamGatewayException.class,
                () -> mfApiClient.fetchLatestSchemes()
        );

        assertTrue(ex.getMessage().contains("Failed to download master scheme directory"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should return empty list on empty upstream array")
    void shouldReturnEmptyListOnEmptyResponse() {
        mockServer.expect(requestTo("https://api.mfapi.in/mf/latest"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        final List<UpstreamSchemeItemDto> schemes = mfApiClient.fetchLatestSchemes();

        assertNotNull(schemes);
        assertTrue(schemes.isEmpty());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should successfully fetch scheme NAV history from /mf/{code}")
    void shouldFetchSchemeNavSuccessfully() {
        final String jsonResponse = """
                {
                    "meta": {
                        "fund_house": "HDFC Mutual Fund",
                        "scheme_type": "Open Ended Schemes",
                        "scheme_category": "Equity Scheme - Large Cap Fund",
                        "scheme_code": 125497,
                        "scheme_name": "HDFC Top 100 Fund - Growth",
                        "isin_growth": "INF179K01BB2",
                        "isin_div_reinvestment": null
                    },
                    "data": [
                        {
                            "date": "06-09-2026",
                            "nav": "216.65"
                        }
                    ],
                    "status": "SUCCESS"
                }
                """;

        mockServer.expect(requestTo("https://api.mfapi.in/mf/125497"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        final com.mf.api.dto.UpstreamNavHistoryDto result = mfApiClient.fetchSchemeNav(125497);

        assertNotNull(result);
        assertNotNull(result.meta());
        assertEquals(125497, result.meta().schemeCode());
        assertEquals("HDFC Top 100 Fund - Growth", result.meta().schemeName());
        assertEquals(1, result.data().size());
        assertEquals("06-09-2026", result.data().get(0).date());
        assertEquals("216.65", result.data().get(0).nav());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw SchemeNotFoundException on 404 when fetching scheme NAV")
    void shouldThrowSchemeNotFoundOn404() {
        mockServer.expect(requestTo("https://api.mfapi.in/mf/999999"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(com.mf.api.exception.SchemeNotFoundException.class, () -> mfApiClient.fetchSchemeNav(999999));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw UpstreamGatewayException on 500 when fetching scheme NAV")
    void shouldThrowGatewayErrorOn500ForNav() {
        mockServer.expect(requestTo("https://api.mfapi.in/mf/125497"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(UpstreamGatewayException.class, () -> mfApiClient.fetchSchemeNav(125497));
        mockServer.verify();
    }
}
