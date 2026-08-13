package com.tfp.timetracking.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Los formateadores del catalogo de textos (T170-12).
 *
 * <p>Es donde vive el riesgo real: son funciones puras, y su unico trabajo es
 * que <b>ningun identificador llegue al usuario</b>. Cada caso de aqui
 * corresponde a una forma concreta en la que un dato interno podria escaparse.
 */
class NotificationTextsTest {

    private static final NotificationTexts.ActorNames SIN_NOMBRES = field -> NotificationTexts.anonimo();

    @Test
    void translatesTheKnownAnomalyCodes() {
        assertThat(anomalias("REQUIRED_BREAK_NOT_MET")).isEqualTo("no se alcanzó la pausa mínima obligatoria");
        assertThat(anomalias("MAX_DAILY_WORK_EXCEEDED")).isEqualTo("se superó el máximo de horas diarias");
    }

    @Test
    void readsTheAnomaliesBothAsAListAndAsText() {
        // Segun haya pasado o no por la serializacion del outbox, el payload
        // llega como lista o como el toString() de la lista. La segunda forma es
        // la que imprimia los corchetes en pantalla.
        assertThat(anomalias(List.of("REQUIRED_BREAK_NOT_MET", "MAX_DAILY_WORK_EXCEEDED")))
                .isEqualTo("no se alcanzó la pausa mínima obligatoria y se superó el máximo de horas diarias");
        assertThat(anomalias("[REQUIRED_BREAK_NOT_MET, MAX_DAILY_WORK_EXCEEDED]"))
                .isEqualTo("no se alcanzó la pausa mínima obligatoria y se superó el máximo de horas diarias");
    }

    @Test
    void neverPrintsAnUnknownCodeVerbatim() {
        // Si timetracking anade una anomalia manana, el aviso debe seguir siendo
        // una frase correcta en vez de filtrar el nombre del enum.
        String texto = anomalias("ANOMALIA_QUE_TODAVIA_NO_EXISTE");

        assertThat(texto).isEqualTo("se detectó una incidencia");
        assertThat(texto).doesNotContain("ANOMALIA_QUE_TODAVIA_NO_EXISTE").doesNotContain("_");
    }

    @Test
    void fallsBackWhenThereAreNoAnomaliesAtAll() {
        assertThat(anomalias(null)).isEqualTo("se detectó una incidencia");
        assertThat(anomalias("")).isEqualTo("se detectó una incidencia");
    }

    @Test
    void writesDatesInSpanish() {
        assertThat(NotificationTexts.fecha("2026-10-01")).isEqualTo("1 de octubre de 2026");
        assertThat(NotificationTexts.fecha("2026-01-31")).isEqualTo("31 de enero de 2026");
    }

    @Test
    void returnsTheRawValueWhenADateDoesNotParse() {
        // Preferible a romper la creacion de la notificacion por un payload raro.
        assertThat(NotificationTexts.fecha("mañana")).isEqualTo("mañana");
    }

    @Test
    void collapsesTheMonthAndYearOfARangeWhenTheyMatch() {
        assertThat(rango("2026-10-01", "2026-10-03")).isEqualTo("del 1 al 3 de octubre de 2026");
        assertThat(rango("2026-09-28", "2026-10-02")).isEqualTo("del 28 de septiembre al 2 de octubre de 2026");
        assertThat(rango("2026-12-30", "2027-01-02")).isEqualTo("del 30 de diciembre de 2026 al 2 de enero de 2027");
    }

    @Test
    void describesASingleDayAndAnOpenEndedRange() {
        assertThat(rango("2026-10-01", "2026-10-01")).isEqualTo("del 1 de octubre de 2026");
        assertThat(rango("2026-10-01", null)).isEqualTo("a partir del 1 de octubre de 2026");
    }

    @Test
    void returnsNoRangeWhenThereIsNoStartDate() {
        // Quien llama elige entonces una frase sin rango, en vez de dejar un hueco.
        assertThat(rango(null, "2026-10-03")).isNull();
    }

    @Test
    void namesTheShiftAndItsValidity() {
        String texto = NotificationTexts.shiftAssigned(
                event(Map.of(
                        "shiftTemplateName", "Turno de mañana",
                        "validFrom", "2026-09-01",
                        "validTo", "2026-09-30")),
                SIN_NOMBRES);

        assertThat(texto).isEqualTo("Se te ha asignado el turno «Turno de mañana» del 1 al 30 de septiembre de 2026.");
    }

    @Test
    void stillReadsWellWhenTheShiftHasNoName() {
        String texto = NotificationTexts.shiftAssigned(event(Map.of("validFrom", "2026-09-01")), SIN_NOMBRES);

        assertThat(texto).isEqualTo("Se te ha asignado un turno nuevo a partir del 1 de septiembre de 2026.");
    }

    @Test
    void degradesToAGenericNameWithoutLeavingAGap() {
        String texto = NotificationTexts.absenceRequested(
                event(Map.of("startDate", "2026-10-01", "endDate", "2026-10-03")), SIN_NOMBRES);

        assertThat(texto).startsWith("Un empleado ha solicitado una ausencia del 1 al 3 de octubre de 2026");
    }

    @Test
    void usesTheResolvedNameWhenThereIsOne() {
        String texto = NotificationTexts.correctionRequested(event(Map.of()), field -> "Ana García");

        assertThat(texto).startsWith("Ana García ha solicitado");
    }

    @Test
    void agreesInNumberWithTheAmountOfStuckMessages() {
        assertThat(NotificationTexts.queueStuck(1)).startsWith("Hay 1 mensaje que ha agotado");
        assertThat(NotificationTexts.queueStuck(4)).startsWith("Hay 4 mensajes que han agotado");
    }

    private static String anomalias(Object valor) {
        Map<String, Object> payload = valor == null ? Map.of() : Map.of("anomalies", valor);
        return NotificationTexts.anomalias(event(payload));
    }

    private static String rango(String desde, String hasta) {
        Map<String, Object> payload = new java.util.HashMap<>();
        if (desde != null) {
            payload.put("startDate", desde);
        }
        if (hasta != null) {
            payload.put("endDate", hasta);
        }
        return NotificationTexts.rango(event(payload), "startDate", "endDate");
    }

    private static IntegrationEvent event(Map<String, Object> payload) {
        return new IntegrationEvent(
                UUID.randomUUID(),
                "test.event.v1",
                1,
                Instant.parse("2026-08-13T10:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Aggregate",
                payload);
    }
}
