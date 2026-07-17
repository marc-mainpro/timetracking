package com.tfp.timetracking.architecture;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * CONTEXT-GLOBAL §4 / T106 regla 4: sin ciclos de dependencia entre los
 * modulos (slices) {@code com.tfp.timetracking.(*)..}, p. ej. {@code identity},
 * {@code tenant}, {@code timetracking}, {@code corrections}, {@code reporting},
 * {@code audit}, {@code outbox}, {@code shared}.
 */
@AnalyzeClasses(packages = "com.tfp.timetracking", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleCyclesTest {

    @ArchTest
    static final ArchRule modules_are_free_of_cycles =
            slices().matching("com.tfp.timetracking.(*)..").should().beFreeOfCycles();
}
