package com.tfp.timetracking.notification.application;

import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Los textos que lee el destinatario de una notificacion (T170-12).
 *
 * <p>Viven aparte del consumidor a proposito: {@link NotificationEventListener}
 * decide <b>a quien</b> se avisa y por que canal, y eso es mecanica; esto es
 * redaccion, y conviene poder revisar los dieciseis textos juntos sin leer la
 * tabla de plantillas.
 *
 * <p><b>Ningun texto puede contener un identificador.</b> Ni un UUID, ni un
 * nombre de enum, ni una fecha ISO: son datos internos, y que se filtren al
 * usuario es exactamente el defecto que este catalogo existe para evitar. De
 * ahi que cada dato del payload pase por un formateador y que todos degraden a
 * una frase valida en vez de imprimir el valor crudo.
 */
final class NotificationTexts {

    /** Como se llama a alguien cuyo nombre no se ha podido resolver. */
    private static final String ANONIMO = "Un empleado";

    private static final String[] MESES = {
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    };

    /**
     * Traduccion de {@code timetracking.domain.WorkdayAnomaly}.
     *
     * <p>Se duplica aqui en vez de depender del enum: {@code notification}
     * consume el evento de integracion, que es un contrato de cadenas, y
     * acoplarse al enum de otro modulo por un texto seria pagar una dependencia
     * entre modulos a cambio de nada.
     */
    private static final Map<String, String> ANOMALIAS = Map.of(
            "REQUIRED_BREAK_NOT_MET", "no se alcanzó la pausa mínima obligatoria",
            "MAX_DAILY_WORK_EXCEEDED", "se superó el máximo de horas diarias");

    private NotificationTexts() {}

    /**
     * Nombres de las personas que aparecen en un texto.
     *
     * <p>Lo construye el consumidor por evento, ya atado a su tenant, para que
     * el catalogo pueda nombrar a alguien sin conocer a {@code identity}.
     */
    @FunctionalInterface
    interface ActorNames {

        /**
         * @param payloadField campo del payload que contiene el id de la persona
         * @return su nombre, o {@value #ANONIMO} si no se puede resolver: el
         *     usuario ya no existe, es de otro tenant, o el campo no viene
         */
        String name(String payloadField);
    }

    /** Cuerpo de una notificacion, que puede necesitar nombrar a alguien. */
    @FunctionalInterface
    interface BodyText {
        String render(IntegrationEvent event, ActorNames actors);
    }

    // ------------------------------------------------------------------
    // Empleado
    // ------------------------------------------------------------------

    static String correctionApproved(IntegrationEvent event, ActorNames actors) {
        return "Tu solicitud de corrección de jornada ha sido aprobada. "
                + "Los cambios ya están aplicados en tu historial.";
    }

    static String correctionRejected(IntegrationEvent event, ActorNames actors) {
        return "Tu solicitud de corrección de jornada ha sido rechazada. "
                + "Tu jornada se mantiene como estaba registrada.";
    }

    static String absenceApproved(IntegrationEvent event, ActorNames actors) {
        String rango = rango(event, "startDate", "endDate");
        return rango == null
                ? "Tu solicitud de ausencia ha sido aprobada."
                : "Tu ausencia " + rango + " ha sido aprobada.";
    }

    static String absenceRejected(IntegrationEvent event, ActorNames actors) {
        String rango = rango(event, "startDate", "endDate");
        return rango == null
                ? "Tu solicitud de ausencia ha sido rechazada."
                : "Tu solicitud de ausencia " + rango + " ha sido rechazada.";
    }

    static String workdayAnomaly(IntegrationEvent event, ActorNames actors) {
        return "En tu última jornada " + anomalias(event)
                + ". Revísala y solicita una corrección si no es correcta.";
    }

    static String accountCreated(IntegrationEvent event, ActorNames actors) {
        // Nunca transporta credenciales: lleva a la pantalla donde la persona
        // establece su propia contrasena (T170-04).
        return "Ya puedes acceder al sistema de control horario. "
                + "Para entrar por primera vez, crea tu contraseña desde la pantalla "
                + "de recuperación de contraseña.";
    }

    static String accountDeactivated(IntegrationEvent event, ActorNames actors) {
        return "Tu cuenta del sistema de control horario se ha desactivado y ya no puedes acceder. "
                + "Si crees que es un error, habla con la administración de tu organización.";
    }

    static String shiftAssigned(IntegrationEvent event, ActorNames actors) {
        String nombre = text(event, "shiftTemplateName");
        String turno = nombre == null ? "un turno nuevo" : "el turno «" + nombre + "»";
        String rango = rango(event, "validFrom", "validTo");
        return rango == null
                ? "Se te ha asignado " + turno + "."
                : "Se te ha asignado " + turno + " " + rango + ".";
    }

    // ------------------------------------------------------------------
    // Administrador de tenant
    // ------------------------------------------------------------------

    static String correctionRequested(IntegrationEvent event, ActorNames actors) {
        return actors.name("requestedBy") + " ha solicitado una corrección de su jornada. "
                + "Está pendiente de que la revises.";
    }

    static String absenceRequested(IntegrationEvent event, ActorNames actors) {
        String rango = rango(event, "startDate", "endDate");
        String quien = actors.name("employeeId");
        return rango == null
                ? quien + " ha solicitado una ausencia. Está pendiente de que la resuelvas."
                : quien + " ha solicitado una ausencia " + rango + ". Está pendiente de que la resuelvas.";
    }

    static String teamWorkdayAnomaly(IntegrationEvent event, ActorNames actors) {
        return "En la última jornada de " + actors.name("employeeId") + " " + anomalias(event) + ".";
    }

    static String tenantSuspended(IntegrationEvent event, ActorNames actors) {
        String motivo = text(event, "reason");
        String cuerpo = "Tu organización se ha suspendido: sus empleados no pueden registrar jornada "
                + "y nadie puede acceder hasta que se reactive.";
        return motivo == null ? cuerpo : cuerpo + " Motivo: " + motivo + ".";
    }

    static String tenantReactivated(IntegrationEvent event, ActorNames actors) {
        return "Tu organización vuelve a estar activa. Los empleados ya pueden registrar jornada "
                + "con normalidad.";
    }

    static String tenantArchived(IntegrationEvent event, ActorNames actors) {
        String motivo = text(event, "reason");
        String cuerpo = "Tu organización se ha archivado y ya no puede operar. "
                + "Los datos se conservan, pero el acceso queda cerrado de forma permanente.";
        return motivo == null ? cuerpo : cuerpo + " Motivo: " + motivo + ".";
    }

    // ------------------------------------------------------------------
    // Administrador de plataforma
    // ------------------------------------------------------------------

    static String registrationPendingReview(IntegrationEvent event, ActorNames actors) {
        String empresa = text(event, "companyName");
        String sujeto = empresa == null ? "Una organización" : "«" + empresa + "»";
        return sujeto + " ha verificado su correo y espera revisión. "
                + "Nadie podrá acceder hasta que apruebes o rechaces el alta.";
    }

    static String queueStuck(long fallidos) {
        String cuantos = fallidos == 1
                ? "Hay 1 mensaje que ha agotado"
                : "Hay " + fallidos + " mensajes que han agotado";
        return cuantos + " sus reintentos y no se recuperan solos. "
                + "Revisa el panel de estado del sistema para ver qué cola está afectada.";
    }

    // ------------------------------------------------------------------
    // Formateadores
    // ------------------------------------------------------------------

    /**
     * Traduce los codigos de anomalia a una enumeracion legible.
     *
     * <p>Acepta el valor como coleccion y como cadena {@code "[A, B]"}: el
     * payload llega en una forma u otra segun haya pasado o no por la
     * serializacion del outbox, y la version cadena es justo la que imprimia los
     * corchetes en pantalla.
     *
     * <p>Un codigo desconocido <b>nunca se imprime en crudo</b>. Si manana
     * {@code timetracking} anade una anomalia, el aviso dira algo generico y
     * correcto en vez de filtrar el nombre del enum.
     */
    static String anomalias(IntegrationEvent event) {
        List<String> frases = new ArrayList<>();
        boolean desconocida = false;
        for (String codigo : codigos(event.payload().get("anomalies"))) {
            String frase = ANOMALIAS.get(codigo);
            if (frase == null) {
                desconocida = true;
            } else if (!frases.contains(frase)) {
                frases.add(frase);
            }
        }
        if (desconocida || frases.isEmpty()) {
            frases.add("se detectó una incidencia");
        }
        return enumerar(frases);
    }

    /** Convierte {@code 2026-10-01} en «1 de octubre de 2026». */
    static String fecha(String iso) {
        LocalDate fecha = parse(iso);
        return fecha == null ? iso : fecha.getDayOfMonth() + " de " + MESES[fecha.getMonthValue() - 1]
                + " de " + fecha.getYear();
    }

    /**
     * Compone «del 1 al 3 de octubre de 2026», colapsando el mes y el ano
     * cuando coinciden, y «a partir del …» cuando no hay fin.
     *
     * @return {@code null} si no hay fecha de inicio, para que quien llama pueda
     *     elegir una frase sin rango en vez de dejar un hueco
     */
    static String rango(IntegrationEvent event, String desdeCampo, String hastaCampo) {
        String desdeTexto = text(event, desdeCampo);
        if (desdeTexto == null) {
            return null;
        }
        String hastaTexto = text(event, hastaCampo);
        if (hastaTexto == null) {
            return "a partir del " + fecha(desdeTexto);
        }
        LocalDate desde = parse(desdeTexto);
        LocalDate hasta = parse(hastaTexto);
        if (desde == null || hasta == null) {
            return "del " + fecha(desdeTexto) + " al " + fecha(hastaTexto);
        }
        if (desde.equals(hasta)) {
            return "del " + fecha(desdeTexto);
        }
        if (desde.getYear() == hasta.getYear() && desde.getMonthValue() == hasta.getMonthValue()) {
            return "del " + desde.getDayOfMonth() + " al " + fecha(hastaTexto);
        }
        if (desde.getYear() == hasta.getYear()) {
            return "del " + desde.getDayOfMonth() + " de " + MESES[desde.getMonthValue() - 1]
                    + " al " + fecha(hastaTexto);
        }
        return "del " + fecha(desdeTexto) + " al " + fecha(hastaTexto);
    }

    /** @return el nombre por defecto cuando no se puede resolver una persona */
    static String anonimo() {
        return ANONIMO;
    }

    private static Set<String> codigos(Object valor) {
        if (valor == null) {
            return Set.of();
        }
        Set<String> codigos = new LinkedHashSet<>();
        if (valor instanceof Collection<?> coleccion) {
            for (Object elemento : coleccion) {
                anadir(codigos, elemento == null ? null : elemento.toString());
            }
            return codigos;
        }
        // Forma "[A, B]" o "A": es como llega tras la ida y vuelta por el outbox.
        String texto = valor.toString().trim();
        if (texto.startsWith("[") && texto.endsWith("]")) {
            texto = texto.substring(1, texto.length() - 1);
        }
        for (String parte : texto.split(",")) {
            anadir(codigos, parte);
        }
        return codigos;
    }

    private static void anadir(Set<String> codigos, String codigo) {
        if (codigo != null && !codigo.isBlank()) {
            codigos.add(codigo.trim());
        }
    }

    /** «a», «a y b», «a, b y c». */
    private static String enumerar(List<String> frases) {
        if (frases.size() == 1) {
            return frases.get(0);
        }
        String ultima = frases.get(frases.size() - 1);
        return String.join(", ", frases.subList(0, frases.size() - 1)) + " y " + ultima;
    }

    private static LocalDate parse(String iso) {
        if (iso == null) {
            return null;
        }
        try {
            return LocalDate.parse(iso);
        } catch (DateTimeParseException noEsUnaFecha) {
            return null;
        }
    }

    private static String text(IntegrationEvent event, String field) {
        Object value = event.payload().get(field);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString();
    }

    /**
     * Un campo del payload que contiene un identificador de usuario.
     *
     * <p>Vive aqui y no en el consumidor porque lo necesitan las dos partes: el
     * consumidor para resolver el destinatario, y el catalogo para nombrar a
     * quien provoco el hecho.
     */
    static UUID uuid(IntegrationEvent event, String field) {
        Object value = event.payload().get(field);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        String texto = text(event, field);
        if (texto == null) {
            return null;
        }
        try {
            return UUID.fromString(texto);
        } catch (IllegalArgumentException noEsUnUuid) {
            return null;
        }
    }
}
