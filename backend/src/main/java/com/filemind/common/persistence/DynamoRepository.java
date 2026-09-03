package com.filemind.common.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public abstract class DynamoRepository<T> {
    protected final DynamoMetadataStore store;
    private final Class<T> type;
    protected DynamoRepository(DynamoMetadataStore store, Class<T> type) { this.store = store; this.type = type; }
    public T save(T entity) { return store.save(entity, type); }
    public List<T> saveAll(Collection<T> entities) { return entities.stream().map(this::save).toList(); }
    public Optional<T> findById(String id) { return store.find(id, type); }
    public List<T> findAll() { return store.findAll(type); }
    public long count() { return findAll().size(); }
    public void delete(T entity) { store.delete(id(entity)); }
    public void deleteAll(Collection<T> entities) { entities.forEach(this::delete); }
    protected abstract String id(T entity);
}
