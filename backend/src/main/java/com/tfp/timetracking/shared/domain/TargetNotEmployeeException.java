package com.tfp.timetracking.shared.domain;

/**
 * Se lanza al intentar asignar algo propio de la jornada laboral —un turno, un
 * calendario de ambito empleado— a un usuario que no tiene el rol de empleado.
 *
 * <p>La regla no es formal: los endpoints de fichaje exigen el rol
 * {@code EMPLOYEE}, asi que una asignacion a un administrador que no ficha
 * nunca llegaria a usarse. Rechazarla al crearla evita datos que no significan
 * nada.
 *
 * <p>Vive en {@code shared.domain} y no duplicada en {@code shift} y
 * {@code calendar} porque es literalmente la misma regla y el mismo
 * {@code errorCode}: el frontend traduce ese codigo a un mensaje una sola vez.
 */
public final class TargetNotEmployeeException extends DomainException {

    public TargetNotEmployeeException() {
        super("TARGET_NOT_EMPLOYEE", "El usuario indicado no tiene el rol de empleado y no admite asignaciones");
    }
}
