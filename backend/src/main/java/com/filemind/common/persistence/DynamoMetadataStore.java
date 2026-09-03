package com.filemind.common.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Metadata persistence backed by the existing FileMindMetadata DynamoDB table. */
@Component
public class DynamoMetadataStore {
    private static final String KEY = "fileId";
    private static final String TYPE = "entityType";
    private static final String PAYLOAD = "payload";

    private final DynamoDbClient dynamoDbClient;
    private final ObjectMapper objectMapper;
    private final String tableName;
    private final boolean inMemory;
    private final Map<String, Map<String, AttributeValue>> inMemoryItems = new ConcurrentHashMap<>();
    // The existing table only has fileId as a key. A brief read-through cache
    // avoids scanning that entire table once for every metadata type in one API request.
    private final Map<String, List<Map<String, AttributeValue>>> itemCache = new ConcurrentHashMap<>();
    private volatile long cacheExpiresAt = 0L;

    public DynamoMetadataStore(DynamoDbClient dynamoDbClient, ObjectMapper objectMapper,
                               @Value("${aws.dynamodb.table-name}") String tableName,
                               @Value("${filemind.metadata.in-memory:false}") boolean inMemory) {
        this.dynamoDbClient = dynamoDbClient;
        this.objectMapper = objectMapper;
        this.tableName = tableName;
        this.inMemory = inMemory;
    }

    public <T> T save(T entity, Class<T> type) {
        String id = idOf(entity);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
            setId(entity, id);
            invokeLifecycle(entity, PrePersist.class);
        } else if (find(id, type).isPresent()) {
            invokeLifecycle(entity, PreUpdate.class);
        } else {
            invokeLifecycle(entity, PrePersist.class);
        }
        try {
            Map<String, AttributeValue> item = Map.of(
                KEY, AttributeValue.fromS(id),
                TYPE, AttributeValue.fromS(type.getName()),
                PAYLOAD, AttributeValue.fromS(objectMapper.writeValueAsString(entity))
            );
            if (inMemory) {
                inMemoryItems.put(id, item);
            } else {
                dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(item).build());
            }
            invalidateCache();
            return entity;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize " + type.getSimpleName() + " metadata", exception);
        }
    }

    public <T> Optional<T> find(String id, Class<T> type) {
        if (inMemory) {
            Map<String, AttributeValue> item = inMemoryItems.get(id);
            return item == null || !type.getName().equals(value(item.get(TYPE)))
                ? Optional.empty()
                : Optional.of(deserialize(item.get(PAYLOAD), type));
        }
        Map<String, AttributeValue> cached = cachedItem(id, type);
        if (cached != null) return Optional.of(deserialize(cached.get(PAYLOAD), type));
        var response = dynamoDbClient.getItem(GetItemRequest.builder().tableName(tableName)
            .key(Map.of(KEY, AttributeValue.fromS(id))).consistentRead(true).build());
        if (!response.hasItem() || !type.getName().equals(value(response.item().get(TYPE)))) return Optional.empty();
        return Optional.of(deserialize(response.item().get(PAYLOAD), type));
    }

    public <T> List<T> findAll(Class<T> type) {
        if (inMemory) {
            return inMemoryItems.values().stream()
                .filter(item -> type.getName().equals(value(item.get(TYPE))))
                .map(item -> deserialize(item.get(PAYLOAD), type))
                .toList();
        }
        refreshCacheIfNeeded();
        return itemCache.getOrDefault(type.getName(), List.of()).stream()
            .map(item -> deserialize(item.get(PAYLOAD), type))
            .toList();
    }

    private synchronized void refreshCacheIfNeeded() {
        if (System.currentTimeMillis() < cacheExpiresAt) return;
        Map<String, List<Map<String, AttributeValue>>> refreshed = new ConcurrentHashMap<>();
        Map<String, AttributeValue> cursor = null;
        do {
            var request = ScanRequest.builder().tableName(tableName).consistentRead(true);
            if (cursor != null && !cursor.isEmpty()) request.exclusiveStartKey(cursor);
            var response = dynamoDbClient.scan(request.build());
            response.items().forEach(item -> refreshed
                .computeIfAbsent(value(item.get(TYPE)), ignored -> new ArrayList<>())
                .add(item));
            cursor = response.lastEvaluatedKey();
        } while (cursor != null && !cursor.isEmpty());
        itemCache.clear();
        itemCache.putAll(refreshed);
        cacheExpiresAt = System.currentTimeMillis() + 15_000L;
    }

    public void delete(String id) {
        if (inMemory) {
            inMemoryItems.remove(id);
            return;
        }
        dynamoDbClient.deleteItem(DeleteItemRequest.builder().tableName(tableName)
            .key(Map.of(KEY, AttributeValue.fromS(id))).build());
        invalidateCache();
    }

    private Map<String, AttributeValue> cachedItem(String id, Class<?> type) {
        if (System.currentTimeMillis() >= cacheExpiresAt) return null;
        return itemCache.getOrDefault(type.getName(), List.of()).stream()
            .filter(item -> id.equals(value(item.get(KEY)))).findFirst().orElse(null);
    }

    private void invalidateCache() { cacheExpiresAt = 0L; itemCache.clear(); }

    private <T> T deserialize(AttributeValue payload, Class<T> type) {
        try { return objectMapper.readValue(payload.s(), type); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to deserialize " + type.getSimpleName() + " metadata", exception); }
    }

    private String idOf(Object entity) {
        try {
            return (String) readField(entity, "id");
        } catch (IllegalStateException ignored) {
            // Stored chunks are content-addressed; their SHA-256 hash is their DynamoDB key.
            return (String) readField(entity, "hash");
        }
    }
    private String value(AttributeValue attribute) { return attribute == null ? null : attribute.s(); }
    private Object readField(Object entity, String name) { try { return field(entity, name).get(entity); } catch (IllegalAccessException e) { throw new IllegalStateException(e); } }
    private void setId(Object entity, String id) { try { field(entity, "id").set(entity, id); } catch (IllegalAccessException e) { throw new IllegalStateException(e); } }
    private Field field(Object entity, String name) { try { Field f = entity.getClass().getDeclaredField(name); f.setAccessible(true); return f; } catch (ReflectiveOperationException e) { throw new IllegalStateException("Missing metadata field " + name, e); } }
    private void invokeLifecycle(Object entity, Class<? extends java.lang.annotation.Annotation> annotation) {
        for (Method method : entity.getClass().getDeclaredMethods()) if (method.isAnnotationPresent(annotation)) {
            try { method.setAccessible(true); method.invoke(entity); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException("Metadata lifecycle failed", e); }
        }
    }
}
