package com.tfp.timetracking.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * CONTEXT-GLOBAL §4 / T106 regla 6: los eventos de dominio
 * ({@code ..domain.event..}) son hechos pasados e inmutables (todos sus
 * campos son {@code final}, ya sea en una clase clasica o en un
 * {@code record}) y no dependen de Spring ni de JPA.
 *
 * <p>Las reglas generales de {@link DomainPurityTest} ya cubren
 * {@code domain.event} (es un subpaquete de {@code ..domain..}); estas reglas
 * explicitas documentan y verifican el requisito especifico de eventos.
 */
@AnalyzeClasses(packages = "com.tfp.timetracking", importOptions = ImportOption.DoNotIncludeTests.class)
class DomainEventImmutabilityTest {

    @ArchTest
    static final ArchRule domain_event_fields_are_final =
            fields().that().areDeclaredInClassesThat().resideInAPackage("..domain.event..")
                    .should().beFinal()
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_events_do_not_depend_on_spring =
            noClasses().that().resideInAPackage("..domain.event..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_events_do_not_depend_on_jpa =
            noClasses().that().resideInAPackage("..domain.event..")
                    .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..")
                    .allowEmptyShould(true);
}
