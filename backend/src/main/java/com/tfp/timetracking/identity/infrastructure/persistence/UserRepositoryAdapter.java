package com.tfp.timetracking.identity.infrastructure.persistence;

import com.tfp.timetracking.identity.domain.Email;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.identity.domain.UserStatus;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    public Optional<User> findById(UUID tenantId, UUID id) {
        return jpaRepository.findByTenantIdAndId(tenantId, id).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return jpaRepository.findByEmail(email.value()).map(UserMapper::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpaRepository.existsByEmail(email.value());
    }

    @Override
    public List<User> findAllByTenantId(UUID tenantId) {
        return jpaRepository.findAllByTenantId(tenantId).stream().map(UserMapper::toDomain).toList();
    }

    @Override
    public PagedResult<User> findByTenant(UUID tenantId, UserStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "email"));
        Page<UserJpaEntity> result = status == null
                ? jpaRepository.findByTenantId(tenantId, pageRequest)
                : jpaRepository.findByTenantIdAndStatus(tenantId, status.name(), pageRequest);
        return new PagedResult<>(
                result.getContent().stream().map(UserMapper::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public long countActiveAdmins(UUID tenantId) {
        return jpaRepository.countActiveAdmins(tenantId);
    }

    @Override
    public long countActiveAdminsExcludingUser(UUID tenantId, UUID userId) {
        return jpaRepository.countActiveAdminsExcludingUser(tenantId, userId);
    }
}
