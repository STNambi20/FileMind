package com.filemind;

import com.filemind.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FileMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileMindApplication.class, args);
    }
}
