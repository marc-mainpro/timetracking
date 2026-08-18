/** Roles del sistema, tal como los declara `identity.domain.Role` en el backend. */
export type Role = 'PLATFORM_ADMIN' | 'TENANT_ADMIN' | 'EMPLOYEE';

/**
 * Zona de la aplicación que se está usando.
 *
 * <p>No es un permiso: quien tiene los dos roles conserva acceso a las dos
 * zonas, y los guards siguen mirando los roles del JWT. La vista solo decide
 * qué menú se muestra, para que quien administra y además ficha no cargue con
 * trece enlaces a la vez.
 */
export type ViewMode = 'PLATFORM' | 'ADMIN' | 'EMPLOYEE';

/** Rol que habilita cada vista. */
export const VIEW_ROLE: Readonly<Record<ViewMode, Role>> = {
  PLATFORM: 'PLATFORM_ADMIN',
  ADMIN: 'TENANT_ADMIN',
  EMPLOYEE: 'EMPLOYEE'
};

/**
 * Orden de preferencia al elegir vista por defecto y al redirigir a quien no
 * puede entrar donde pedía. Estaba repetido —y no siempre igual— en el guard de
 * rol, en la cabecera y en el login; vive aquí para que no vuelva a divergir.
 */
export const VIEW_PRIORITY: readonly ViewMode[] = ['PLATFORM', 'ADMIN', 'EMPLOYEE'];

/** Pantalla de inicio de cada vista. */
export const VIEW_HOME_ROUTE: Readonly<Record<ViewMode, string>> = {
  PLATFORM: '/platform/tenants',
  ADMIN: '/admin/employees',
  EMPLOYEE: '/employee-dashboard'
};
