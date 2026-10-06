package com.mf.migrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchemeTableTest {

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
                WHERE table_schema = 'public' AND table_name = 'scheme'
                ORDER BY ordinal_position""");

        assertEquals(List.of(
                "scheme_code:integer:NO",
                "scheme_name:text:NO",
                "fund_house:text:YES",
                "scheme_type:text:YES",
                "scheme_category:text:YES",
                "isin_growth:character varying:YES",
                "isin_div_reinvestment:character varying:YES",
                "created_at:timestamp with time zone:NO",
                "updated_at:timestamp with time zone:NO"), columns);
    }

    @Test
    void isinColumnsHoldTwelveCharacters() throws Exception {
        List<String> lengths = TestDatabase.column("""
                SELECT character_maximum_length::text
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'scheme' AND column_name LIKE 'isin%'""");

        assertEquals(List.of("12", "12"), lengths);
    }

    @Test
    void schemeCodeIsThePrimaryKey() throws Exception {
        String primaryKey = TestDatabase.scalar("""
                SELECT string_agg(kcu.column_name, ',')
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON kcu.constraint_name = tc.constraint_name AND kcu.table_schema = tc.table_schema
                WHERE tc.table_schema = 'public' AND tc.table_name = 'scheme' AND tc.constraint_type = 'PRIMARY KEY'""");

        assertEquals("scheme_code", primaryKey);
    }

    @Test
    void indexesMatchTheDesign() throws Exception {
        List<String> indexes = TestDatabase.column(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'scheme' ORDER BY indexname");

        assertEquals(List.of(
                "ix_scheme_isin_div_reinvestment",
                "ix_scheme_isin_growth",
                "ix_scheme_name_trgm",
                "scheme_pkey"), indexes);
    }

    @Test
    void nameIndexIsTrigramGin() throws Exception {
        String definition = TestDatabase.scalar("SELECT indexdef FROM pg_indexes WHERE indexname = 'ix_scheme_name_trgm'");

        assertTrue(definition.contains("USING gin"), definition);
        assertTrue(definition.contains("gin_trgm_ops"), definition);
    }

    @Test
    void timestampsDefaultToNow() throws Exception {
        TestDatabase.update("INSERT INTO scheme (scheme_code, scheme_name) VALUES (?, ?)", 119551, "Some Fund");

        Boolean stamped = TestDatabase.scalar("""
                SELECT created_at > now() - interval '1 minute' AND updated_at > now() - interval '1 minute'
                FROM scheme WHERE scheme_code = 119551""");

        assertEquals(Boolean.TRUE, stamped);
    }

    @Test
    void schemeNameIsRequired() {
        var rejected = assertThrows(SQLException.class,
                () -> TestDatabase.update("INSERT INTO scheme (scheme_code, scheme_name) VALUES (?, ?)", 1, null));

        assertEquals("23502", rejected.getSQLState(), rejected.getMessage()); // not_null_violation
    }

    @Test
    void nameSearchCanUseTheTrigramIndex() throws Exception {
        for (int code = 1; code <= 20; code++) {
            TestDatabase.update("INSERT INTO scheme (scheme_code, scheme_name) VALUES (?, ?)", code, "Fund number " + code);
        }
        TestDatabase.update("INSERT INTO scheme (scheme_code, scheme_name) VALUES (?, ?)", 99, "Bluechip Growth Fund");

        var plan = new ArrayList<String>();
        try (var connection = TestDatabase.connect(); var statement = connection.createStatement()) {
            statement.execute("SET enable_seqscan = off"); // prove the index is usable even on a tiny table
            try (var rows = statement.executeQuery("EXPLAIN SELECT scheme_code FROM scheme WHERE scheme_name ILIKE '%bluechip%'")) {
                while (rows.next()) {
                    plan.add(rows.getString(1));
                }
            }
        }

        assertTrue(plan.stream().anyMatch(line -> line.contains("ix_scheme_name_trgm")), String.join("\n", plan));
        assertEquals(List.of("99"), TestDatabase.column("SELECT scheme_code::text FROM scheme WHERE scheme_name ILIKE '%bluechip%'"));
    }
}
