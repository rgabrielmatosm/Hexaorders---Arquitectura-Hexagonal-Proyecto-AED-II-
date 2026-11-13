package com.polymer.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.polymer", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {
    @ArchTest
    static final ArchRule CORE_HAS_NO_FRAMEWORK_OR_INFRASTRUCTURE = noClasses()
            .that().resideInAnyPackage("..domain..", "..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "javax.sql..", "java.sql..",
                    "com.fasterxml..", "io.micrometer..", "com.zaxxer..", "..adapter..", "..config..");

    @ArchTest
    static final ArchRule DOMAIN_IS_INNERMOST = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapter..", "..config..");

    @ArchTest
    static final ArchRule ADAPTERS_ARE_INDEPENDENT = noClasses().that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out..");

    /** New rule (v3): driving adapters talk to the core only through input ports, never to output ports. */
    @ArchTest
    static final ArchRule DRIVING_ADAPTERS_USE_INPUT_PORTS_ONLY = noClasses().that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..application.port.out..");

    // ---- Contratos (puertos) e implementaciones (adaptadores y servicios) ------------------------------

    /** Un puerto es un contrato: interfaz (o un registro de datos que viaja por él), nunca lógica. */
    @ArchTest
    static final ArchRule PORTS_ARE_CONTRACTS = classes().that().resideInAPackage("..application.port..")
            .and().areTopLevelClasses()
            .should().beInterfaces().orShould().beRecords();

    /** Los puertos no conocen sus implementaciones: ni servicios de aplicación ni adaptadores. */
    @ArchTest
    static final ArchRule PORTS_DO_NOT_DEPEND_ON_IMPLEMENTATIONS = noClasses().that().resideInAPackage("..application.port..")
            .should().dependOnClassesThat().resideInAnyPackage("..application.service..", "..adapter..", "..config..");

    /** Cada adaptador de salida implementa un puerto de salida (Jdbc es solo una utilidad compartida). */
    @ArchTest
    static final ArchRule OUTPUT_ADAPTERS_IMPLEMENT_OUTPUT_PORTS = classes().that().resideInAPackage("..adapter.out..")
            .and().areTopLevelClasses().and().doNotHaveSimpleName("Jdbc")
            .should().implement(implementsInterfaceIn("..application.port.out.."));

    /** Cada servicio de casos de uso implementa un puerto de entrada (PricingService es lógica interna de precios). */
    @ArchTest
    static final ArchRule USE_CASES_IMPLEMENT_INPUT_PORTS = classes().that().resideInAPackage("..application.service..")
            .and().haveSimpleNameEndingWith("Service").and().doNotHaveSimpleName("PricingService")
            .should().implement(implementsInterfaceIn("..application.port.in.."));

    private static DescribedPredicate<JavaClass> implementsInterfaceIn(String packageIdentifier) {
        return JavaClass.Predicates.resideInAPackage(packageIdentifier).and(JavaClass.Predicates.INTERFACES)
                .as("una interfaz de " + packageIdentifier);
    }
}
