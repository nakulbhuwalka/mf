package com.mf.api;

import com.mf.api.client.MfApiClient;
import com.mf.api.dto.UpstreamNavDataDto;
import com.mf.api.dto.UpstreamNavHistoryDto;
import com.mf.api.dto.UpstreamSchemeItemDto;
import com.mf.api.exception.SchemeNotFoundException;
import com.mf.api.repository.NavRecordRepository;
import com.mf.api.repository.SchemeRepository;
import io.restassured.http.ContentType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NavFunctionalTest extends BaseApiTest {

    @MockitoBean
    private MfApiClient mfApiClient;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private NavRecordRepository navRecordRepository;

    @Autowired
    private com.mf.api.service.NavService navService;

    @BeforeEach
    void cleanData() {
        navService.clearCache();
        navRecordRepository.deleteAll();
        schemeRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /api/v1/schemes/{code}/nav should lazy-load on first call and hit cache on second call")
    void shouldLazyLoadAndHitCache() {
        final int schemeCode = 125497;
        final UpstreamSchemeItemDto meta = new UpstreamSchemeItemDto(
                schemeCode,
                "SBI SMALL CAP FUND - Direct Plan - Growth",
                "SBI Mutual Fund",
                "Open Ended Schemes",
                "Equity Scheme - Small Cap Fund",
                "INF200K01T51",
                null
        );
        final List<UpstreamNavDataDto> points = List.of(
                new UpstreamNavDataDto("04-09-2026", "216.6525"),
                new UpstreamNavDataDto("03-09-2026", "216.4037")
        );
        when(mfApiClient.fetchSchemeNav(schemeCode)).thenReturn(new UpstreamNavHistoryDto(meta, points, "SUCCESS"));

        // 1. First request -> Lazy load
        given()
                .accept(ContentType.JSON)
        .when()
                .get("/api/v1/schemes/" + schemeCode + "/nav")
        .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("meta.schemeCode", equalTo(schemeCode))
                .body("meta.schemeName", equalTo("SBI SMALL CAP FUND - Direct Plan - Growth"))
                .body("meta.totalPoints", equalTo(2))
                .body("meta.startDate", equalTo("2026-09-03"))
                .body("meta.endDate", equalTo("2026-09-04"))
                .body("data", hasSize(2))
                .body("data[0].date", equalTo("2026-09-04"))
                .body("data[0].nav", equalTo(216.6525f))
                .body("data[1].date", equalTo("2026-09-03"))
                .body("data[1].nav", equalTo(216.4037f));

        // 2. Second request -> Served from DB cache
        given()
                .accept(ContentType.JSON)
        .when()
                .get("/api/v1/schemes/" + schemeCode + "/nav")
        .then()
                .statusCode(200)
                .body("meta.totalPoints", equalTo(2));

        verify(mfApiClient, times(1)).fetchSchemeNav(schemeCode);
    }

    @Test
    @DisplayName("GET /api/v1/schemes/{code}/nav should filter by startDate and endDate")
    void shouldFilterByDateRange() {
        final int schemeCode = 125497;
        final UpstreamSchemeItemDto meta = new UpstreamSchemeItemDto(
                schemeCode, "SBI SMALL CAP FUND", "SBI", "Open", "Equity", "INF1", null
        );
        final List<UpstreamNavDataDto> points = List.of(
                new UpstreamNavDataDto("05-09-2026", "220.0"),
                new UpstreamNavDataDto("04-09-2026", "218.0"),
                new UpstreamNavDataDto("03-09-2026", "216.0"),
                new UpstreamNavDataDto("02-09-2026", "214.0")
        );
        when(mfApiClient.fetchSchemeNav(schemeCode)).thenReturn(new UpstreamNavHistoryDto(meta, points, "SUCCESS"));

        given()
                .accept(ContentType.JSON)
                .queryParam("startDate", "2026-09-03")
                .queryParam("endDate", "2026-09-04")
        .when()
                .get("/api/v1/schemes/" + schemeCode + "/nav")
        .then()
                .statusCode(200)
                .body("data", hasSize(2))
                .body("data[0].date", equalTo("2026-09-04"))
                .body("data[1].date", equalTo("2026-09-03"))
                .body("meta.totalPoints", equalTo(2));
    }

    @Test
    @DisplayName("GET /api/v1/schemes/{code}/nav should return 404 when scheme not found")
    void shouldReturn404WhenSchemeNotFound() {
        when(mfApiClient.fetchSchemeNav(999999)).thenThrow(new SchemeNotFoundException(999999));

        given()
                .accept(ContentType.JSON)
        .when()
                .get("/api/v1/schemes/999999/nav")
        .then()
                .statusCode(404)
                .body("code", equalTo("SCHEME_NOT_FOUND"))
                .body("message", equalTo("Mutual fund scheme with code 999999 was not found"))
                .body("timestamp", notNullValue());
    }

    @Test
    @DisplayName("GET /api/v1/schemes/{code}/nav should return 400 when startDate is after endDate")
    void shouldReturn400WhenInvalidDateRange() {
        given()
                .accept(ContentType.JSON)
                .queryParam("startDate", "2026-09-10")
                .queryParam("endDate", "2026-09-01")
        .when()
                .get("/api/v1/schemes/125497/nav")
        .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_DATE_RANGE"))
                .body("timestamp", notNullValue());
    }
}
