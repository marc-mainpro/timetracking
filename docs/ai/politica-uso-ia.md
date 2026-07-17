# Política de uso de IA

Este proyecto se desarrolla con asistencia de agentes de IA siguiendo un
proceso SDD (Spec-Driven Development) trazable. Reglas obligatorias:

1. **Revisión humana obligatoria**: ningún cambio generado por IA se
   considera terminado sin revisión por una persona responsable del
   repositorio.
2. **No commitear sin tests**: el código generado por IA solo se integra si
   incluye las pruebas correspondientes a las reglas de negocio que toca
   (ver `docs/testing/estrategia.md`).
3. **Verificar dependencias y APIs**: antes de introducir una librería o
   usar una API, se comprueba que existe, que la versión es correcta y que
   no introduce riesgos de seguridad o licenciamiento.
4. **Cambios pequeños y auditables**: se prefieren cambios acotados por
   tarea (ficha `tasks/iter-XX/TXXX-*.md`), con su propio informe en
   `tasks/_reports/TXXX-report.md`.
5. **Sin ampliación de alcance**: un agente no debe implementar más de lo
   especificado en la ficha de la tarea en curso.
6. **Decisiones documentadas**: toda decisión de arquitectura no fijada en
   `tasks/_context/CONTEXT-GLOBAL.md` se registra como un nuevo ADR en
   `docs/adr/`.
7. **Sin secretos generados o expuestos**: los agentes no escriben secretos
   en el repositorio; usan variables de entorno y `.env.example`.
8. **Trazabilidad**: cada tarea deja constancia de cambios, pruebas,
   cobertura, seguridad, documentación, ADR, riesgos y pendientes en su
   informe de tarea.
