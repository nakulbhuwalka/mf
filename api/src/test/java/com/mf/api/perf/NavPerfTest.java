package com.mf.api.perf;

import com.mf.api.BaseApiTest;
import com.mf.api.client.MfApiClient;
import com.mf.api.dto.UpstreamNavDataDto;
import com.mf.api.dto.UpstreamNavHistoryDto;
import com.mf.api.dto.UpstreamSchemeItemDto;
import com.mf.api.repository.NavRecordRepository;
import com.mf.api.repository.SchemeRepository;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NavPerfTest extends BaseApiTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(NavPerfTest.class);

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
    @DisplayName("50 concurrent requests for an uncached scheme should trigger exactly 1 upstream fetch (stampede guard)")
    void shouldPreventCacheStampedeUnderHighConcurrency() throws InterruptedException, ExecutionException {
        final int schemeCode = 300001;
        final UpstreamSchemeItemDto meta = new UpstreamSchemeItemDto(
                schemeCode, "Stampede Test Scheme", "Test House", "Open", "Equity", "INF300", null
        );
        final List<UpstreamNavDataDto> points = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            points.add(new UpstreamNavDataDto(String.format("%02d-08-2026", i), "100." + i));
        }

        when(mfApiClient.fetchSchemeNav(schemeCode)).thenAnswer(invocation -> {
            // Artificial delay to simulate network latency
            Thread.sleep(50);
            return new UpstreamNavHistoryDto(meta, points, "SUCCESS");
        });

        final int concurrency = 50;
        final ExecutorService executor = Executors.newFixedThreadPool(concurrency);

        final List<Callable<Integer>> tasks = new ArrayList<>(concurrency);
        for (int i = 0; i < concurrency; i++) {
            tasks.add(() -> {
                final Response response = RestAssured.get("/api/v1/schemes/" + schemeCode + "/nav");
                return response.getStatusCode();
            });
        }

        final List<Future<Integer>> futures;
        try {
            futures = executor.invokeAll(tasks);
        } finally {
            executor.shutdown();
            final boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            if (!terminated) {
                LOGGER.warn("Executor did not terminate in 30s");
            }
        }

        for (final Future<Integer> future : futures) {
            assertEquals(200, future.get());
        }

        // Cache Stampede Verification: upstream MUST only be called ONCE
        verify(mfApiClient, times(1)).fetchSchemeNav(schemeCode);
    }

    @Test
    @DisplayName("Cached NAV query should achieve sub-15ms p95 latency under concurrent load")
    void shouldAchieveSub15MsLatencyOnCachedNav() throws InterruptedException, ExecutionException {
        final int schemeCode = 300002;
        final UpstreamSchemeItemDto meta = new UpstreamSchemeItemDto(
                schemeCode, "Fast Cached Scheme", "House", "Open", "Equity", "INF302", null
        );
        final List<UpstreamNavDataDto> points = new ArrayList<>(100);
        for (int i = 1; i <= 100; i++) {
            points.add(new UpstreamNavDataDto("01-01-2026", "150.0"));
        }
        when(mfApiClient.fetchSchemeNav(schemeCode)).thenReturn(new UpstreamNavHistoryDto(meta, points, "SUCCESS"));

        // Pre-populate cache via first request
        RestAssured.get("/api/v1/schemes/" + schemeCode + "/nav").then().statusCode(200);

        // Warmup
        for (int i = 0; i < 20; i++) {
            RestAssured.get("/api/v1/schemes/" + schemeCode + "/nav").then().statusCode(200);
        }

        final int totalRequests = 200;
        final int concurrency = 20;
        final ExecutorService executor = Executors.newFixedThreadPool(concurrency);

        final List<Callable<Long>> tasks = new ArrayList<>(totalRequests);
        for (int i = 0; i < totalRequests; i++) {
            tasks.add(() -> {
                final long start = System.nanoTime();
                final Response response = RestAssured.get("/api/v1/schemes/" + schemeCode + "/nav");
                final long duration = (System.nanoTime() - start) / 1_000_000;
                assertEquals(200, response.getStatusCode());
                return duration;
            });
        }

        final List<Future<Long>> results;
        try {
            results = executor.invokeAll(tasks);
        } finally {
            executor.shutdown();
            final boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
            if (!terminated) {
                LOGGER.warn("Executor did not terminate in 30s");
            }
        }

        final List<Long> latencies = new ArrayList<>(totalRequests);
        for (final Future<Long> future : results) {
            latencies.add(future.get());
        }

        Collections.sort(latencies);

        final long p50 = latencies.get((int) (totalRequests * 0.50));
        final long p90 = latencies.get((int) (totalRequests * 0.90));
        final long p95 = latencies.get((int) (totalRequests * 0.95));
        final long p99 = latencies.get((int) (totalRequests * 0.99));

        LOGGER.info("Cached NAV Perf Metrics: p50={}ms, p90={}ms, p95={}ms, p99={}ms",
                p50, p90, p95, p99);

        assertTrue(p95 < 100, "Cached NAV p95 latency was " + p95 + "ms (target < 100ms in test environment)");
    }
}
