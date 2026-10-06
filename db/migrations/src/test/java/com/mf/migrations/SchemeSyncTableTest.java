package com.mf.migrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchemeSyncTableTest {

    private static final int SCHEME = 119551;

    @BeforeEach
    void freshDatabaseWithOneScheme() throws Exception {
        TestDatabase.reset();
        TestDatabase.migrate();
        TestDatabase.update("INSERT INTO scheme (scheme_code, scheme_name) VALUES (?, ?)", SCHEME, "Some Fund");
    }

    @Test
    void columnsMatchTheDesign() throws Exception {
        List<String> columns = TestDatabase.column("""
                SELECT column_name || ':' || data_type || ':' || is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'scheme_sync'
                ORDER BY ordinal_position""");

        assertEquals(List.of(
                "scheme_code:integer:NO",
                "latest_nav_date:date:YES",
                "history_loaded:boolean:NO",
                "last_checked_at:timestamp with time zone:YES"), columns);
    }

    @Test
    void historyLoadedDefaultsToFalse() throws Exception {
        TestDatabase.update("INSERT INTO scheme_sync (scheme_code) VALUES (?)", SCHEME);

        assertEquals(Boolean.FALSE, TestDatabase.scalar("SELECT history_loaded FROM scheme_sync WHERE scheme_code = ?", SCHEME));
    }

    @Test
    void thereIsOneRowPerScheme() throws Exception {
        TestDatabase.update("INSERT INTO scheme_sync (scheme_code) VALUES (?)", SCHEME);

        var rejected = assertThrows(SQLException.class,
                () -> TestDatabase.update("INSERT INTO scheme_sync (scheme_code) VALUES (?)", SCHEME));

        assertEquals("23505", rejected.getSQLState(), rejected.getMessage()); // unique_violation
    }

    @Test
    void syncRowsMustReferenceAnExistingScheme() {
        var rejected = assertThrows(SQLException.class,
                () -> TestDatabase.update("INSERT INTO scheme_sync (scheme_code) VALUES (?)", 424242));

        assertEquals("23503", rejected.getSQLState(), rejected.getMessage()); // foreign_key_violation
    }

    @Test
    void deletingASchemeDeletesItsSyncRow() throws Exception {
        TestDatabase.update("INSERT INTO scheme_sync (scheme_code) VALUES (?)", SCHEME);

        TestDatabase.update("DELETE FROM scheme WHERE scheme_code = ?", SCHEME);

        assertEquals(0L, (Long) TestDatabase.scalar("SELECT count(*) FROM scheme_sync"));
    }
}
