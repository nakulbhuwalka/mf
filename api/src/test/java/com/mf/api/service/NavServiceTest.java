package com.mf.api.service;

import com.mf.api.client.MfApiClient;
import com.mf.api.dto.NavHistoryResponseDto;
import com.mf.api.dto.UpstreamNavDataDto;
import com.mf.api.dto.UpstreamNavHistoryDto;
import com.mf.api.dto.UpstreamSchemeItemDto;
import com.mf.api.exception.InvalidDateRangeException;
import com.mf.api.exception.SchemeNotFoundException;
import com.mf.api.model.Scheme;
import com.mf.api.repository.NavRecordRepository;
import com.mf.api.repository.SchemeRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class NavServiceTest {

    @MockitoBean
    private MfApiClient mfApiClient;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private NavRecordRepository navRecordRepository;

    @Autowired
    private NavService navService;

    @BeforeEach
    void cleanDatabase() {
        navService.clearCache();
        navRecordRepository.deleteAll();
        schemeRepository.deleteAll();
    }

    @Test
    @DisplayName("Should lazy load NAV records on first call and hit cache on subsequent call")
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
        final List<UpstreamNavDataDto> data = List.of(
                new UpstreamNavDataDto("04-09-2026", "216.6525"),
                new UpstreamNavDataDto("03-09-2026", "216.4037"),
                new UpstreamNavDataDto("02-09-2026", "215.8012")
        );
        final UpstreamNavHistoryDto upstreamDto = new UpstreamNavHistoryDto(meta, data, "SUCCESS");

        when(mfApiClient.fetchSchemeNav(schemeCode)).thenReturn(upstreamDto);

        // 1. First call (cache miss)
        final NavHistoryResponseDto firstResponse = navService.getSchemeNav(
                schemeCode, null, null, "desc", false
        );

        assertNotNull(firstResponse);
        assertEquals(3, firstResponse.data().size());
        assertEquals("2026-09-04", firstResponse.data().get(0).date());
        assertEquals(216.6525f, firstResponse.data().get(0).nav());
        assertEquals("2026-09-02", firstResponse.meta().startDate());
        assertEquals("2026-09-04", firstResponse.meta().endDate());

        // Verify scheme is marked navSynced = true
        final Scheme scheme = schemeRepository.findById(schemeCode).orElseThrow();
        assertTrue(scheme.isNavSynced());

        // 2. Second call (cache hit)
        final NavHistoryResponseDto secondResponse = navService.getSchemeNav(
                schemeCode, null, null, "desc", false
        );

        assertEquals(3, secondResponse.data().size());
        // Verify client was only called once (on the first request)
        verify(mfApiClient, times(1)).fetchSchemeNav(schemeCode);
    }

    @Test
    @DisplayName("Should filter records by date range correctly")
    void shouldFilterByDateRange() {
        final int schemeCode = 125497;
        final UpstreamSchemeItemDto meta = new UpstreamSchemeItemDto(
                schemeCode, "SBI SMALL CAP FUND", "SBI", "Open", "Equity", "INF1", null
        );
        final List<UpstreamNavDataDto> data = List.of(
                new UpstreamNavDataDto("05-09-2026", "220.0"),
                new UpstreamNavDataDto("04-09-2026", "218.0"),
                new UpstreamNavDataDto("03-09-2026", "216.0"),
                new UpstreamNavDataDto("02-09-2026", "214.0"),
                new UpstreamNavDataDto("01-09-2026", "212.0")
        );
        when(mfApiClient.fetchSchemeNav(schemeCode)).thenReturn(new UpstreamNavHistoryDto(meta, data, "SUCCESS"));

        final NavHistoryResponseDto filtered = navService.getSchemeNav(
                schemeCode, "2026-09-02", "2026-09-04", "desc", false
        );

        assertEquals(3, filtered.data().size());
        assertEquals("2026-09-04", filtered.data().get(0).date());
        assertEquals("2026-09-02", filtered.data().get(2).date());
        assertEquals("2026-09-02", filtered.meta().startDate());
        assertEquals("2026-09-04", filtered.meta().endDate());
    }

    @Test
    @DisplayName("Should sort records in ascending order when sort=asc")
    void shouldSortAscending() {
        final int schemeCode = 125497;
        final UpstreamSchemeItemDto meta = new UpstreamSchemeItemDto(
                schemeCode, "SBI SMALL CAP FUND", "SBI", "Open", "Equity", "INF1", null
        );
        final List<UpstreamNavDataDto> data = List.of(
                new UpstreamNavDataDto("03-09-2026", "216.0"),
                new UpstreamNavDataDto("01-09-2026", "212.0")
        );
        when(mfApiClient.fetchSchemeNav(schemeCode)).thenReturn(new UpstreamNavHistoryDto(meta, data, "SUCCESS"));

        final NavHistoryResponseDto ascResponse = navService.getSchemeNav(
                schemeCode, null, null, "asc", false
        );

        assertEquals(2, ascResponse.data().size());
        assertEquals("2026-09-01", ascResponse.data().get(0).date());
        assertEquals("2026-09-03", ascResponse.data().get(1).date());
    }

    @Test
    @DisplayName("Should throw InvalidDateRangeException when startDate is after endDate")
    void shouldThrowWhenStartDateAfterEndDate() {
        final int schemeCode = 125497;
        assertThrows(
                InvalidDateRangeException.class,
                () -> navService.getSchemeNav(schemeCode, "2026-09-10", "2026-09-01", "desc", false)
        );
    }

    @Test
    @DisplayName("Should propagate SchemeNotFoundException when scheme does not exist")
    void shouldPropagateSchemeNotFound() {
        when(mfApiClient.fetchSchemeNav(999999)).thenThrow(new SchemeNotFoundException(999999));

        assertThrows(SchemeNotFoundException.class, () -> navService.getSchemeNav(999999, null, null, "desc", false));
    }
}
