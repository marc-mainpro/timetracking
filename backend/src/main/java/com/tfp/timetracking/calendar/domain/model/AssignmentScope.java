package com.tfp.timetracking.calendar.domain.model;

/**
 * Ambito de una {@link CalendarAssignment} (RF-CAL-006, T70-02).
 *
 * <p>El valor de {@link #specificity()} es el <b>contrato de precedencia</b> del
 * modulo: cuando varias asignaciones aplican a un mismo empleado en una misma
 * fecha, gana la de mayor especificidad ("la asignacion mas especifica
 * prevalece"). Los modulos de turnos (T90) y ausencias (T80) reutilizan esta
 * regla a traves de
 * {@code com.tfp.timetracking.calendar.domain.service.EffectiveCalendarResolver};
 * <b>no deben reimplementarla</b>.
 *
 * <p>Los numeros son deliberadamente no contiguos ({@code 10/20/30}) para poder
 * intercalar un ambito futuro (p.ej. centro de trabajo) sin renumerar los
 * existentes ni cambiar el orden relativo ya publicado.
 */
public enum AssignmentScope {

    /** Toda la organizacion. Es el calendario por defecto; no lleva destinatario. */
    TENANT(10),

    /**
     * Un equipo. El identificador de equipo es opaco para este modulo: no
     * existe gestion de equipos en el sistema (ver ADR-0017), asi que la
     * pertenencia empleado-equipo la aporta quien invoca la resolucion.
     */
    TEAM(20),

    /** Un empleado concreto. Es el ambito mas especifico. */
    EMPLOYEE(30);

    private final int specificity;

    AssignmentScope(int specificity) {
        this.specificity = specificity;
    }

    /** Peso de precedencia: a mayor valor, mas especifico y por tanto prevalece. */
    public int specificity() {
        return specificity;
    }

    /** {@code true} si el ambito exige un destinatario ({@code targetId}). */
    public boolean requiresTarget() {
        return this != TENANT;
    }
}
