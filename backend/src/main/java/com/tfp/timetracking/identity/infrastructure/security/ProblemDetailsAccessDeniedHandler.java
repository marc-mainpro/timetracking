package com.tfp.timetracking.identity.infrastructure.security;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class ProblemDetailsAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailsAuthenticationEntryPoint problemWriter;

    public ProblemDetailsAccessDeniedHandler(ProblemDetailsAuthenticationEntryPoint problemWriter) {
        this.problemWriter = problemWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        problemWriter.writeProblem(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden", "FORBIDDEN", "Acceso denegado");
    }
}
