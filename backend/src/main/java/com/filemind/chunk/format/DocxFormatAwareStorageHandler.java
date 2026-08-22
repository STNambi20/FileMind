package com.filemind.chunk.format;

import com.filemind.chunk.model.ManifestChunk;
import com.filemind.chunk.model.ManifestPart;
import com.filemind.chunk.model.ManifestStorageMode;
import com.filemind.chunk.model.StoredManifest;
import com.filemind.chunk.util.ChunkHashUtils;
import com.filemind.common.util.FileTypeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DocxFormatAwareStorageHandler implements FileFormatHandler {

    @Override
    public boolean supportsUpload(String fileName, String mediaType) {
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mediaType)
            || "docx".equalsIgnoreCase(FileTypeUtils.fileExtension(fileName));
    }

    @Override
    public boolean supportsManifest(StoredManifest manifest) {
        return manifest.storageMode() == ManifestStorageMode.DOCX_PACKAGE;
    }

    @Override
    public StoredManifest createManifest(String fileName,
                                         String mediaType,
                                         Path filePath,
                                         FormatChunkStore chunkStore) throws IOException {
        Path tempDirectory = Files.createTempDirectory("filemind-docx-parts-");
        List<ManifestPart> parts = new ArrayList<>();
        int order = 0;

        try (InputStream inputStream = Files.newInputStream(filePath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    parts.add(new ManifestPart(
                        entry.getName(),
                        order++,
                        true,
                        false,
                        0L,
                        entry.getTime() >= 0 ? entry.getTime() : null,
                        null,
                        List.of()
                    ));
                    zipInputStream.closeEntry();
                    continue;
                }

                Path stagedPart = tempDirectory.resolve("part-" + order + resolveSuffix(entry.getName()));
                try (var outputStream = Files.newOutputStream(stagedPart)) {
                    zipInputStream.transferTo(outputStream);
                }
                zipInputStream.closeEntry();

                boolean chunked = shouldChunkEntry(entry.getName(), Files.size(stagedPart));
                List<ManifestChunk> chunks = chunked
                    ? chunkStore.storeChunked(stagedPart)
                    : List.of(chunkStore.storeWhole(stagedPart));

                parts.add(new ManifestPart(
                    entry.getName(),
                    order++,
                    false,
                    chunked,
                    Files.size(stagedPart),
                    entry.getTime() >= 0 ? entry.getTime() : null,
                    ChunkHashUtils.sha256(stagedPart),
                    chunks
                ));

                Files.deleteIfExists(stagedPart);
            }
        } finally {
            try (var walk = Files.walk(tempDirectory)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        }

        return new StoredManifest(
            canonicalPackageHash(parts),
            fileName,
            mediaType,
            Files.size(filePath),
            ManifestStorageMode.DOCX_PACKAGE,
            parts
        );
    }

    @Override
    public byte[] reconstruct(StoredManifest manifest, ManifestChunkReader chunkReader) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        List<ManifestPart> orderedParts = manifest.parts().stream()
            .sorted(Comparator.comparingInt(ManifestPart::order))
            .toList();

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (ManifestPart part : orderedParts) {
                ZipEntry entry = new ZipEntry(part.name());
                if (part.modifiedTimeEpochMillis() != null) {
                    entry.setTime(part.modifiedTimeEpochMillis());
                }
                zipOutputStream.putNextEntry(entry);
                if (!part.directory()) {
                    byte[] content = reconstructPart(part, chunkReader);
                    zipOutputStream.write(content);
                }
                zipOutputStream.closeEntry();
            }
        }

        String rebuiltCanonicalHash = canonicalPackageHash(orderedParts);
        if (!rebuiltCanonicalHash.equals(manifest.contentHash())) {
            throw new IllegalStateException("Rebuilt DOCX content failed canonical package verification");
        }
        return outputStream.toByteArray();
    }

    private byte[] reconstructPart(ManifestPart part, ManifestChunkReader chunkReader) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        List<ManifestChunk> orderedChunks = part.chunks().stream()
            .sorted(Comparator.comparingInt(ManifestChunk::order))
            .toList();

        for (int index = 0; index < orderedChunks.size(); index += 1) {
            ManifestChunk chunk = orderedChunks.get(index);
            if (chunk.order() != index) {
                throw new IllegalStateException("DOCX part chunk order is invalid for " + part.name());
            }
            outputStream.writeBytes(chunkReader.readChunk(chunk));
        }

        byte[] content = outputStream.toByteArray();
        if (content.length != part.logicalSize()) {
            throw new IllegalStateException("DOCX part size does not match manifest metadata for " + part.name());
        }
        if (part.contentHash() != null && !part.contentHash().equals(ChunkHashUtils.sha256(content))) {
            throw new IllegalStateException("DOCX part failed integrity verification for " + part.name());
        }
        return content;
    }

    private boolean shouldChunkEntry(String entryName, long sizeBytes) {
        if (sizeBytes <= 0L) {
            return false;
        }
        String normalizedName = entryName.toLowerCase();
        return normalizedName.endsWith(".xml")
            || normalizedName.endsWith(".rels")
            || normalizedName.endsWith(".txt");
    }

    private String canonicalPackageHash(List<ManifestPart> parts) {
        var digest = ChunkHashUtils.newSha256Digest();
        digest.update("DOCX-PACKAGE".getBytes(StandardCharsets.UTF_8));
        parts.stream()
            .sorted(Comparator.comparing(ManifestPart::name))
            .forEach(part -> {
                updateDigest(digest, part.name());
                updateDigest(digest, part.directory() ? "DIR" : "FILE");
                updateDigest(digest, part.contentHash() == null ? "" : part.contentHash());
            });
        return ChunkHashUtils.toHex(digest.digest());
    }

    private void updateDigest(java.security.MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private String resolveSuffix(String entryName) {
        int lastDot = entryName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == entryName.length() - 1) {
            return ".bin";
        }
        return entryName.substring(lastDot);
    }
}
