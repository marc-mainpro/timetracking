package com.tfp.timetracking.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.identity.domain.UserRepository;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RepositoryTenantConventionTest {

    private static final Set<String> EXCEPTION_SIGNATURES = Set.of(
            "com.tfp.timetracking.identity.domain.UserRepository#findById(java.util.UUID)",
            "com.tfp.timetracking.identity.domain.UserRepository#findByEmail(com.tfp.timetracking.identity.domain.Email)",
            "com.tfp.timetracking.identity.domain.UserRepository#existsByEmail(com.tfp.timetracking.identity.domain.Email)");

    @Test
    void businessRepositoryMethodsDeclareTenantIdAsFirstParameterUnlessDocumentedException() {
        for (Method method : UserRepository.class.getDeclaredMethods()) {
            String signature = signature(method);
            if (EXCEPTION_SIGNATURES.contains(signature) || method.getName().equals("save")) {
                continue;
            }
            assertThat(method.getParameterCount()).isGreaterThan(0);
            assertThat(method.getParameterTypes()[0])
                    .as("primer parametro tenantId en %s", signature)
                    .isEqualTo(UUID.class);
        }
    }

    private String signature(Method method) {
        String params = java.util.Arrays.stream(method.getParameterTypes()).map(Class::getName).reduce((a, b) -> a + "," + b).orElse("");
        return method.getDeclaringClass().getName() + "#" + method.getName() + "(" + params + ")";
    }
}
