package com.tfp.timetracking.shared.infrastructure.security;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RouteAuthorizationIntegrationTest {

    private static final Set<String> PUBLIC_ROUTES = Set.of(
            "POST /api/v1/auth/login",
            "POST /api/v1/auth/refresh",
            "POST /api/v1/auth/register",
            "GET /actuator/health",
            "GET /v3/api-docs",
            "GET /swagger-ui.html");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("timetracking")
            .withUsername("timetracking")
            .withPassword("timetracking");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void everyNonPublicApplicationRouteRequiresAuthentication() throws Exception {
        List<Route> routes = requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(this::routes)
                .filter(route -> route.path().startsWith("/api/") || route.path().startsWith("/actuator/"))
                .filter(route -> !route.path().startsWith("/v3/api-docs"))
                .filter(route -> !route.path().startsWith("/swagger-ui"))
                .filter(route -> !route.path().startsWith("/error"))
                .sorted(Comparator.comparing(Route::signature))
                .toList();

        for (Route route : routes) {
            if (PUBLIC_ROUTES.contains(route.signature()) || route.path().startsWith("/actuator/health")) {
                continue;
            }
            mockMvc.perform(route.toRequest())
                    .andExpect(status().isUnauthorized());
        }
    }

    private java.util.stream.Stream<Route> routes(RequestMappingInfo mappingInfo) {
        Set<String> patterns = mappingInfo.getPatternValues();
        Set<RequestMethod> configuredMethods = mappingInfo.getMethodsCondition().getMethods();
        Set<RequestMethod> methods = configuredMethods.isEmpty() ? Set.of(RequestMethod.GET) : configuredMethods;
        return patterns.stream().flatMap(pattern -> methods.stream().map(method -> new Route(method.name(), normalize(pattern))));
    }

    private String normalize(String pattern) {
        return pattern
                .replaceAll("\\{[^/]+Id}", "00000000-0000-0000-0000-000000000001")
                .replaceAll("\\{[^/]+}", "value");
    }

    private record Route(String method, String path) {
        String signature() {
            return method + " " + path;
        }

        MockHttpServletRequestBuilder toRequest() {
            return switch (method) {
                case "GET" -> org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path);
                case "POST" -> org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path);
                case "PUT" -> org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path);
                case "PATCH" -> org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(path);
                case "DELETE" -> org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path);
                case "OPTIONS" -> org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options(path);
                default -> throw new IllegalArgumentException("Metodo no soportado: " + method);
            };
        }
    }

}
