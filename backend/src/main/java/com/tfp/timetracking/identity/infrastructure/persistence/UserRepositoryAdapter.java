package com.tfp.timetracking.identity.infrastructure.persistence;

import com.tfp.timetracking.identity.domain.Email;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de infraestructura que implementa el puerto
 * {@link UserRepository} usando Spring Data JPA (CONTEXT-GLOBAL §4:
 * infrastructure implementa puertos definidos en domain).
 */
@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;

    public UserRepositoryAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public User save(User user) {
        UserJpaEntity saved = jpaRepository.save(UserMapper.toJpaEntity(user));
        return UserMapper.toDomain(saved);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByTenantIdAndEmail(UUID tenantId, Email email) {
        return jpaRepository.findByTenantIdAndEmail(tenantId, email.value()).map(UserMapper::toDomain);
    }

    @Override
    public boolean existsByTenantIdAndEmail(UUID tenantId, Email email) {
        return jpaRepository.existsByTenantIdAndEmail(tenantId, email.value());
    }
}
