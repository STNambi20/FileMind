package com.filemind.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * DynamoDB writes are performed by the metadata store rather than JPA. This
 * lightweight transaction boundary keeps Spring's @Transactional service
 * semantics and AFTER_COMMIT upload events without reintroducing H2/JPA.
 */
@Configuration
public class MetadataTransactionConfig {

    @Bean
    public PlatformTransactionManager transactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
                // Individual DynamoDB writes are immediately durable.
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
                // Nothing to commit: persistence occurs inside DynamoMetadataStore.
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
                // Compensating cleanup is handled by the calling services where needed.
            }
        };
    }
}
