package com.mf.api.service;

import com.mf.api.client.MfApiClient;
import com.mf.api.dto.SyncSummaryDto;
import com.mf.api.dto.UpstreamSchemeItemDto;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchemeSyncService {

    private static final int BATCH_SIZE = 2000;

    private static final String UPSERT_SQL = """
            MERGE INTO schemes (
                scheme_code, scheme_name, fund_house, scheme_type, scheme_category, isin_growth, isin_div_reinvestment
            ) KEY (scheme_code) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final MfApiClient mfApiClient;
    private final JdbcTemplate jdbcTemplate;

    public SchemeSyncService(final MfApiClient mfApiClient, final JdbcTemplate jdbcTemplate) {
        this.mfApiClient = mfApiClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public SyncSummaryDto syncSchemes() {
        final long startTime = System.currentTimeMillis();

        final List<UpstreamSchemeItemDto> schemes = mfApiClient.fetchLatestSchemes();
        if (schemes.isEmpty()) {
            final long durationMs = System.currentTimeMillis() - startTime;
            return new SyncSummaryDto("COMPLETED", 0, 0, 0, durationMs, Instant.now().toString());
        }

        final Integer countBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schemes", Integer.class);
        final int existingCount = countBefore == null ? 0 : countBefore;

        final List<UpstreamSchemeItemDto> validSchemes = schemes.stream()
                .filter(item -> item.schemeCode() != null && item.schemeName() != null)
                .toList();

        jdbcTemplate.batchUpdate(
                UPSERT_SQL,
                validSchemes,
                BATCH_SIZE,
                (ps, item) -> {
                    ps.setInt(1, item.schemeCode());
                    ps.setString(2, item.schemeName());
                    ps.setString(3, item.fundHouse());
                    ps.setString(4, item.schemeType());
                    ps.setString(5, item.schemeCategory());
                    ps.setString(6, item.isinGrowth());
                    ps.setString(7, item.isinDivReinvestment());
                }
        );

        final Integer countAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schemes", Integer.class);
        final int finalCount = countAfter == null ? 0 : countAfter;

        final int inserted = finalCount - existingCount;
        final int updated = validSchemes.size() - inserted;
        final long durationMs = System.currentTimeMillis() - startTime;

        return new SyncSummaryDto(
                "COMPLETED",
                validSchemes.size(),
                inserted,
                updated,
                durationMs,
                Instant.now().toString()
        );
    }
}
