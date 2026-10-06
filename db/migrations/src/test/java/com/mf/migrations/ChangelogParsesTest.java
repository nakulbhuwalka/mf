package com.mf.migrations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.parser.ChangeLogParser;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class ChangelogParsesTest {

    private static final String MASTER = "db/changelog/db.changelog-master.yaml";

    @Test
    void parsesMasterYaml() throws Exception {
        var accessor = new ClassLoaderResourceAccessor();
        ChangeLogParser parser = ChangeLogParserFactory.getInstance().getParser(MASTER, accessor);

        DatabaseChangeLog changeLog = parser.parse(MASTER, new ChangeLogParameters(), accessor);

        assertEquals(0, changeLog.getChangeSets().size());
    }
}
