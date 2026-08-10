package com.tfp.timetracking.reporting.infrastructure;

import com.tfp.timetracking.reporting.application.TimeSummaryPdfRenderer;
import com.tfp.timetracking.reporting.domain.EmployeeName;
import com.tfp.timetracking.reporting.domain.TenantEmployeeSummary;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

/**
 * Genera el informe de tenant en PDF (T100-06, RF-REP-006).
 *
 * <p>Vive en {@code infrastructure} y no junto a {@code TimeSummaryCsvWriter}
 * —que esta en {@code application}— porque no es lo mismo: el CSV se compone
 * concatenando texto, sin dependencias, mientras que esto es un adaptador sobre
 * una libreria externa. Mantenerlo fuera de {@code application} evita que una
 * decision de formato arrastre a PDFBox hacia la capa de casos de uso.
 *
 * <p>El PDF es <b>para leer</b>, no para procesar: por eso las duraciones van
 * en {@code 8h 30m} y no en segundos como en el CSV, que existe justamente para
 * el consumo programatico. Elegir un unico formato para ambos empeoraria los
 * dos usos.
 *
 * <p>Paginacion propia: PDFBox no la hace. Se emite cabecera de tabla en cada
 * pagina para que una hoja suelta siga siendo legible.
 */
@Component
public class TimeSummaryPdfWriter implements TimeSummaryPdfRenderer {

    private static final float MARGIN = 40f;
    private static final float LINE_HEIGHT = 16f;
    private static final float TITLE_SIZE = 14f;
    private static final float BODY_SIZE = 9f;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC);

    private static final String[] HEADERS = {
        "Empleado", "Trabajado", "Pausas", "Esperado", "Extra", "Desviación", "Jornadas"
    };
    private static final float[] COLUMN_WIDTHS = {170f, 70f, 60f, 70f, 60f, 75f, 60f};

    @Override
    public byte[] render(List<TenantEmployeeSummary> summaries, Map<UUID, EmployeeName> names, Instant from, Instant to) {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Cursor cursor = new Cursor(document);
            cursor.startPage();
            cursor.title("Informe de horas por empleado");
            cursor.subtitle("Periodo: " + DATE_FORMAT.format(from) + " — " + DATE_FORMAT.format(to) + " (UTC)");
            cursor.newLine();
            cursor.tableHeader();

            if (summaries.isEmpty()) {
                cursor.text("No hay jornadas registradas en este periodo.");
            }
            for (TenantEmployeeSummary summary : summaries) {
                EmployeeName name = names.get(summary.employeeId());
                cursor.row(new String[] {
                    name != null ? name.displayName() : summary.employeeId().toString(),
                    humanDuration(summary.worked()),
                    humanDuration(summary.paused()),
                    humanDuration(summary.expected()),
                    humanDuration(summary.overtime()),
                    humanDuration(summary.deviation()),
                    String.valueOf(summary.workdayCount())
                });
            }
            cursor.close();

            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF del informe", e);
        }
    }

    /** {@code PT8H30M} no se lee: se convierte a {@code 8h 30m}. */
    static String humanDuration(Duration duration) {
        if (duration == null || duration.isZero()) {
            return "0h 0m";
        }
        long totalMinutes = Math.abs(duration.toMinutes());
        String sign = duration.isNegative() ? "-" : "";
        return sign + (totalMinutes / 60) + "h " + (totalMinutes % 60) + "m";
    }

    /**
     * Posicion de escritura dentro del documento. Encapsula el salto de pagina
     * para que el bucle de filas no tenga que saber nada de coordenadas.
     */
    private static final class Cursor {

        private final PDDocument document;
        private PDPageContentStream stream;
        private float y;

        private Cursor(PDDocument document) {
            this.document = document;
        }

        private void startPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private void title(String text) throws IOException {
            write(text, MARGIN, TITLE_SIZE, Standard14Fonts.FontName.HELVETICA_BOLD);
            y -= LINE_HEIGHT * 1.5f;
        }

        private void subtitle(String text) throws IOException {
            write(text, MARGIN, BODY_SIZE + 1, Standard14Fonts.FontName.HELVETICA);
            y -= LINE_HEIGHT;
        }

        private void text(String value) throws IOException {
            write(value, MARGIN, BODY_SIZE, Standard14Fonts.FontName.HELVETICA);
            y -= LINE_HEIGHT;
        }

        private void newLine() {
            y -= LINE_HEIGHT / 2;
        }

        private void tableHeader() throws IOException {
            float x = MARGIN;
            for (int i = 0; i < HEADERS.length; i++) {
                write(HEADERS[i], x, BODY_SIZE, Standard14Fonts.FontName.HELVETICA_BOLD);
                x += COLUMN_WIDTHS[i];
            }
            y -= LINE_HEIGHT;
        }

        private void row(String[] values) throws IOException {
            if (y < MARGIN + LINE_HEIGHT * 2) {
                startPage();
                tableHeader();
            }
            float x = MARGIN;
            for (int i = 0; i < values.length; i++) {
                write(truncate(values[i], COLUMN_WIDTHS[i]), x, BODY_SIZE, Standard14Fonts.FontName.HELVETICA);
                x += COLUMN_WIDTHS[i];
            }
            y -= LINE_HEIGHT;
        }

        private void write(String text, float x, float size, Standard14Fonts.FontName font) throws IOException {
            stream.beginText();
            stream.setFont(new PDType1Font(font), size);
            stream.newLineAtOffset(x, y);
            stream.showText(text);
            stream.endText();
        }

        /** Recorta para que una celda larga no invada la siguiente columna. */
        private static String truncate(String value, float columnWidth) {
            int maxChars = (int) (columnWidth / 5f);
            return value.length() <= maxChars ? value : value.substring(0, Math.max(1, maxChars - 1)) + "…";
        }

        private void close() throws IOException {
            if (stream != null) {
                stream.close();
            }
        }
    }
}
