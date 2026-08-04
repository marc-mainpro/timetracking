package com.tfp.timetracking.shared.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.shared.application.ObservabilityContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.method.HandlerMethod;

class RequestObservabilityInterceptorTest {

    private static final String TENANT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String USER_ID = "22222222-2222-2222-2222-222222222222";

    private final RequestObservabilityInterceptor interceptor = new RequestObservabilityInterceptor();

    @AfterEach
    void reset() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void publishesTenantUserAndUseCaseOfAnAuthenticatedRequest() throws Exception {
        authenticateWithJwt();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/employees");

        interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod());

        assertThat(MDC.get(ObservabilityContext.TENANT_ID)).isEqualTo(TENANT_ID);
        assertThat(MDC.get(ObservabilityContext.USER_ID)).isEqualTo(USER_ID);
        assertThat(MDC.get(ObservabilityContext.USE_CASE)).isEqualTo("SampleController#list");
    }

    @Test
    void leavesTenantAndUserEmptyOnAnonymousRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");

        interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod());

        assertThat(MDC.get(ObservabilityContext.TENANT_ID)).isNull();
        assertThat(MDC.get(ObservabilityContext.USER_ID)).isNull();
        assertThat(MDC.get(ObservabilityContext.USE_CASE)).isEqualTo("SampleController#list");
    }

    @Test
    void ignoresNonJwtAuthentications() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("someone", "credentials", "ROLE_EMPLOYEE"));

        interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/v1/employees"), new MockHttpServletResponse(), handlerMethod());

        assertThat(MDC.get(ObservabilityContext.TENANT_ID)).isNull();
        assertThat(MDC.get(ObservabilityContext.USER_ID)).isNull();
    }

    @Test
    void fallsBackToMethodAndPathWhenNoHandlerMethodResolved() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(MDC.get(ObservabilityContext.USE_CASE)).isEqualTo("GET /actuator/health");
    }

    @Test
    void marksTheResultAsSuccessFor2xx() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        interceptor.afterCompletion(new MockHttpServletRequest("GET", "/api/v1/employees"), response, null, null);

        assertThat(MDC.get(ObservabilityContext.RESULT)).isEqualTo(ObservabilityContext.RESULT_SUCCESS);
    }

    /** Un 4xx es un desenlace esperado del negocio, no un fallo del sistema. */
    @Test
    void marksClientErrorsAsSuccessAndServerErrorsAsFailure() {
        MockHttpServletResponse clientError = new MockHttpServletResponse();
        clientError.setStatus(409);
        interceptor.afterCompletion(new MockHttpServletRequest("POST", "/api/v1/workdays"), clientError, null, null);
        assertThat(MDC.get(ObservabilityContext.RESULT)).isEqualTo(ObservabilityContext.RESULT_SUCCESS);

        MockHttpServletResponse serverError = new MockHttpServletResponse();
        serverError.setStatus(500);
        interceptor.afterCompletion(new MockHttpServletRequest("POST", "/api/v1/workdays"), serverError, null, null);
        assertThat(MDC.get(ObservabilityContext.RESULT)).isEqualTo(ObservabilityContext.RESULT_FAILURE);
    }

    @Test
    void marksTheResultAsFailureWhenTheHandlerThrew() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        interceptor.afterCompletion(
                new MockHttpServletRequest("GET", "/api/v1/employees"), response, null, new IllegalStateException());

        assertThat(MDC.get(ObservabilityContext.RESULT)).isEqualTo(ObservabilityContext.RESULT_FAILURE);
    }

    /**
     * RS-014: ni la cabecera {@code Authorization}, ni {@code Cookie}, ni la
     * query string llegan nunca al contexto de diagnostico.
     */
    @Test
    void neverPublishesCredentialsCarriedByTheRequest() throws Exception {
        authenticateWithJwt();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/employees");
        request.addHeader("Authorization", "Bearer token-secreto");
        request.addHeader("Cookie", "refresh_token=cookie-secreta");
        request.setQueryString("password=hunter2");

        interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod());
        Map<String, String> context = MDC.getCopyOfContextMap();

        assertThat(context).isNotNull();
        assertThat(context.keySet())
                .containsExactlyInAnyOrder(
                        ObservabilityContext.TENANT_ID, ObservabilityContext.USER_ID, ObservabilityContext.USE_CASE);
        assertThat(context.values())
                .noneMatch(value -> value.contains("token-secreto")
                        || value.contains("cookie-secreta")
                        || value.contains("hunter2"));
    }

    private void authenticateWithJwt() {
        Jwt jwt = Jwt.withTokenValue("token-secreto")
                .header("alg", "HS256")
                .subject(USER_ID)
                .claim("tenantId", TENANT_ID)
                .claim("roles", List.of("EMPLOYEE"))
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));
    }

    private HandlerMethod handlerMethod() throws NoSuchMethodException {
        return new HandlerMethod(new SampleController(), SampleController.class.getMethod("list"));
    }

    static class SampleController {
        public void list() {
        }
    }
}
