package com.tfp.timetracking.reporting.infrastructure;

import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.reporting.domain.EmployeeDirectoryPort;
import com.tfp.timetracking.reporting.domain.EmployeeName;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Implementa {@link EmployeeDirectoryPort} sobre {@code identity.domain.UserRepository}.
 *
 * <p>Depende del puerto de dominio de {@code identity}, no de su
 * infraestructura JPA: {@code LayeredArchitectureTest} prohibe que cualquier
 * capa dependa de {@code infrastructure} de otro modulo, y esto es exactamente
 * el mismo patron de cableado que usa Spring para inyectar cualquier otro
 * puerto.
 */
@Component
public class UserEmployeeDirectoryAdapter implements EmployeeDirectoryPort {

    private final UserRepository userRepository;

    public UserEmployeeDirectoryAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Map<UUID, EmployeeName> namesByTenant(UUID tenantId) {
        return userRepository.findAllByTenantId(tenantId).stream()
                .collect(Collectors.toMap(User::id, user -> new EmployeeName(user.firstName(), user.lastName())));
    }
}
