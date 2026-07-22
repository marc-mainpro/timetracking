package com.tfp.timetracking.shared.interfaces.rest;

/**
 * Validacion defensiva compartida para parametros de paginacion REST.
 */
public record PageQuery(int page, int size) {

    private static final int MAX_SIZE = 100;

    public static PageQuery of(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page debe ser mayor o igual que 0");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size debe ser mayor que 0");
        }
        if (size > MAX_SIZE) {
            throw new IllegalArgumentException("size no puede ser mayor que " + MAX_SIZE);
        }
        return new PageQuery(page, size);
    }
}
