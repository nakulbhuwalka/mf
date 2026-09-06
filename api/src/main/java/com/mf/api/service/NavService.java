package com.mf.api.service;

import com.mf.api.client.MfApiClient;
import com.mf.api.dto.NavHistoryResponseDto;
import com.mf.api.dto.NavMetaDto;
import com.mf.api.dto.NavPointDto;
import com.mf.api.dto.UpstreamNavDataDto;
import com.mf.api.dto.UpstreamNavHistoryDto;
import com.mf.api.dto.UpstreamSchemeItemDto;
import com.mf.api.exception.InvalidDateRangeException;
import com.mf.api.exception.SchemeNotFoundException;
import com.mf.api.model.NavRecord;
import com.mf.api.model.Scheme;
import com.mf.api.repository.NavRecordRepository;
import com.mf.api.repository.SchemeRepository;
import com.mf.api.util.DateEpochUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NavService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NavService.class);
    private static final int BATCH_SIZE = 2000;

    private static final String NAV_UPSERT_SQL = """
            MERGE INTO nav_records (scheme_code, nav_date, nav_value)
            KEY (scheme_code, nav_date) VALUES (?, ?, ?)
            """;

    private final SchemeRepository schemeRepository;
    private final NavRecordRepository navRecordRepository;
    private final MfApiClient mfApiClient;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ConcurrentMap<Integer, Object> schemeLocks = new ConcurrentHashMap<>();
    private final java.util.Set<Integer> syncedSchemes = ConcurrentHashMap.newKeySet();

    public void clearCache() {
        syncedSchemes.clear();
        schemeLocks.clear();
    }

    public NavService(
            final SchemeRepository schemeRepository,
            final NavRecordRepository navRecordRepository,
            final MfApiClient mfApiClient,
            final JdbcTemplate jdbcTemplate,
            final TransactionTemplate transactionTemplate) {
        this.schemeRepository = schemeRepository;
        this.navRecordRepository = navRecordRepository;
        this.mfApiClient = mfApiClient;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    public NavHistoryResponseDto getSchemeNav(
            final int schemeCode,
            final String startDate,
            final String endDate,
            final String sort,
            final boolean forceRefresh) {

        final Integer startEpoch = parseDateParam(startDate, "startDate");
        final Integer endEpoch = parseDateParam(endDate, "endDate");

        if (startEpoch != null && endEpoch != null && startEpoch > endEpoch) {
            throw new InvalidDateRangeException(
                    "startDate (" + startDate + ") cannot be after endDate (" + endDate + ")"
            );
        }

        ensureNavLoaded(schemeCode, forceRefresh);

        final Scheme scheme = schemeRepository.findById(schemeCode)
                .orElseThrow(() -> new SchemeNotFoundException(schemeCode));

        final boolean ascending = "asc".equalsIgnoreCase(sort);
        final List<NavRecord> records = ascending
                ? navRecordRepository.findNavRecordsAsc(schemeCode, startEpoch, endEpoch)
                : navRecordRepository.findNavRecordsDesc(schemeCode, startEpoch, endEpoch);

        final List<NavPointDto> points = records.stream()
                .map(r -> new NavPointDto(DateEpochUtils.toIsoString(r.getNavDate()), r.getNavValue()))
                .toList();

        final String metaStartDate;
        final String metaEndDate;
        if (records.isEmpty()) {
            metaStartDate = null;
            metaEndDate = null;
        } else {
            final int minEpoch = ascending ? records.getFirst().getNavDate() : records.getLast().getNavDate();
            final int maxEpoch = ascending ? records.getLast().getNavDate() : records.getFirst().getNavDate();
            metaStartDate = DateEpochUtils.toIsoString(minEpoch);
            metaEndDate = DateEpochUtils.toIsoString(maxEpoch);
        }

        final NavMetaDto meta = new NavMetaDto(
                scheme.getSchemeCode(),
                scheme.getSchemeName(),
                scheme.getFundHouse(),
                scheme.getSchemeType(),
                scheme.getSchemeCategory(),
                scheme.getIsinGrowth(),
                scheme.getIsinDivReinvestment(),
                points.size(),
                metaStartDate,
                metaEndDate
        );

        return new NavHistoryResponseDto(meta, points);
    }

    private Integer parseDateParam(final String dateStr, final String paramName) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return DateEpochUtils.toEpochDayFromIso(dateStr);
        } catch (final IllegalArgumentException ex) {
            throw new InvalidDateRangeException("Invalid format for " + paramName + ", expected YYYY-MM-DD: " + dateStr, ex);
        }
    }

    private void ensureNavLoaded(final int schemeCode, final boolean forceRefresh) {
        if (!forceRefresh && syncedSchemes.contains(schemeCode)) {
            return;
        }

        final Object lock = schemeLocks.computeIfAbsent(schemeCode, k -> new Object());
        synchronized (lock) {
            if (!forceRefresh && syncedSchemes.contains(schemeCode)) {
                return;
            }

            final Scheme current = schemeRepository.findById(schemeCode).orElse(null);
            if (current != null && current.isNavSynced() && !forceRefresh) {
                syncedSchemes.add(schemeCode);
                return;
            }

            fetchAndPersistNav(schemeCode, current);
            syncedSchemes.add(schemeCode);
        }
    }

    protected void fetchAndPersistNav(final int schemeCode, final Scheme existingScheme) {
        final UpstreamNavHistoryDto history = mfApiClient.fetchSchemeNav(schemeCode);
        final UpstreamSchemeItemDto meta = history.meta();
        if (meta == null) {
            throw new SchemeNotFoundException(schemeCode);
        }

        final Scheme scheme = existingScheme == null ? new Scheme() : existingScheme;
        scheme.setSchemeCode(schemeCode);
        scheme.setSchemeName(meta.schemeName() != null ? meta.schemeName() : "Scheme " + schemeCode);
        scheme.setFundHouse(meta.fundHouse());
        scheme.setSchemeType(meta.schemeType());
        scheme.setSchemeCategory(meta.schemeCategory());
        scheme.setIsinGrowth(meta.isinGrowth());
        scheme.setIsinDivReinvestment(meta.isinDivReinvestment());
        scheme.setNavSynced(true);

        final List<UpstreamNavDataDto> rawData = history.data();
        record ParsedNav(int schemeCode, int navDate, float navValue) {}
        final List<ParsedNav> parsedList;
        if (rawData == null || rawData.isEmpty()) {
            parsedList = List.of();
        } else {
            parsedList = new ArrayList<>(rawData.size());
            for (final UpstreamNavDataDto item : rawData) {
                if (item.date() == null || item.nav() == null) {
                    continue;
                }
                try {
                    final int epochDay = DateEpochUtils.toEpochDayFromDdMmYyyy(item.date());
                    final float navFloat = Float.parseFloat(item.nav().trim());
                    parsedList.add(new ParsedNav(schemeCode, epochDay, navFloat));
                } catch (final IllegalArgumentException | NullPointerException ex) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Skipping unparseable NAV entry: date={}, nav={}", item.date(), item.nav(), ex);
                    }
                }
            }
        }

        transactionTemplate.executeWithoutResult(status -> {
            schemeRepository.saveAndFlush(scheme);
            if (!parsedList.isEmpty()) {
                jdbcTemplate.batchUpdate(
                        NAV_UPSERT_SQL,
                        parsedList,
                        BATCH_SIZE,
                        (ps, item) -> {
                            ps.setInt(1, item.schemeCode());
                            ps.setInt(2, item.navDate());
                            ps.setFloat(3, item.navValue());
                        }
                );
            }
        });
    }
}
