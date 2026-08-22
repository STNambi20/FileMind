package com.filemind.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class JsonListCodec {

    private final ObjectMapper objectMapper;

    public List<String> read(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
                })
                .stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        } catch (Exception exception) {
            return Collections.emptyList();
        }
    }

    public String write(List<String> values) {
        try {
            List<String> normalized = values == null
                ? Collections.emptyList()
                : values.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList();
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception exception) {
            return "[]";
        }
    }
}
