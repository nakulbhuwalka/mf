package com.mf.api.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DateEpochUtilsTest {

    @Test
    @DisplayName("Should convert epoch day 0 to 1970-01-01")
    void shouldConvertEpochZero() {
        assertEquals("1970-01-01", DateEpochUtils.toIsoString(0));
        assertEquals(0, DateEpochUtils.toEpochDayFromIso("1970-01-01"));
        assertEquals(0, DateEpochUtils.toEpochDayFromDdMmYyyy("01-01-1970"));
    }

    @Test
    @DisplayName("Should handle leap years correctly")
    void shouldHandleLeapYears() {
        final int leapDayEpoch = DateEpochUtils.toEpochDayFromIso("2024-02-29");
        assertEquals("2024-02-29", DateEpochUtils.toIsoString(leapDayEpoch));
        assertEquals(leapDayEpoch, DateEpochUtils.toEpochDayFromDdMmYyyy("29-02-2024"));
    }

    @Test
    @DisplayName("Should round-trip dates accurately")
    void shouldRoundTripDates() {
        final String expected = "2026-09-06";
        final int epochDay = DateEpochUtils.toEpochDayFromIso(expected);
        final String actual = DateEpochUtils.toIsoString(epochDay);
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-date", "2026/09/06", "32-01-2026", ""})
    @DisplayName("Should throw IllegalArgumentException on invalid date formats")
    void shouldThrowOnInvalidDateFormat(final String invalidDate) {
        assertThrows(IllegalArgumentException.class, () -> DateEpochUtils.toEpochDayFromIso(invalidDate));
        assertThrows(IllegalArgumentException.class, () -> DateEpochUtils.toEpochDayFromDdMmYyyy(invalidDate));
    }
}
