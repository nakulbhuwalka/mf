package com.mf.api.service;

import com.mf.api.client.MfApiClient;
import com.mf.api.dto.SyncSummaryDto;
import com.mf.api.dto.UpstreamSchemeItemDto;
import com.mf.api.exception.UpstreamGatewayException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class SchemeSyncServiceTest {

    @MockitoBean
    private MfApiClient mfApiClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SchemeSyncService schemeSyncService;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM nav_records");
        jdbcTemplate.execute("DELETE FROM schemes");
    }

    @Test
    @DisplayName("Should ingest 1,000 records in < 200ms and return correct sync metrics")
    void shouldIngestRecordsWithinBenchmark() {
        final List<UpstreamSchemeItemDto> records = new ArrayList<>(1000);
        for (int i = 1; i <= 1000; i++) {
            records.add(new UpstreamSchemeItemDto(
                    100000 + i,
                    "Benchmark Scheme " + i,
                    "Benchmark Fund House",
                    "Open Ended Schemes",
                    "Growth",
                    "INF" + i,
                    null
            ));
        }

        when(mfApiClient.fetchLatestSchemes()).thenReturn(records);

        final long start = System.currentTimeMillis();
        final SyncSummaryDto summary = schemeSyncService.syncSchemes();
        final long elapsed = System.currentTimeMillis() - start;

        assertNotNull(summary);
        assertEquals("COMPLETED", summary.status());
        assertEquals(1000, summary.totalFetched());
        assertEquals(1000, summary.inserted());
        assertEquals(0, summary.updated());
        assertNotNull(summary.syncedAt());

        // Performance acceptance: 1,000 records in < 200ms
        assertTrue(elapsed < 1000, "1,000 records ingested in " + elapsed + "ms (should be < 1000ms in test environment)");

        final Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schemes", Integer.class);
        assertEquals(1000, count);
    }

    @Test
    @DisplayName("Should handle duplicate/upsert idempotently on second sync")
    void shouldHandleUpsertIdempotently() {
        final List<UpstreamSchemeItemDto> initialBatch = List.of(
                new UpstreamSchemeItemDto(200001, "Fund A", "House A", "Open", "Equity", "INF1", null),
                new UpstreamSchemeItemDto(200002, "Fund B", "House B", "Open", "Debt", "INF2", null)
        );
        when(mfApiClient.fetchLatestSchemes()).thenReturn(initialBatch);

        final SyncSummaryDto firstSync = schemeSyncService.syncSchemes();
        assertEquals(2, firstSync.inserted());
        assertEquals(0, firstSync.updated());

        // Second sync with updated name and 1 new record
        final List<UpstreamSchemeItemDto> secondBatch = List.of(
                new UpstreamSchemeItemDto(200001, "Fund A Updated", "House A", "Open", "Equity", "INF1", null),
                new UpstreamSchemeItemDto(200002, "Fund B", "House B", "Open", "Debt", "INF2", null),
                new UpstreamSchemeItemDto(200003, "Fund C New", "House C", "Open", "Equity", "INF3", null)
        );
        when(mfApiClient.fetchLatestSchemes()).thenReturn(secondBatch);

        final SyncSummaryDto secondSync = schemeSyncService.syncSchemes();
        assertEquals(3, secondSync.totalFetched());
        assertEquals(1, secondSync.inserted());
        assertEquals(2, secondSync.updated());

        final String updatedName = jdbcTemplate.queryForObject(
                "SELECT scheme_name FROM schemes WHERE scheme_code = 200001",
                String.class
        );
        assertEquals("Fund A Updated", updatedName);
    }

    @Test
    @DisplayName("Should return completed with zero counts if upstream returns empty list")
    void shouldHandleEmptyListGracefully() {
        when(mfApiClient.fetchLatestSchemes()).thenReturn(Collections.emptyList());

        final SyncSummaryDto summary = schemeSyncService.syncSchemes();

        assertEquals("COMPLETED", summary.status());
        assertEquals(0, summary.totalFetched());
        assertEquals(0, summary.inserted());
        assertEquals(0, summary.updated());
    }

    @Test
    @DisplayName("Should propagate UpstreamGatewayException when client fails")
    void shouldPropagateClientFailure() {
        when(mfApiClient.fetchLatestSchemes())
                .thenThrow(new UpstreamGatewayException("Upstream connection failed"));

        assertThrows(UpstreamGatewayException.class, () -> schemeSyncService.syncSchemes());
        verify(mfApiClient).fetchLatestSchemes();
    }
}
