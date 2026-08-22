package com.filemind.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "filemind.jwt")
public class JwtProperties {

    private String secret;
    private long expirationMs;
}

