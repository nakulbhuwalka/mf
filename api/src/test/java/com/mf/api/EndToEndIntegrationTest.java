package com.mf.api;

import com.mf.api.client.MfApiClient;
import com.mf.api.dto.UpstreamNavDataDto;
import com.mf.api.dto.UpstreamNavHistoryDto;
import com.mf.api.dto.UpstreamSchemeItemDto;
import com.mf.api.exception.SchemeNotFoundException;
import com.mf.api.repository.NavRecordRepository;
import com.mf.api.repository.SchemeRepository;
import com.mf.api.service.NavService;
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

class EndToEndIntegrationTest extends BaseApiTest {

    @MockitoBean
    private MfApiClient mfApiClient;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private NavRecordRepository navRecordRepository;

    @Autowired
    private NavService navService;

    @BeforeEach
    void cleanState() {
        navService.clearCache();
        navRecordRepository.deleteAll();
        schemeRepository.deleteAll();
    }

    @Test
    @DisplayName("Complete E2E: Admin Sync -> Catalog Search -> NAV Lazy Load -> Cached Query -> Filtered Query")
    void testCompleteEndToEndFlow() {
        // Step 1: Initial empty catalog
        given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/v1/schemes")
                .then()
                .statusCode(200)
                .body("content", hasSize(0))
                .body("page.totalElements", equalTo(0));

        // Step 2: Ingest schemes catalog via Admin API
        final List<UpstreamSchemeItemDto> catalogSchemes = List.of(
                new UpstreamSchemeItemDto(100001, "Nippon India Large Cap Fund", "Nippon India Mutual Fund",
                        "Open Ended Schemes", "Equity Scheme - Large Cap Fund", "INF204K01017", null),
                new UpstreamSchemeItemDto(100002, "HDFC Mid-Cap Opportunities Fund", "HDFC Mutual Fund",
                        "Open Ended Schemes", "Equity Scheme - Mid Cap Fund", "INF179K01970", null),
                new UpstreamSchemeItemDto(100003, "SBI Small Cap Fund", "SBI Mutual Fund",
                        "Open Ended Schemes", "Equity Scheme - Small Cap Fund", "INF200K01T51", null)
        );
        when(mfApiClient.fetchLatestSchemes()).thenReturn(catalogSchemes);

        given()
                .accept(ContentType.JSON)
                .when()
                .post("/api/v1/admin/schemes/sync")
                .then()
                .statusCode(200)
                .body("status", equalTo("COMPLETED"))
                .body("totalFetched", equalTo(3))
                .body("inserted", equalTo(3));

        // Step 3: Search & filter in catalog
        given()
                .accept(ContentType.JSON)
                .queryParam("search", "Small")
                .when()
                .get("/api/v1/schemes")
                .then()
                .statusCode(200)
                .body("content", hasSize(1))
                .body("content[0].schemeCode", equalTo(100003))
                .body("content[0].schemeName", equalTo("SBI Small Cap Fund"))
                .body("page.totalElements", equalTo(1));

        given()
                .accept(ContentType.JSON)
                .queryParam("fundHouse", "HDFC")
                .when()
                .get("/api/v1/schemes")
                .then()
                .statusCode(200)
                .body("content", hasSize(1))
                .body("content[0].schemeCode", equalTo(100002));

        // Step 4: Lazy load NAV for scheme 100003
        final UpstreamSchemeItemDto schemeMeta = catalogSchemes.get(2);
        final List<UpstreamNavDataDto> navPoints = List.of(
                new UpstreamNavDataDto("05-09-2026", "220.50"),
                new UpstreamNavDataDto("04-09-2026", "219.00"),
                new UpstreamNavDataDto("03-09-2026", "217.25"),
                new UpstreamNavDataDto("02-09-2026", "215.80"),
                new UpstreamNavDataDto("01-09-2026", "214.10")
        );
        when(mfApiClient.fetchSchemeNav(100003)).thenReturn(new UpstreamNavHistoryDto(schemeMeta, navPoints, "SUCCESS"));

        given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/v1/schemes/100003/nav")
                .then()
                .statusCode(200)
                .body("meta.schemeCode", equalTo(100003))
                .body("meta.schemeName", equalTo("SBI Small Cap Fund"))
                .body("meta.totalPoints", equalTo(5))
                .body("meta.startDate", equalTo("2026-09-01"))
                .body("meta.endDate", equalTo("2026-09-05"))
                .body("data", hasSize(5))
                .body("data[0].date", equalTo("2026-09-05"))
                .body("data[0].nav", equalTo(220.50f));

        verify(mfApiClient, times(1)).fetchSchemeNav(100003);

        // Step 5: Cached request returns same data without upstream call
        given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/v1/schemes/100003/nav")
                .then()
                .statusCode(200)
                .body("data", hasSize(5));

        verify(mfApiClient, times(1)).fetchSchemeNav(100003);

        // Step 6: Filtered query by date range and ascending sort
        given()
                .accept(ContentType.JSON)
                .queryParam("startDate", "2026-09-02")
                .queryParam("endDate", "2026-09-04")
                .queryParam("sort", "asc")
                .when()
                .get("/api/v1/schemes/100003/nav")
                .then()
                .statusCode(200)
                .body("meta.totalPoints", equalTo(3))
                .body("meta.startDate", equalTo("2026-09-02"))
                .body("meta.endDate", equalTo("2026-09-04"))
                .body("data", hasSize(3))
                .body("data[0].date", equalTo("2026-09-02"))
                .body("data[2].date", equalTo("2026-09-04"));

        // Step 7: Error handling
        when(mfApiClient.fetchSchemeNav(999999)).thenThrow(new SchemeNotFoundException(999999));
        given()
                .accept(ContentType.JSON)
                .when()
                .get("/api/v1/schemes/999999/nav")
                .then()
                .statusCode(404)
                .body("code", equalTo("SCHEME_NOT_FOUND"))
                .body("message", notNullValue());

        given()
                .accept(ContentType.JSON)
                .queryParam("startDate", "2026-09-05")
                .queryParam("endDate", "2026-09-01")
                .when()
                .get("/api/v1/schemes/100003/nav")
                .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_DATE_RANGE"));
    }
}
