package com.mf.api.service;

import com.mf.api.dto.SchemePageResponseDto;
import com.mf.api.model.Scheme;
import com.mf.api.repository.SchemeRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SchemeQueryServiceTest {

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private SchemeQueryService schemeQueryService;

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
    @DisplayName("Should return paginated schemes with default sorting")
    void shouldReturnPaginatedSchemes() {
        final SchemePageResponseDto result = schemeQueryService.getSchemes(0, 2, null, null, null, null);

        assertNotNull(result);
        assertEquals(2, result.content().size());
        assertEquals(1001, result.content().get(0).schemeCode());
        assertEquals(1002, result.content().get(1).schemeCode());
        assertEquals(0, result.page().number());
        assertEquals(2, result.page().size());
        assertEquals(5, result.page().totalElements());
        assertEquals(3, result.page().totalPages());
    }

    @Test
    @DisplayName("Should filter schemes by case-insensitive name search")
    void shouldFilterByNameSearch() {
        final SchemePageResponseDto result = schemeQueryService.getSchemes(0, 10, "hdfc", null, null, null);

        assertEquals(2, result.content().size());
        assertTrue(result.content().stream().allMatch(s -> s.schemeName().contains("HDFC")));
    }

    @Test
    @DisplayName("Should filter schemes by fund house")
    void shouldFilterByFundHouse() {
        final SchemePageResponseDto result = schemeQueryService.getSchemes(0, 10, null, "SBI", null, null);

        assertEquals(2, result.content().size());
        assertTrue(result.content().stream().allMatch(s -> s.fundHouse().contains("SBI")));
    }

    @Test
    @DisplayName("Should filter schemes by category")
    void shouldFilterByCategory() {
        final SchemePageResponseDto result = schemeQueryService.getSchemes(0, 10, null, null, null, "Large Cap");

        assertEquals(2, result.content().size());
        assertTrue(result.content().stream().allMatch(s -> s.schemeCategory().contains("Large Cap")));
    }

    @Test
    @DisplayName("Should filter schemes by combined multi-filter")
    void shouldFilterByCombinedCriteria() {
        final SchemePageResponseDto result = schemeQueryService.getSchemes(
                0, 10, "top 100", "HDFC", "Open Ended Schemes", "Large Cap"
        );

        assertEquals(1, result.content().size());
        assertEquals(1001, result.content().get(0).schemeCode());
        assertEquals("HDFC Top 100 Fund - Growth", result.content().get(0).schemeName());
    }

    @Test
    @DisplayName("Should return empty content when no records match filter")
    void shouldReturnEmptyWhenNoMatch() {
        final SchemePageResponseDto result = schemeQueryService.getSchemes(0, 10, "NonExistentKeyword", null, null, null);

        assertNotNull(result);
        assertTrue(result.content().isEmpty());
        assertEquals(0, result.page().totalElements());
        assertEquals(0, result.page().totalPages());
    }

    @Test
    @DisplayName("Should handle empty page beyond available records")
    void shouldHandleEmptyPageBeyondLimit() {
        final SchemePageResponseDto result = schemeQueryService.getSchemes(10, 5, null, null, null, null);

        assertNotNull(result);
        assertTrue(result.content().isEmpty());
        assertEquals(5, result.page().totalElements());
    }
}
