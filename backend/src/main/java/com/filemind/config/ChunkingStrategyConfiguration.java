package com.filemind.config;

import com.filemind.chunk.strategy.ChunkingStrategy;
import com.filemind.chunk.strategy.ContentDefinedChunkingStrategy;
import com.filemind.chunk.strategy.FixedSizeChunkingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChunkingStrategyConfiguration {

    @Bean
    public ChunkingStrategy chunkingStrategy(StorageProperties storageProperties) {
        if ("fixed-size".equalsIgnoreCase(storageProperties.getChunkingStrategy())) {
            return new FixedSizeChunkingStrategy(storageProperties);
        }
        return new ContentDefinedChunkingStrategy(storageProperties);
    }
}
