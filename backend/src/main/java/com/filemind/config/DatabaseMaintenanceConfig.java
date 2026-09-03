package com.filemind.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DatabaseMaintenanceConfig {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Bean(name = "localSchemaRepair")
    public InitializingBean localSchemaRepair() {
        return this::repairSchemaIfNeeded;
    }

    @Bean
    public static BeanFactoryPostProcessor localSchemaRepairDependencyConfigurer() {
        return beanFactory -> addDependsOn(beanFactory, "entityManagerFactory", "localSchemaRepair");
    }

    private static void addDependsOn(org.springframework.beans.factory.config.ConfigurableListableBeanFactory beanFactory,
                                     String beanName, String dependencyName) {
        if (!beanFactory.containsBeanDefinition(beanName) || !beanFactory.containsBeanDefinition(dependencyName)) return;
        AbstractBeanDefinition beanDefinition = (AbstractBeanDefinition) beanFactory.getBeanDefinition(beanName);
        String[] currentDependencies = beanDefinition.getDependsOn();
        if (currentDependencies != null && Arrays.asList(currentDependencies).contains(dependencyName)) return;
        if (currentDependencies == null || currentDependencies.length == 0) {
            beanDefinition.setDependsOn(dependencyName);
            return;
        }
        String[] updatedDependencies = Arrays.copyOf(currentDependencies, currentDependencies.length + 1);
        updatedDependencies[currentDependencies.length] = dependencyName;
        beanDefinition.setDependsOn(updatedDependencies);
    }

    private void repairSchemaIfNeeded() {
        try (Connection connection = dataSource.getConnection()) {
            String jdbcUrl = connection.getMetaData().getURL();
            if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:h2:")) repairLocalH2Schema();
        } catch (Exception exception) {
            log.warn("Local schema repair skipped: {}", exception.getMessage());
        }
    }

    void repairLocalH2Schema() {
        relaxEnumColumn("USERS", "ROLE", 32);
        relaxEnumColumn("DOCUMENT_ACCESS", "PERMISSION", 32);
        relaxEnumColumn("DOCUMENT_VERSIONS", "ORIGIN", 40);
        relaxEnumColumn("ACTIVITY_EVENTS", "ACTION", 40);
        relaxNullableColumn("DOCUMENT_VERSIONS", "CONTENT_HASH");
        relaxNullableColumn("DOCUMENT_VERSIONS", "MANIFEST_PATH");
        ensureColumn("DOCUMENTS", "LAST_VIEWED_AT", "TIMESTAMP WITH TIME ZONE");
        ensureColumn("DOCUMENTS", "LAST_VIEWED_BY_NAME", "VARCHAR(255)");
        ensureColumn("DOCUMENTS", "LAST_VIEWED_BY_EMAIL", "VARCHAR(255)");
        ensureColumn("DOCUMENTS", "PROCESSING_STATUS", "VARCHAR(32) DEFAULT 'COMPLETED' NOT NULL");
        ensureColumn("DOCUMENTS", "PROCESSING_MESSAGE", "VARCHAR(512)");
        ensureColumn("DOCUMENTS", "PROCESSING_UPDATED_AT", "TIMESTAMP WITH TIME ZONE");
        ensureColumn("DOCUMENT_VERSIONS", "SOURCE_CONTENT_HASH", "VARCHAR(128)");
        ensureColumn("DOCUMENT_VERSIONS", "TOTAL_CHUNK_COUNT", "INTEGER");
        ensureColumn("DOCUMENT_VERSIONS", "NEW_CHUNK_COUNT", "INTEGER");
        ensureColumn("DOCUMENT_VERSIONS", "REUSED_CHUNK_COUNT", "INTEGER");
        ensureColumn("DOCUMENT_VERSIONS", "NOTE", "VARCHAR(512)");
        ensureColumn("DOCUMENT_VERSIONS", "EXTRACTED_TEXT", "CLOB");
        ensureColumn("DOCUMENT_VERSIONS", "SUMMARY", "CLOB");
        ensureColumn("DOCUMENT_VERSIONS", "TITLE_SUGGESTION", "VARCHAR(512)");
        ensureColumn("DOCUMENT_VERSIONS", "TAGS_JSON", "CLOB");
        ensureColumn("DOCUMENT_VERSIONS", "INSIGHTS_JSON", "CLOB");
        ensureColumn("DOCUMENT_VERSIONS", "HIGHLIGHTS_JSON", "CLOB");
        ensureColumn("DOCUMENT_VERSIONS", "READABILITY_NOTE", "VARCHAR(1024)");
        ensureColumn("DOCUMENT_VERSIONS", "DATES_JSON", "CLOB");
        ensureColumn("DOCUMENT_VERSIONS", "NAMES_JSON", "CLOB");
        ensureColumn("DOCUMENT_VERSIONS", "KEYWORDS_JSON", "CLOB");
        ensureColumn("DOCUMENT_VERSIONS", "OCR_APPLIED", "BOOLEAN DEFAULT FALSE NOT NULL");
        ensureColumn("DOCUMENT_VERSIONS", "PROCESSING_STATUS", "VARCHAR(32) DEFAULT 'COMPLETED' NOT NULL");
        ensureColumn("DOCUMENT_VERSIONS", "PROCESSING_ERROR", "VARCHAR(1024)");
        ensureColumn("DOCUMENT_VERSIONS", "STAGED_CONTENT_PATH", "VARCHAR(2048)");
        ensureColumn("DOCUMENT_VERSIONS", "PROCESSING_RETRY_COUNT", "INTEGER DEFAULT 0 NOT NULL");
        ensureColumn("ACTIVITY_EVENTS", "VERSION_ID", "VARCHAR(255)");
        ensureColumn("ACTIVITY_EVENTS", "VERSION_NUMBER", "INTEGER");
        ensureColumn("PUBLIC_SHARE_LINKS", "EXPIRES_AT", "TIMESTAMP WITH TIME ZONE");
    }

    private void relaxEnumColumn(String tableName, String columnName, int length) {
        if (!tableExists(tableName) || columnExists(tableName, columnName) == 0) return;
        jdbcTemplate.execute("ALTER TABLE \"" + tableName + "\" ALTER COLUMN \"" + columnName + "\" VARCHAR(" + length + ")");
    }

    private void relaxNullableColumn(String tableName, String columnName) {
        if (!tableExists(tableName)) return;
        String isNullable = jdbcTemplate.query("SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?",
            resultSet -> resultSet.next() ? resultSet.getString("IS_NULLABLE") : null, tableName, columnName);
        if (isNullable == null || "YES".equalsIgnoreCase(isNullable)) return;
        jdbcTemplate.execute("ALTER TABLE \"" + tableName + "\" ALTER COLUMN \"" + columnName + "\" DROP NOT NULL");
    }

    private void ensureColumn(String tableName, String columnName, String definition) {
        if (!tableExists(tableName) || columnExists(tableName, columnName) > 0) return;
        jdbcTemplate.execute("ALTER TABLE \"" + tableName + "\" ADD COLUMN \"" + columnName + "\" " + definition);
    }

    private int columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?", Integer.class, tableName, columnName);
        return count == null ? 0 : count;
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?", Integer.class, tableName);
        return count != null && count > 0;
    }
}
