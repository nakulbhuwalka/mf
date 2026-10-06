package com.mf.migrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LoadRunTableTest {

    @BeforeEach
    void freshDatabase() throws Exception {
        TestDatabase.reset();
        TestDatabase.migrate();
    }

    @Test
    void columnsMatchTheDesign() throws Exception {
        List<String> columns = TestDatabase.column("""
                SELECT column_name || ':' || data_type || ':' || is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'load_run'
                ORDER BY ordinal_position""");

        assertEquals(List.of(
                "id:bigint:NO",
                "started_at:timestamp with time zone:NO",
                "finished_at:timestamp with time zone:YES",
                "status:character varying:NO",
                "schemes_upserted:integer:NO",
                "error:text:YES"), columns);
    }

    @Test
    void idIsGeneratedAsIdentity() throws Exception {
        TestDatabase.update("INSERT INTO load_run (started_at, status) VALUES (now(), 'RUNNING')");
        TestDatabase.update("INSERT INTO load_run (started_at, status) VALUES (now(), 'RUNNING')");

        List<String> ids = TestDatabase.column("SELECT id::text FROM load_run ORDER BY id");

        assertEquals(2, ids.size());
        assertNotEquals(ids.get(0), ids.get(1));
        assertEquals("YES", TestDatabase.scalar("""
                SELECT is_identity FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'load_run' AND column_name = 'id'"""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RUNNING", "SUCCESS", "FAILED"})
    void knownStatusesAreAccepted(String status) throws Exception {
        assertEquals(1, TestDatabase.update("INSERT INTO load_run (started_at, status) VALUES (now(), ?)", status));
    }

    @Test
    void unknownStatusIsRejected() {
        var rejected = assertThrows(SQLException.class,
                () -> TestDatabase.update("INSERT INTO load_run (started_at, status) VALUES (now(), 'BOGUS')"));

        assertEquals("23514", rejected.getSQLState(), rejected.getMessage()); // check_violation
    }

    @Test
    void schemesUpsertedDefaultsToZero() throws Exception {
        TestDatabase.update("INSERT INTO load_run (started_at, status) VALUES (now(), 'RUNNING')");

        assertEquals(0, (Integer) TestDatabase.scalar("SELECT schemes_upserted FROM load_run"));
    }

    @Test
    void startTimeIsRequired() {
        var rejected = assertThrows(SQLException.class,
                () -> TestDatabase.update("INSERT INTO load_run (status) VALUES ('RUNNING')"));

        assertEquals("23502", rejected.getSQLState(), rejected.getMessage()); // not_null_violation
    }
}
