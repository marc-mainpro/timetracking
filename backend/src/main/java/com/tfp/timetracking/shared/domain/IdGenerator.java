package com.tfp.timetracking.shared.domain;

import java.util.UUID;

/**
 * Puerto de dominio para generar identificadores (UUID v4) para las
 * entidades de negocio. Ver CONTEXT-GLOBAL §3: "IDs: UUID v4 generados en
 * aplicacion". La implementacion concreta vive en infraestructura.
 */
public interface IdGenerator {

    UUID newId();
}
