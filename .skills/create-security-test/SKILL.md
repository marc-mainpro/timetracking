# Skill: create-security-test

## Objetivo

Escribir la prueba que fija una propiedad de seguridad antes de darla por
cumplida. Aplica a autorización, aislamiento entre tenants, enumeración,
limitación de peticiones y filtrado de secretos.

La regla de fondo: una propiedad de seguridad que nadie comprueba de forma
automática es una intención, no una garantía. Se pierde en la primera
refactorización y nadie se entera hasta que alguien la explota.

## Entradas

- Requisito de seguridad afectado (`RS-*` de `requisitos-v2-control-horario.md`).
- Endpoint, caso de uso o consulta a proteger.
- `docs/security/threat-model.md` y `docs/security/owasp-review.md`.

## Pasos

1. **Formula la propiedad en una frase**, en términos de lo observable desde
   fuera. «El listado no devuelve datos de otro tenant» sirve; «el repositorio
   filtra bien» no, porque describe la implementación y no lo que hay que
   garantizar.

2. **Elige el nivel más barato que la demuestre de verdad**:
   - Unitaria si la propiedad vive en el dominio o en el caso de uso
     (anti-enumeración en el login, invariantes de estado).
   - Integración con Testcontainers si depende de la cadena de seguridad, de
     la base de datos o del filtrado por tenant.
   - E2E (`frontend/e2e/`) si atraviesa varios módulos o la interfaz.
   - Regla automática (`src/test/.../architecture/`) si lo que hay que impedir
     es un **descuido futuro** y no un fallo actual: por ejemplo, que un
     endpoint privilegiado nuevo se quede sin `@PreAuthorize`.

3. **Escribe primero el caso negativo**: el actor sin permiso, el recurso
   ajeno, la credencial equivocada. Es el que fija la propiedad; el positivo
   solo confirma que la funcionalidad sigue viva.

4. **Comprueba que el test falla sin la protección.** Retírala temporalmente,
   ejecuta y confirma que salta. Un test de seguridad que nunca has visto
   fallar puede estar pasando por vacío, y eso es peor que no tenerlo porque
   además da confianza.

5. **Evita que pase por vacío de forma permanente**: si la prueba recorre un
   conjunto (controladores, rutas, ficheros), afirma también que ese conjunto
   no está vacío.

6. **Cuida qué afirmas en las respuestas de error**:
   - Recurso de otro tenant: `404`, nunca `403`. Un `403` confirma que ese
     identificador existe.
   - Credenciales incorrectas: cuerpo y código idénticos exista o no la
     cuenta.
   - Comprueba el cuerpo completo cuando la propiedad es de indistinguibilidad,
     no solo el código de estado.

7. **Nombra el test por la propiedad**, no por el mecanismo:
   `doesNotRevealThatADeactivatedAccountExists` dice qué se pierde si falla;
   `testAuthenticate2` no.

## Validaciones

- `mvn -B verify` en verde, con ArchUnit y cobertura.
- El test falla si se retira la protección (paso 4, verificado a mano).
- Cross-tenant cubierto si la funcionalidad es tenant-scoped (RT-003).
- Cada rol probado en lo permitido y en lo prohibido (RT-004).

## Salida y documentación a actualizar

- La prueba, con un comentario que explique **qué se pierde si falla**, no lo
  que hace.
- `docs/security/owasp-review.md`: sección de la categoría afectada, citando la
  clase de prueba como evidencia.
- `docs/security/threat-model.md`: fila nueva si la amenaza no estaba recogida.
- `docs/traceability/requirements-matrix.md`: requisito → prueba.
- ADR si la protección introduce una decisión arquitectónica (por ejemplo, un
  punto de contribución nuevo o un cambio en la cadena de filtros).
