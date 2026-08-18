package com.tfp.timetracking.identity.application;

import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.identity.domain.UserStatus;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.PagedResult;
import org.springframework.stereotype.Service;

@Service
public class ListEmployeesUseCase {

    private final UserRepository userRepository;
    private final TenantContext tenantContext;

    public ListEmployeesUseCase(UserRepository userRepository, TenantContext tenantContext) {
        this.userRepository = userRepository;
        this.tenantContext = tenantContext;
    }

    /**
     * @param status estado por el que acotar, o {@code null} para no acotar
     * @param role nombre del rol por el que acotar, o {@code null} para
     *     devolver todos los usuarios del tenant. El listado sin filtro es lo
     *     que consume la gestion de usuarios; el filtro por {@code EMPLOYEE} es
     *     lo que consumen los desplegables de asignacion, que no deben ofrecer
     *     a un administrador que no ficha.
     * @throws IllegalArgumentException si el rol no existe o no es asignable
     *     dentro de un tenant: filtrar por un rol de plataforma no tiene
     *     significado aqui y no debe parecer una consulta valida que no
     *     encuentra a nadie.
     */
    public PagedResult<User> list(int page, int size, UserStatus status, String role) {
        return userRepository.findByTenant(tenantContext.currentTenantId(), status, parseRole(role), page, size);
    }

    private static Role parseRole(String role) {
        if (role == null) {
            return null;
        }
        Role parsed = Role.valueOf(role);
        if (!parsed.isAssignableWithinTenant()) {
            throw new IllegalArgumentException("Rol no filtrable dentro de un tenant: " + role);
        }
        return parsed;
    }
}
