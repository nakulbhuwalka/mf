package com.mf.migrations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExtensionsTest {

    @BeforeEach
    void freshDatabase() throws Exception {
        TestDatabase.reset();
        TestDatabase.migrate();
    }

    @Test
    void trigramExtensionIsInstalled() throws Exception {
        Long installed = TestDatabase.scalar("SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'");

        assertEquals(1L, installed);
    }
}
