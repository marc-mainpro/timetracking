package com.tfp.timetracking.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Todo endpoint de administración o de plataforma exige un rol explícito
 * (RS-010, A01 de OWASP).
 *
 * <p>La cadena de seguridad solo garantiza {@code anyRequest().authenticated()}:
 * un controlador nuevo bajo {@code /admin/**} o {@code /platform/**} al que se
 * le olvide el {@code @PreAuthorize} queda abierto a <b>cualquier usuario
 * autenticado</b>, incluido un EMPLOYEE de otro tenant. Es un descuido barato
 * de cometer y caro de detectar: la ruta responde 200 y nada falla.
 *
 * <p>Se comprueba sobre las anotaciones y no levantando el contexto de Spring
 * para que sea una prueba rápida: no necesita saber a qué handler resuelve
 * cada ruta, solo que el controlador que la declara pide un rol.
 *
 * <p>No cubre los endpoints de recurso propio ({@code /api/v1/notifications},
 * {@code /api/v1/auth/sessions}...), que a propósito no llevan rol: los acota
 * el usuario del principal, no una autoridad. Esa propiedad la verifican sus
 * pruebas de integración con un segundo usuario del mismo tenant.
 */
class PrivilegedEndpointsRequireRoleTest {

    private static final String BASE_PACKAGE = "com.tfp.timetracking";

    @Test
    void adminAndPlatformControllersDeclareARequiredRole() {
        List<String> offenders = new ArrayList<>();
        List<String> inspected = new ArrayList<>();

        for (Class<?> controller : restControllers()) {
            String basePath = basePathOf(controller);
            if (!isPrivileged(basePath)) {
                continue;
            }
            inspected.add(controller.getSimpleName());
            if (AnnotatedElementUtils.hasAnnotation(controller, PreAuthorize.class)) {
                continue;
            }
            for (Method method : controller.getDeclaredMethods()) {
                if (isRequestMapping(method) && !AnnotatedElementUtils.hasAnnotation(method, PreAuthorize.class)) {
                    offenders.add(controller.getSimpleName() + "#" + method.getName() + " (" + basePath + ")");
                }
            }
        }

        // Sin esta comprobación la regla podría pasar por vacío —si el escaneo
        // dejara de encontrar controladores— y daría una falsa sensación de
        // cobertura justo donde más cara sale.
        assertThat(inspected)
                .as("La regla debe estar examinando los controladores privilegiados")
                .hasSizeGreaterThanOrEqualTo(8);

        assertThat(offenders)
                .as("Endpoints bajo /admin o /platform sin @PreAuthorize: quedarían abiertos a "
                        + "cualquier usuario autenticado")
                .isEmpty();
    }

    private static boolean isPrivileged(String basePath) {
        return basePath.contains("/admin") || basePath.contains("/platform");
    }

    private static String basePathOf(Class<?> controller) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        if (mapping == null || mapping.value().length == 0) {
            return "";
        }
        return mapping.value()[0];
    }

    private static boolean isRequestMapping(Method method) {
        for (Annotation annotation : method.getAnnotations()) {
            if (AnnotatedElementUtils.hasAnnotation(annotation.annotationType(), RequestMapping.class)
                    || annotation.annotationType().equals(RequestMapping.class)) {
                return true;
            }
        }
        return false;
    }

    private static List<Class<?>> restControllers() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> controllers = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            try {
                controllers.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("No se pudo cargar " + definition.getBeanClassName(), e);
            }
        }
        assertThat(controllers).as("El escaneo debe encontrar controladores").isNotEmpty();
        return controllers;
    }
}
