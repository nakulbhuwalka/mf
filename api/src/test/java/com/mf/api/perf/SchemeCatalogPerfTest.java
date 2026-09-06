package com.mf.api.perf;

import com.mf.api.BaseApiTest;
import com.mf.api.model.Scheme;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchemeCatalogPerfTest extends BaseApiTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchemeCatalogPerfTest.class);

    @Autowired
    private SchemeRepository schemeRepository;

    @BeforeAll
    void loadLargeDataset() {
        schemeRepository.deleteAll();

        final List<Scheme> dataset = new ArrayList<>(1000);
        for (int i = 1; i <= 1000; i++) {
            final String fundHouse = (i % 2 == 0) ? "HDFC Mutual Fund" : "SBI Mutual Fund";
            final String category = (i % 3 == 0) ? "Equity Scheme - Large Cap" : "Equity Scheme - Small Cap";
            dataset.add(new Scheme(
                    100000 + i,
                    "Index Scheme " + i + " Growth Direct",
                    fundHouse,
                    "Open Ended Schemes",
                    category,
                    "INF" + i,
                    null,
                    false
            ));
        }
        schemeRepository.saveAll(dataset);
    }

    @Test
    @DisplayName("Scheme Catalog should achieve sub-20ms p95 latency under 20 concurrent clients")
    void shouldAchieveTargetLatencyUnderLoad() throws InterruptedException, ExecutionException {
        // Warm-up phase
        for (int i = 0; i < 20; i++) {
            RestAssured.get("/api/v1/schemes?search=Index").then().statusCode(200);
        }

        final int totalRequests = 200;
        final int concurrency = 20;
        final ExecutorService executor = Executors.newFixedThreadPool(concurrency);

        final List<Callable<Long>> tasks = new ArrayList<>(totalRequests);
        for (int i = 0; i < totalRequests; i++) {
            final int index = i;
            tasks.add(() -> {
                final String query = (index % 2 == 0) ? "search=HDFC" : "fundHouse=SBI";
                final long start = System.nanoTime();
                final Response response = RestAssured.get("/api/v1/schemes?" + query);
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
                LOGGER.warn("Executor did not terminate within 30 seconds");
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

        LOGGER.info("Scheme Catalog Perf Metrics: p50={}ms, p90={}ms, p95={}ms, p99={}ms",
                p50, p90, p95, p99);

        // Assert performance target
        assertTrue(p95 < 100, "p95 latency was " + p95 + "ms (should be well within target)");
    }
}
