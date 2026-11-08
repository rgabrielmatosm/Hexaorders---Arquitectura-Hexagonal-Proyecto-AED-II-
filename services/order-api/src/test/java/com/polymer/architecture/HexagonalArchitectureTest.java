package com.polymer.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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
}
