package com.filemind.settings.repository;

import com.filemind.settings.entity.UserPreference;
import com.filemind.common.persistence.DynamoMetadataStore;
import com.filemind.common.persistence.DynamoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserPreferenceRepository extends DynamoRepository<UserPreference> {
    public UserPreferenceRepository(DynamoMetadataStore store) { super(store, UserPreference.class); }
    @Override protected String id(UserPreference preference) { return preference.getId(); }

    public Optional<UserPreference> findByUserId(String userId) { return findAll().stream().filter(p -> userId.equals(p.getUser().getId())).findFirst(); }
}
