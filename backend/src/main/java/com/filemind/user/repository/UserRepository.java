package com.filemind.user.repository;

import com.filemind.user.entity.User;
import com.filemind.common.persistence.DynamoMetadataStore;
import com.filemind.common.persistence.DynamoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository extends DynamoRepository<User> {

    public UserRepository(DynamoMetadataStore store) {
        super(store, User.class);
    }

    @Override
    protected String id(User user) {
        return user.getId();
    }

    public Optional<User> findByEmailIgnoreCase(String email) {
        return findAll().stream().filter(user -> user.getEmail().equalsIgnoreCase(email)).findFirst();
    }

    public boolean existsByEmailIgnoreCase(String email) {
        return findByEmailIgnoreCase(email).isPresent();
    }
}

