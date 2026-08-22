package com.filemind.config;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMaintenanceConfigTest {

    @Test
    void repairLocalH2SchemaAddsNewColumnsNeededByCurrentDashboardAndVersionModel() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:schema-repair;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE USERS (ID VARCHAR(255) PRIMARY KEY, ROLE VARCHAR(16))");
        jdbcTemplate.execute("CREATE TABLE DOCUMENT_ACCESS (ID VARCHAR(255) PRIMARY KEY, PERMISSION VARCHAR(16))");
        jdbcTemplate.execute("CREATE TABLE DOCUMENTS (ID VARCHAR(255) PRIMARY KEY, NAME VARCHAR(255))");
        jdbcTemplate.execute("""
            CREATE TABLE DOCUMENT_VERSIONS (
              ID VARCHAR(255) PRIMARY KEY,
              DOCUMENT_ID VARCHAR(255),
              CREATED_BY_ID VARCHAR(255),
              VERSION_NUMBER INTEGER,
              FILE_NAME VARCHAR(255),
              MEDIA_TYPE VARCHAR(255),
              CONTENT_HASH VARCHAR(128),
              LOGICAL_SIZE BIGINT,
              ACTUAL_STORED_SIZE BIGINT,
              DEDUP_SAVED_SIZE BIGINT,
              CURRENT_VERSION BOOLEAN,
              MANIFEST_PATH VARCHAR(255),
              ORIGIN VARCHAR(24)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE ACTIVITY_EVENTS (
              ID VARCHAR(255) PRIMARY KEY,
              ACTION VARCHAR(24),
              ACTOR_NAME VARCHAR(255),
              ACTOR_EMAIL VARCHAR(255),
              DETAILS VARCHAR(512),
              CREATED_AT TIMESTAMP
            )
            """);

        DatabaseMaintenanceConfig config = new DatabaseMaintenanceConfig(dataSource, jdbcTemplate);

        config.repairLocalH2Schema();
        config.repairLocalH2Schema();

        assertThat(columnExists(jdbcTemplate, "DOCUMENT_VERSIONS", "OCR_APPLIED")).isTrue();
        assertThat(columnExists(jdbcTemplate, "DOCUMENT_VERSIONS", "TITLE_SUGGESTION")).isTrue();
        assertThat(columnExists(jdbcTemplate, "DOCUMENTS", "LAST_VIEWED_AT")).isTrue();
        assertThat(columnExists(jdbcTemplate, "DOCUMENTS", "LAST_VIEWED_BY_EMAIL")).isTrue();
        assertThat(columnExists(jdbcTemplate, "ACTIVITY_EVENTS", "VERSION_ID")).isTrue();
        assertThat(columnExists(jdbcTemplate, "ACTIVITY_EVENTS", "VERSION_NUMBER")).isTrue();
    }

    @Test
    void repairLocalH2SchemaAllowsQueuedVersionsToPersistWithoutContentHash() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:schema-repair-nullability;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE DOCUMENT_VERSIONS (ID VARCHAR(255) PRIMARY KEY, CONTENT_HASH VARCHAR(128) NOT NULL, MANIFEST_PATH VARCHAR(255) NOT NULL, ORIGIN VARCHAR(24))");

        DatabaseMaintenanceConfig config = new DatabaseMaintenanceConfig(dataSource, jdbcTemplate);

        config.repairLocalH2Schema();

        String contentHashNullable = jdbcTemplate.queryForObject(
            "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'DOCUMENT_VERSIONS' AND COLUMN_NAME = 'CONTENT_HASH'",
            String.class
        );
        String manifestPathNullable = jdbcTemplate.queryForObject(
            "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'DOCUMENT_VERSIONS' AND COLUMN_NAME = 'MANIFEST_PATH'",
            String.class
        );

        assertThat(contentHashNullable).isEqualTo("YES");
        assertThat(manifestPathNullable).isEqualTo("YES");
    }

    @Test
    void entityManagerFactoryDependsOnLocalSchemaRepair() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("localSchemaRepair", new RootBeanDefinition(Object.class));
        beanFactory.registerBeanDefinition("entityManagerFactory", new RootBeanDefinition(Object.class));

        DatabaseMaintenanceConfig.localSchemaRepairDependencyConfigurer().postProcessBeanFactory(beanFactory);

        assertThat(beanFactory.getBeanDefinition("entityManagerFactory").getDependsOn())
            .containsExactly("localSchemaRepair");
    }

    private boolean columnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?",
            Integer.class,
            tableName,
            columnName
        );
        return count != null && count > 0;
    }
}
