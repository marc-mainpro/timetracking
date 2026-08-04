package com.tfp.timetracking.tenant.application;

import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import com.tfp.timetracking.tenant.domain.Tenant;
import com.tfp.timetracking.tenant.domain.TenantRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recupera el detalle de un tenant para la administración de plataforma
 * (RF-TEN-002, T50-05). El tenant de sistema no es consultable como tenant de
 * negocio.
 */
@Service
public class GetTenantUseCase {

    private final TenantRepository tenantRepository;

    public GetTenantUseCase(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public Tenant get(UUID tenantId) {
        if (PlatformTenant.ID.equals(tenantId)) {
            throw new ResourceNotFoundException("Tenant no encontrado");
        }
        return tenantRepository
                .findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));
    }
}
