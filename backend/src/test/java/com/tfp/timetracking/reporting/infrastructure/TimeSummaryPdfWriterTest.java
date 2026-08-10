package com.tfp.timetracking.reporting.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.reporting.domain.EmployeeName;
import com.tfp.timetracking.reporting.domain.TenantEmployeeSummary;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class TimeSummaryPdfWriterTest {

    private static final Instant FROM = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-31T23:59:59Z");

    private final TimeSummaryPdfWriter writer = new TimeSummaryPdfWriter();

    @Test
    void producesAReadablePdfWithTheReportContent() throws IOException {
        UUID employeeId = UUID.randomUUID();
        Map<UUID, EmployeeName> names = Map.of(employeeId, new EmployeeName("Ana", "Ruiz"));
        byte[] pdf = writer.render(
                List.of(summary(employeeId, Duration.ofHours(8).plusMinutes(30))), names, FROM, TO);

        // No basta con que devuelva bytes: se abre el documento y se lee, que es
        // lo que hará quien lo reciba.
        assertThat(pdf).isNotEmpty();
        String text = textOf(pdf);
        assertThat(text).contains("Informe de horas por empleado");
        assertThat(text).contains("01/07/2026").contains("31/07/2026");
        assertThat(text).contains("8h 30m");
        assertThat(text).contains("Ruiz Ana");
    }

    @Test
    void statesExplicitlyWhenThereIsNothingToReport() {
        // Un PDF con solo la cabecera de tabla parecería un fallo de generación.
        String text = textOf(writer.render(List.of(), Map.of(), FROM, TO));

        assertThat(text).contains("No hay jornadas registradas en este periodo");
    }

    @Test
    void fallsBackToTheEmployeeIdWhenTheNameIsUnknown() {
        UUID employeeId = UUID.randomUUID();
        byte[] pdf = writer.render(List.of(summary(employeeId, Duration.ofHours(8))), Map.of(), FROM, TO);

        // La celda se trunca al ancho de columna (ver Cursor#truncate): basta con
        // comprobar el prefijo del id, no la cadena completa.
        assertThat(textOf(pdf)).contains(employeeId.toString().substring(0, 8));
    }

    @Test
    void paginatesAndRepeatsTheHeaderOnEveryPage() throws IOException {
        // Una hoja suelta de un informe largo debe seguir siendo legible.
        List<TenantEmployeeSummary> many = new ArrayList<>();
        Map<UUID, EmployeeName> names = new HashMap<>();
        for (int i = 0; i < 120; i++) {
            UUID employeeId = UUID.randomUUID();
            many.add(summary(employeeId, Duration.ofHours(8)));
            names.put(employeeId, new EmployeeName("Empleado", "Numero" + i));
        }

        byte[] pdf = writer.render(many, names, FROM, TO);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(2);
            stripper.setEndPage(2);
            assertThat(stripper.getText(document)).contains("Empleado").contains("Trabajado");
        }
    }

    @Test
    void formatsDurationsForReadingAndNotForParsing() {
        // El CSV exporta segundos para consumo programático; el PDF se lee.
        assertThat(TimeSummaryPdfWriter.humanDuration(Duration.ofMinutes(510))).isEqualTo("8h 30m");
        assertThat(TimeSummaryPdfWriter.humanDuration(Duration.ZERO)).isEqualTo("0h 0m");
        assertThat(TimeSummaryPdfWriter.humanDuration(null)).isEqualTo("0h 0m");
        assertThat(TimeSummaryPdfWriter.humanDuration(Duration.ofMinutes(-90))).isEqualTo("-1h 30m");
    }

    private static String textOf(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            throw new IllegalStateException("El PDF generado no se puede abrir", e);
        }
    }

    private static TenantEmployeeSummary summary(UUID employeeId, Duration worked) {
        return new TenantEmployeeSummary(
                employeeId,
                worked,
                Duration.ofMinutes(30),
                Duration.ofHours(8),
                worked,
                Duration.ZERO,
                Duration.ZERO,
                20,
                0,
                0,
                20);
    }
}
