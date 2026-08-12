# Procesos de negocio

Vista de **proceso** del sistema: cómo transcurre cada operación de negocio de
principio a fin, atravesando los módulos que hagan falta. Complementa a las
otras vistas de la documentación, que están organizadas por estructura y no por
recorrido:

- `docs/architecture/` describe **qué piezas existen** (modelo C4).
- `docs/domain/` describe **qué reglas** gobiernan cada agregado.
- `docs/api/` describe **qué endpoints** se exponen.
- `docs/procesos/` (este directorio) describe **en qué orden ocurren las cosas**
  y qué caminos alternativos hay cuando algo no va bien.

## Índice

| Proceso | Actor que lo inicia |
| --- | --- |
| [Alta de una empresa](alta-de-empresa.md) | Visitante anónimo → `PLATFORM_ADMIN` |
| [Ciclo de vida del tenant](ciclo-de-vida-del-tenant.md) | `PLATFORM_ADMIN` |
| [Inicio de sesión](inicio-de-sesion.md) | Cualquier usuario |
| [Gestión de la sesión](gestion-de-sesion.md) | Cualquier usuario / navegador |
| [Recuperación de contraseña](recuperacion-de-contrasena.md) | Cualquier usuario |
| [Gestión de empleados](gestion-de-empleados.md) | `TENANT_ADMIN` |
| [Jornada laboral](jornada-laboral.md) | `EMPLOYEE` |
| [Corrección de jornada](correccion-de-jornada.md) | `EMPLOYEE` → `TENANT_ADMIN` |
| [Gestión de ausencias](gestion-de-ausencias.md) | `EMPLOYEE` → `TENANT_ADMIN` |
| [Calendarios laborales](calendarios-laborales.md) | `TENANT_ADMIN` |
| [Turnos](turnos.md) | `TENANT_ADMIN` |
| [Informes y exportaciones](informes-y-exportaciones.md) | `EMPLOYEE` / `TENANT_ADMIN` |
| [Notificaciones y outbox](notificaciones-y-outbox.md) | Sistema (reactivo) |

## Convenciones de los diagramas

Los diagramas se escriben en **Mermaid**, dentro de bloques ` ```mermaid `.
Es la primera parte de `docs/` que lo usa; el resto de diagramas del repositorio
son tablas o ASCII. Mermaid se eligió aquí porque estos documentos son
mayoritariamente secuencias y máquinas de estado, que en texto plano resultan
ilegibles, y porque GitHub y los IDE los renderizan sin herramientas extra.

Reglas que sigue todo diagrama de esta carpeta:

- **Altitud de negocio.** Los participantes son actores y sistemas
  (`Empleado`, `Admin del tenant`, `Backend`, `Correo`), no clases Java. Los
  nombres de clases viven en la sección «Referencias» de cada documento, para
  que un refactor no invalide el dibujo.
- **Solo las ramas que cambian el resultado de negocio.** Las validaciones de
  formato (campo obligatorio, longitud mínima) no se dibujan; sí se dibujan los
  conflictos de estado, los rechazos por permisos y las respuestas silenciosas.
- **El outbox nunca se dibuja como llamada síncrona.** El evento se escribe en
  la misma transacción que el cambio de negocio, y un job lo publica después
  (ADR-0005). En los diagramas aparece como una escritura seguida de un salto
  temporal explícito.
- **404 en lugar de 403** al pedir un recurso de otro tenant o de otro empleado
  (ADR-0002). Aparece como rama explícita en los procesos donde aplica.
- **Anti-enumeración**: los endpoints públicos responden lo mismo pase lo que
  pase por dentro (RF-REG-005). Como un diagrama de secuencia no muestra bien
  «muchos caminos, una sola salida», esos procesos incluyen además un
  `flowchart` de los caminos silenciosos.

## Referencias transversales

- Reglas de negocio: `docs/domain/reglas-de-negocio.md`
- Agregados y sus invariantes: `docs/domain/agregados.md`
- Contrato de eventos: `docs/integration/event-catalog.md`
- Entrega de eventos: `docs/integration/outbox-publisher.md`
- Contrato HTTP completo: `docs/api/openapi.yaml`
- Controles de autenticación: `docs/security/auth-controls.md`
