package com.mf.api.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class DateEpochUtils {

    private static final DateTimeFormatter DD_MM_YYYY_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter YYYY_MM_DD_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private DateEpochUtils() {
        // Private constructor for utility class
    }

    public static int toEpochDayFromDdMmYyyy(final String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            throw new IllegalArgumentException("Date string cannot be blank");
        }
        try {
            final LocalDate localDate = LocalDate.parse(dateStr.trim(), DD_MM_YYYY_FORMATTER);
            return Math.toIntExact(localDate.toEpochDay());
        } catch (final DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid date format, expected dd-MM-yyyy: " + dateStr, ex);
        }
    }

    public static int toEpochDayFromIso(final String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            throw new IllegalArgumentException("Date string cannot be blank");
        }
        try {
            final LocalDate localDate = LocalDate.parse(dateStr.trim(), YYYY_MM_DD_FORMATTER);
            return Math.toIntExact(localDate.toEpochDay());
        } catch (final DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid date format, expected YYYY-MM-DD: " + dateStr, ex);
        }
    }

    public static String toIsoString(final int epochDay) {
        return LocalDate.ofEpochDay(epochDay).format(YYYY_MM_DD_FORMATTER);
    }
}
