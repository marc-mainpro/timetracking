package com.tfp.timetracking.identity.infrastructure.persistence;

import com.tfp.timetracking.identity.domain.TenantAccessRepository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TenantAccessRepositoryAdapter implements TenantAccessRepository {

    private final JdbcTemplate jdbcTemplate;

    public TenantAccessRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isActive(UUID tenantId) {
        Boolean active = jdbcTemplate.query(
                "SELECT status = 'ACTIVE' FROM tenant WHERE id = ?",
                resultSet -> resultSet.next() && resultSet.getBoolean(1),
                tenantId);
        return Boolean.TRUE.equals(active);
    }
}
