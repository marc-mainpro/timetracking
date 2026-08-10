package com.tfp.timetracking.reporting.domain;

/**
 * Nombre de un empleado tal y como lo necesitan las salidas del informe
 * (CSV, PDF): el id no es legible para quien abre el fichero, así que las
 * exportaciones deben mostrar nombre y apellidos en su lugar.
 */
public record EmployeeName(String firstName, String lastName) {

    public String displayName() {
        return lastName + " " + firstName;
    }
}
