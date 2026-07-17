package com.tfp.timetracking.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * CONTEXT-GLOBAL §4 / T106 regla 2: los controladores ({@code interfaces.rest})
 * no tienen logica de negocio ni acceso directo a repositorios; solo delegan
 * en casos de uso de {@code application}.
 */
@AnalyzeClasses(packages = "com.tfp.timetracking", importOptions = ImportOption.DoNotIncludeTests.class)
class RestLayerAccessTest {

    private static final DescribedPredicate<JavaClass> DOMAIN_REPOSITORY_PORTS =
            resideInAPackage("..domain..")
                    .and(simpleNameEndingWith("Repository"))
                    .as("interfaces de repositorio de dominio (*Repository)");

    @ArchTest
    static final ArchRule rest_should_not_access_infrastructure_persistence =
            noClasses().that().resideInAPackage("..interfaces.rest..")
                    .should().dependOnClassesThat().resideInAnyPackage("..infrastructure.persistence..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule rest_should_not_access_domain_repositories_directly =
            noClasses().that().resideInAPackage("..interfaces.rest..")
                    .should().dependOnClassesThat(DOMAIN_REPOSITORY_PORTS)
                    .allowEmptyShould(true);
}
