package com.mf.api;

import com.mf.api.client.MfApiClient;
import com.mf.api.dto.UpstreamSchemeItemDto;
import com.mf.api.exception.UpstreamGatewayException;
import io.restassured.http.ContentType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class AdminSyncFunctionalTest extends BaseApiTest {

    @MockitoBean
    private MfApiClient mfApiClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM nav_records");
        jdbcTemplate.execute("DELETE FROM schemes");
    }

    @Test
    @DisplayName("POST /api/v1/admin/schemes/sync should return 200 and sync summary")
    void shouldSyncSchemesSuccessfully() {
        final List<UpstreamSchemeItemDto> sampleSchemes = List.of(
                new UpstreamSchemeItemDto(
                        125497,
                        "SBI SMALL CAP FUND - Direct Plan - Growth",
                        "SBI Mutual Fund",
                        "Open Ended Schemes",
                        "Equity Scheme - Small Cap Fund",
                        "INF200K01T51",
                        null
                ),
                new UpstreamSchemeItemDto(
                        100027,
                        "Grindlays Super Saver Income Fund",
                        "Standard Chartered Mutual Fund",
                        "Open Ended Schemes",
                        "Income",
                        null,
                        "INF700K01123"
                )
        );

        when(mfApiClient.fetchLatestSchemes()).thenReturn(sampleSchemes);

        given()
                .contentType(ContentType.JSON)
        .when()
                .post("/api/v1/admin/schemes/sync")
        .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", equalTo("COMPLETED"))
                .body("totalFetched", equalTo(2))
                .body("inserted", equalTo(2))
                .body("updated", equalTo(0))
                .body("durationMs", greaterThanOrEqualTo(0))
                .body("syncedAt", notNullValue());

        final Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schemes", Integer.class);
        assertEquals(2, count);

        final String fundHouse = jdbcTemplate.queryForObject(
                "SELECT fund_house FROM schemes WHERE scheme_code = 125497",
                String.class
        );
        assertEquals("SBI Mutual Fund", fundHouse);
    }

    @Test
    @DisplayName("POST /api/v1/admin/schemes/sync should return 502 when upstream fails")
    void shouldReturn502OnUpstreamFailure() {
        when(mfApiClient.fetchLatestSchemes())
                .thenThrow(new UpstreamGatewayException("Failed to download master scheme directory from mfapi.in"));

        given()
                .contentType(ContentType.JSON)
        .when()
                .post("/api/v1/admin/schemes/sync")
        .then()
                .statusCode(502)
                .contentType(ContentType.JSON)
                .body("code", equalTo("UPSTREAM_GATEWAY_ERROR"))
                .body("message", equalTo("Failed to download master scheme directory from mfapi.in"))
                .body("timestamp", notNullValue());
    }
}
