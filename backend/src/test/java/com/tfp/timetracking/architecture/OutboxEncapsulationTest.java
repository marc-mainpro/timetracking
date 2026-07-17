package com.tfp.timetracking.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * CONTEXT-GLOBAL §4 / T106 regla 5: las clases de infraestructura del modulo
 * {@code outbox} no son accedidas desde otros modulos salvo por sus puertos
 * (interfaces expuestas en {@code outbox.domain}/{@code outbox.application}).
 * Solo el propio modulo {@code outbox} puede usar directamente sus clases de
 * {@code infrastructure}.
 */
@AnalyzeClasses(packages = "com.tfp.timetracking", importOptions = ImportOption.DoNotIncludeTests.class)
class OutboxEncapsulationTest {

    @ArchTest
    static final ArchRule outbox_infrastructure_is_only_used_within_outbox =
            classes().that().resideInAPackage("..outbox.infrastructure..")
                    .should().onlyBeAccessed().byAnyPackage("..outbox..")
                    .allowEmptyShould(true);
}
