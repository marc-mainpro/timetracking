# T502 — Frontend: gestión de empleados (admin)

- Iteración: 5 · Depende de: T501, T404 · Contexto: CONTEXT-API §2 y §4

## Objetivo
Pantallas de administración de empleados tras guard de rol `TENANT_ADMIN`.

## Detalle
1. Listado paginado con filtro por estado y búsqueda visual; acciones activar/desactivar con confirmación.
2. Formulario reactivo de alta (email, nombre, apellidos, password inicial, roles) y de edición; asignación de roles.
3. Errores del servidor (409 email duplicado, LAST_ADMIN) mostrados sobre el campo/acción correspondiente; estados de carga.
4. Ruta `/admin/employees` protegida por guard de autenticación + guard de rol; un EMPLOYEE que navegue a ella es redirigido.

## Pruebas
Tests de componente (listado con datos mock, formulario con validaciones, manejo de 409) y del guard de rol.

## Criterios de aceptación
- `ng build`/`ng test` verdes; flujo manual verificado contra backend local.

## Ficheros previstos
`frontend/src/app/features/admin-employees/**`, tests.
