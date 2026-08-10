package com.tfp.timetracking.reporting.application;

import com.tfp.timetracking.reporting.domain.EmployeeName;
import com.tfp.timetracking.reporting.domain.TenantEmployeeSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Puerto de renderizado del informe en PDF (T100-06).
 *
 * <p>El caso de uso depende de esta interfaz y no de PDFBox: la librería es un
 * detalle de infraestructura y cambiarla no debe alcanzar a la capa de
 * aplicación.
 */
public interface TimeSummaryPdfRenderer {

    byte[] render(List<TenantEmployeeSummary> summaries, Map<UUID, EmployeeName> names, Instant from, Instant to);
}
