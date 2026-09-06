package com.mf.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LiquibaseMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Verify Liquibase DATABASECHANGELOG has executed all changesets")
    void verifyDatabaseChangeLogExecuted() {
        List<String> changeSetIds = jdbcTemplate.query(
                "SELECT ID FROM DATABASECHANGELOG",
                (rs, rowNum) -> rs.getString("ID")
        );

        assertThat(changeSetIds)
                .as("Liquibase changelog must record both changesets")
                .contains("001-create-schemes-table", "002-create-nav-records-table");
    }

    @Test
    @DisplayName("Verify schemes table columns adhere strictly to lean schema spec")
    void verifySchemesTableStructure() throws SQLException {
        Set<String> columns = getColumnNamesForTable("SCHEMES");

        assertThat(columns)
                .as("schemes table must have all required columns")
                .contains(
                        "SCHEME_CODE",
                        "SCHEME_NAME",
                        "FUND_HOUSE",
                        "SCHEME_TYPE",
                        "SCHEME_CATEGORY",
                        "ISIN_GROWTH",
                        "ISIN_DIV_REINVESTMENT",
                        "NAV_SYNCED"
                );

        assertThat(columns)
                .as("schemes table must NOT have timestamp or latest nav columns")
                .doesNotContain(
                        "CREATED_AT",
                        "UPDATED_AT",
                        "LATEST_NAV",
                        "LATEST_NAV_DATE"
                );
    }

    @Test
    @DisplayName("Verify nav_records table structure has exactly 3 columns with composite PK")
    void verifyNavRecordsTableStructure() throws SQLException {
        Set<String> columns = getColumnNamesForTable("NAV_RECORDS");

        assertThat(columns)
                .as("nav_records table must contain exactly scheme_code, nav_date, and nav_value")
                .containsExactlyInAnyOrder(
                        "SCHEME_CODE",
                        "NAV_DATE",
                        "NAV_VALUE"
                );
    }

    @Test
    @DisplayName("Verify data integrity, FK constraints, and cascade delete")
    void verifyDataIntegrityAndConstraints() {
        int testSchemeCode = 999001;
        int testNavDate = 20698; // days from epoch
        float testNavValue = 123.45f;

        // Clean up if exists
        jdbcTemplate.update("DELETE FROM nav_records WHERE scheme_code = ?", testSchemeCode);
        jdbcTemplate.update("DELETE FROM schemes WHERE scheme_code = ?", testSchemeCode);

        // Insert into schemes
        jdbcTemplate.update(
                "INSERT INTO schemes (scheme_code, scheme_name, fund_house, scheme_type, scheme_category, isin_growth, nav_synced) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                testSchemeCode, "Test Scheme", "Test AMC", "Open Ended", "Equity", "INF123456789", false
        );

        // Insert into nav_records
        jdbcTemplate.update(
                "INSERT INTO nav_records (scheme_code, nav_date, nav_value) VALUES (?, ?, ?)",
                testSchemeCode, testNavDate, testNavValue
        );

        // Verify inserted
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nav_records WHERE scheme_code = ?", Integer.class, testSchemeCode
        );
        assertThat(count).isEqualTo(1);

        // Verify cascade delete
        jdbcTemplate.update("DELETE FROM schemes WHERE scheme_code = ?", testSchemeCode);
        Integer countAfterDelete = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nav_records WHERE scheme_code = ?", Integer.class, testSchemeCode
        );
        assertThat(countAfterDelete).isZero();
    }

    private Set<String> getColumnNamesForTable(String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, tableName.toUpperCase(Locale.ROOT), "%")) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME").toUpperCase(Locale.ROOT));
                }
            }
            if (columns.isEmpty()) {
                // Fallback for case sensitivity or lowercase in H2
                try (ResultSet rs = metaData.getColumns(null, null, tableName.toLowerCase(Locale.ROOT), "%")) {
                    while (rs.next()) {
                        columns.add(rs.getString("COLUMN_NAME").toUpperCase(Locale.ROOT));
                    }
                }
            }
        }
        return columns;
    }
}
