package com.filemind.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@RequiredArgsConstructor
public class StorageBootstrapConfig {

    private final StorageProperties storageProperties;

    @PostConstruct
    public void ensureDirectoriesExist() throws IOException {
        Path root = Path.of(storageProperties.getRoot());
        Path uploads = Path.of(storageProperties.getUploads());
        Path chunks = Path.of(storageProperties.getChunks());
        Path manifests = Path.of(storageProperties.getManifests());
        Path pdfBoxCache = root.resolve("cache").resolve("pdfbox");

        Files.createDirectories(root);
        Files.createDirectories(uploads);
        Files.createDirectories(chunks);
        Files.createDirectories(manifests);
        Files.createDirectories(pdfBoxCache);

        System.setProperty("pdfbox.fontcache", pdfBoxCache.toAbsolutePath().toString());
    }
}
