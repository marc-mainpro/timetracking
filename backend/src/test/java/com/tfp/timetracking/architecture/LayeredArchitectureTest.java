package com.tfp.timetracking.architecture;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tfp.timetracking.shared.domain.DomainException;
import com.tfp.timetracking.shared.interfaces.rest.GlobalExceptionHandler;
import com.tfp.timetracking.identity.interfaces.rest.EmployeeRestMapper;
import com.tfp.timetracking.identity.interfaces.rest.EmployeeController;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserStatus;
import com.tfp.timetracking.identity.domain.Email;
import com.tfp.timetracking.corrections.interfaces.rest.CorrectionRestMapper;
import com.tfp.timetracking.corrections.interfaces.rest.CorrectionController;
import com.tfp.timetracking.corrections.domain.CorrectionRequest;
import com.tfp.timetracking.corrections.domain.CorrectionRequestStatus;
import com.tfp.timetracking.corrections.domain.ProposedChanges;
import com.tfp.timetracking.corrections.domain.ProposedChanges.ProposedBreak;
import com.tfp.timetracking.audit.interfaces.rest.AuditEventRestMapper;
import com.tfp.timetracking.audit.domain.AuditEvent;
import com.tfp.timetracking.timetracking.interfaces.rest.WorkdayRestMapper;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.BreakEntry;
import com.tfp.timetracking.timetracking.domain.WorkdayStatus;
import com.tfp.timetracking.reporting.interfaces.rest.ReportRestMapper;
import com.tfp.timetracking.reporting.domain.EmployeeDaySummary;
import com.tfp.timetracking.reporting.domain.TenantEmployeeSummary;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.PagedResult;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * CONTEXT-GLOBAL §4 / T106 regla 3: capas en sentido
 * {@code interfaces -> application -> domain}; {@code infrastructure} puede
 * depender de {@code domain}/{@code application}; nadie depende de
 * {@code infrastructure} (salvo cableado de configuracion de Spring, que no
 * genera un import Java real y por tanto no es una dependencia detectable
 * aqui).
 *
 * <p>{@code allowEmptyShould(true)}: sobre el esqueleto actual varios modulos
 * solo tienen {@code package-info.java} en cada capa, sin dependencias entre
 * clases; la regla debe pasar en verde igualmente.
 *
 * <p>Excepcion puntual (T203, CONTEXT-GLOBAL §7 / ADR-0006): el
 * {@code @RestControllerAdvice} que traduce excepciones a Problem Details
 * ({@link GlobalExceptionHandler}, en {@code shared.interfaces.rest})
 * necesita capturar {@link DomainException} (en {@code shared.domain}) para
 * leer su {@code errorCode} y su mensaje. Esta es la unica forma de que
 * {@code interfaces} conozca un tipo de {@code domain}: no es logica de
 * negocio, es traduccion de errores en el borde de la API, y el propio
 * {@code DomainException} sigue sin depender de Spring/JPA (ver
 * {@code DomainPurityTest}). Se permite explicitamente esta unica
 * dependencia en vez de relajar la regla en general.
 */
@AnalyzeClasses(packages = "com.tfp.timetracking", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

    @ArchTest
    static final ArchRule layers_are_respected = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Interfaces").definedBy("..interfaces..")
            .layer("Application").definedBy("..application..")
            .layer("Domain").definedBy("..domain..")
            .layer("Infrastructure").definedBy("..infrastructure..")
            .whereLayer("Interfaces").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Interfaces", "Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .ignoreDependency(GlobalExceptionHandler.class, DomainException.class)
            .ignoreDependency(EmployeeRestMapper.class, User.class)
            .ignoreDependency(EmployeeRestMapper.class, UserStatus.class)
            .ignoreDependency(EmployeeRestMapper.class, Email.class)
            .ignoreDependency(EmployeeRestMapper.class, PagedResult.class)
            .ignoreDependency(EmployeeController.class, UserStatus.class)
            .ignoreDependency(CorrectionRestMapper.class, CorrectionRequest.class)
            .ignoreDependency(CorrectionRestMapper.class, CorrectionRequestStatus.class)
            .ignoreDependency(CorrectionRestMapper.class, ProposedChanges.class)
            .ignoreDependency(CorrectionRestMapper.class, ProposedBreak.class)
            .ignoreDependency(CorrectionRestMapper.class, PagedResult.class)
            .ignoreDependency(CorrectionController.class, CorrectionRequestStatus.class)
            .ignoreDependency(AuditEventRestMapper.class, AuditEvent.class)
            .ignoreDependency(AuditEventRestMapper.class, PagedResult.class)
            .ignoreDependency(WorkdayRestMapper.class, Workday.class)
            .ignoreDependency(WorkdayRestMapper.class, BreakEntry.class)
            .ignoreDependency(WorkdayRestMapper.class, WorkdayStatus.class)
            .ignoreDependency(WorkdayRestMapper.class, Clock.class)
            .ignoreDependency(WorkdayRestMapper.class, PagedResult.class)
            .ignoreDependency(ReportRestMapper.class, EmployeeDaySummary.class)
            .ignoreDependency(ReportRestMapper.class, TenantEmployeeSummary.class)
            .allowEmptyShould(true);
}
