package com.filemind.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "filemind.ai")
public class AiServiceProperties {

    private String baseUrl;
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 10000;
    private int maxInputCharacters = 40000;
}
