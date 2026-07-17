package com.tfp.timetracking.architecture;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

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
            .allowEmptyShould(true);
}
