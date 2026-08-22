package com.filemind.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "filemind.storage")
public class StorageProperties {

    private String root = "storage";
    private String uploads = "storage/uploads";
    private String chunks = "storage/chunks";
    private String manifests = "storage/manifests";
    private String chunkingStrategy = "content-defined";
    private int chunkSize = 4096;
    private int cdcMinChunkSize = 2048;
    private int cdcAvgChunkSize = 4096;
    private int cdcMaxChunkSize = 8192;
}
