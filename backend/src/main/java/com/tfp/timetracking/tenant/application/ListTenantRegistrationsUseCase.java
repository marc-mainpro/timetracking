package com.tfp.timetracking.tenant.application;

import com.tfp.timetracking.shared.domain.PagedResult;
import com.tfp.timetracking.tenant.domain.TenantRegistration;
import com.tfp.timetracking.tenant.domain.TenantRegistrationRepository;
import com.tfp.timetracking.tenant.domain.TenantRegistrationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listado paginado de solicitudes de alta para la revisión de plataforma
 * (T53-03). Solo lo consume {@code PLATFORM_ADMIN}.
 */
@Service
public class ListTenantRegistrationsUseCase {

    private final TenantRegistrationRepository registrationRepository;

    public ListTenantRegistrationsUseCase(TenantRegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    /**
     * @param status nombre del estado por el que filtrar, o {@code null} para
     *     no filtrar. Se recibe como cadena y se traduce aquí a propósito: así
     *     el controlador no necesita conocer el enum de dominio (ver
     *     {@code LayeredArchitectureTest}). Un nombre desconocido produce
     *     {@link IllegalArgumentException}, que el borde traduce a 400.
     */
    @Transactional(readOnly = true)
    public PagedResult<TenantRegistration> list(String status, int page, int size) {
        TenantRegistrationStatus filter = status == null || status.isBlank()
                ? null
                : TenantRegistrationStatus.valueOf(status);
        return registrationRepository.findAll(filter, page, size);
    }
}
