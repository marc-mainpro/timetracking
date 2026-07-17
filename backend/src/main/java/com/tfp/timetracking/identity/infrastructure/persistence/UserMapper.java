package com.tfp.timetracking.identity.infrastructure.persistence;

import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserStatus;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mapper dominio &lt;-&gt; JPA para User (CONTEXT-GLOBAL §4: entidades JPA
 * separadas del modelo de dominio).
 */
final class UserMapper {

    private UserMapper() {}

    static UserJpaEntity toJpaEntity(User user) {
        Set<String> roleNames = user.roles().stream().map(Enum::name).collect(Collectors.toSet());
        return new UserJpaEntity(
                user.id(),
                user.tenantId(),
                user.email().value(),
                user.passwordHash(),
                user.firstName(),
                user.lastName(),
                user.status().name(),
                roleNames,
                user.createdAt(),
                user.updatedAt());
    }

    static User toDomain(UserJpaEntity entity) {
        Set<Role> roles = entity.getRoles().stream().map(Role::valueOf).collect(Collectors.toSet());
        return User.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getFirstName(),
                entity.getLastName(),
                UserStatus.valueOf(entity.getStatus()),
                roles,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
