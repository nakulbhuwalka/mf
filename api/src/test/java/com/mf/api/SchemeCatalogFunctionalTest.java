package com.mf.api;

import com.mf.api.model.Scheme;
import com.mf.api.repository.SchemeRepository;
import io.restassured.http.ContentType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

class SchemeCatalogFunctionalTest extends BaseApiTest {

    @Autowired
    private SchemeRepository schemeRepository;

    @BeforeEach
    void setUpData() {
        schemeRepository.deleteAll();

        final List<Scheme> testSchemes = List.of(
                new Scheme(1001, "HDFC Top 100 Fund - Growth", "HDFC Mutual Fund", "Open Ended Schemes", "Equity Scheme - Large Cap", "INF179K01BB2", null, true),
                new Scheme(1002, "HDFC Mid-Cap Opportunities Fund", "HDFC Mutual Fund", "Open Ended Schemes", "Equity Scheme - Mid Cap", "INF179K01BC0", null, false),
                new Scheme(1003, "SBI Small Cap Fund - Growth", "SBI Mutual Fund", "Open Ended Schemes", "Equity Scheme - Small Cap", "INF200K01T51", null, true),
                new Scheme(1004, "SBI Magnum Gilt Fund", "SBI Mutual Fund", "Open Ended Schemes", "Debt Scheme - Gilt Fund", "INF200K01T69", null, false),
                new Scheme(1005, "ICICI Prudential Bluechip Fund", "ICICI Prudential Mutual Fund", "Open Ended Schemes", "Equity Scheme - Large Cap", "INF109K011B1", null, false)
        );

        schemeRepository.saveAll(testSchemes);
    }

    @Test
    @DisplayName("GET /api/v1/schemes should return paginated list with default size 50")
    void shouldReturnDefaultPaginatedList() {
        given()
                .contentType(ContentType.JSON)
        .when()
                .get("/api/v1/schemes")
        .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("content", hasSize(5))
                .body("page.number", equalTo(0))
                .body("page.size", equalTo(50))
                .body("page.totalElements", equalTo(5))
                .body("page.totalPages", equalTo(1))
                .body("content[0].schemeCode", equalTo(1001))
                .body("content[0].schemeName", equalTo("HDFC Top 100 Fund - Growth"))
                .body("content[0].navSynced", equalTo(true));
    }

    @Test
    @DisplayName("GET /api/v1/schemes with search param should filter by scheme name")
    void shouldFilterByNameSearch() {
        given()
                .contentType(ContentType.JSON)
                .queryParam("search", "mid-cap")
        .when()
                .get("/api/v1/schemes")
        .then()
                .statusCode(200)
                .body("content", hasSize(1))
                .body("content[0].schemeCode", equalTo(1002))
                .body("content[0].schemeName", equalTo("HDFC Mid-Cap Opportunities Fund"));
    }

    @Test
    @DisplayName("GET /api/v1/schemes with fundHouse param should filter by fund house")
    void shouldFilterByFundHouse() {
        given()
                .contentType(ContentType.JSON)
                .queryParam("fundHouse", "SBI")
        .when()
                .get("/api/v1/schemes")
        .then()
                .statusCode(200)
                .body("content", hasSize(2))
                .body("page.totalElements", equalTo(2));
    }

    @Test
    @DisplayName("GET /api/v1/schemes with pagination parameters should return subset")
    void shouldPaginateCorrectly() {
        given()
                .contentType(ContentType.JSON)
                .queryParam("page", 1)
                .queryParam("size", 2)
        .when()
                .get("/api/v1/schemes")
        .then()
                .statusCode(200)
                .body("content", hasSize(2))
                .body("page.number", equalTo(1))
                .body("page.size", equalTo(2))
                .body("content[0].schemeCode", equalTo(1003));
    }

    @Test
    @DisplayName("GET /api/v1/schemes should return 400 when page or size is invalid")
    void shouldReturn400OnInvalidPagination() {
        given()
                .contentType(ContentType.JSON)
                .queryParam("page", -1)
        .when()
                .get("/api/v1/schemes")
        .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_REQUEST"))
                .body("timestamp", notNullValue());

        given()
                .contentType(ContentType.JSON)
                .queryParam("size", 500)
        .when()
                .get("/api/v1/schemes")
        .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_REQUEST"));
    }
}
