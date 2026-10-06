package com.mf.migrations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MigrationLifecycleTest {

    private static final List<String> TABLES = List.of("load_run", "nav", "scheme", "scheme_sync");

    @BeforeEach
    void emptyDatabase() throws Exception {
        TestDatabase.reset();
    }

    @Test
    void appliesTheFiveChangeSetsInOrder() throws Exception {
        TestDatabase.migrate();

        assertEquals(List.of("001", "002", "003", "004", "005"),
                TestDatabase.column("SELECT id FROM databasechangelog ORDER BY orderexecuted"));
        assertEquals(TABLES, applicationTables());
    }

    @Test
    void migratingAgainChangesNothing() throws Exception {
        TestDatabase.migrate();
        TestDatabase.migrate();

        assertEquals(5L, (Long) TestDatabase.scalar("SELECT count(*) FROM databasechangelog"));
        assertEquals(TABLES, applicationTables());
    }

    @Test
    void rollingBackEverythingLeavesNoApplicationObjects() throws Exception {
        TestDatabase.migrate();

        TestDatabase.rollback(5);

        assertEquals(List.of(), applicationTables());
        assertEquals(0L, (Long) TestDatabase.scalar("SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'"));
    }

    @Test
    void canMigrateAgainAfterAFullRollback() throws Exception {
        TestDatabase.migrate();
        TestDatabase.rollback(5);

        TestDatabase.migrate();

        assertEquals(TABLES, applicationTables());
        assertEquals(1L, (Long) TestDatabase.scalar("SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'"));
    }

    private static List<String> applicationTables() throws Exception {
        return TestDatabase.column("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name NOT LIKE 'databasechangelog%'
                ORDER BY table_name""");
    }
}
