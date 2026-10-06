package com.mf.migrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NavTableTest {

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
                WHERE table_schema = 'public' AND table_name = 'nav'
                ORDER BY ordinal_position""");

        assertEquals(List.of("scheme_code:integer:NO", "nav_date:date:NO", "nav:numeric:NO"), columns);
    }

    @Test
    void navIsExactDecimalWithSixPlaces() throws Exception {
        String precision = TestDatabase.scalar("""
                SELECT numeric_precision || ',' || numeric_scale
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'nav' AND column_name = 'nav'""");

        assertEquals("18,6", precision);
    }

    @Test
    void primaryKeyIsSchemeCodeThenDate() throws Exception {
        String primaryKey = TestDatabase.scalar("""
                SELECT string_agg(kcu.column_name, ',' ORDER BY kcu.ordinal_position)
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON kcu.constraint_name = tc.constraint_name AND kcu.table_schema = tc.table_schema
                WHERE tc.table_schema = 'public' AND tc.table_name = 'nav' AND tc.constraint_type = 'PRIMARY KEY'""");

        assertEquals("scheme_code,nav_date", primaryKey);
    }

    @Test
    void navRowsMustReferenceAnExistingScheme() {
        var rejected = assertThrows(SQLException.class, () -> insertNav(424242, "2026-10-05", "10.5"));

        assertEquals("23503", rejected.getSQLState(), rejected.getMessage()); // foreign_key_violation
    }

    @Test
    void deletingASchemeDeletesItsNavs() throws Exception {
        insertNav(SCHEME, "2026-10-01", "100.1");
        insertNav(SCHEME, "2026-10-05", "100.2");

        TestDatabase.update("DELETE FROM scheme WHERE scheme_code = ?", SCHEME);

        assertEquals(0L, (Long) TestDatabase.scalar("SELECT count(*) FROM nav"));
    }

    @Test
    void upsertIsIdempotentAndKeepsTheLatestValue() throws Exception {
        String upsert = """
                INSERT INTO nav (scheme_code, nav_date, nav) VALUES (?, ?, ?)
                ON CONFLICT (scheme_code, nav_date) DO UPDATE SET nav = EXCLUDED.nav""";

        TestDatabase.update(upsert, SCHEME, LocalDate.parse("2026-10-05"), new BigDecimal("100.000000"));
        TestDatabase.update(upsert, SCHEME, LocalDate.parse("2026-10-05"), new BigDecimal("101.250000"));

        assertEquals(1L, (Long) TestDatabase.scalar("SELECT count(*) FROM nav"));
        assertEquals(0, new BigDecimal("101.25").compareTo(TestDatabase.scalar("SELECT nav FROM nav")));
    }

    @Test
    void sixDecimalPlacesRoundTripExactly() throws Exception {
        insertNav(SCHEME, "2026-10-05", "107.033312");

        BigDecimal stored = TestDatabase.scalar("SELECT nav FROM nav");

        assertEquals(new BigDecimal("107.033312"), stored);
    }

    private static void insertNav(int scheme, String date, String nav) throws SQLException {
        TestDatabase.update(
                "INSERT INTO nav (scheme_code, nav_date, nav) VALUES (?, ?, ?)",
                scheme, LocalDate.parse(date), new BigDecimal(nav));
    }
}
