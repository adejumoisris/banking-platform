package com.rova.banking;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.*;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

@AnalyzeClasses(packages = "com.rova.banking", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
  @ArchTest
  static final ArchRule domainIsFrameworkIndependent =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta.persistence..",
              "..controller..",
              "..dto..",
              "..filter..",
              "..exception..",
              "..infrastructure..");

  @ArchTest
  static final ArchRule servicesUsePorts =
      noClasses()
          .that()
          .haveSimpleNameEndingWith("Service")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "jakarta.persistence..",
              "org.springframework.web..",
              "..controller..",
              "..dto..",
              "..filter..",
              "..exception..",
              "..infrastructure..");

  @ArchTest
  static final ArchRule controllersAvoidAdapters =
      noClasses()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infrastructure..");

  @ArchTest
  static final ArchRule entitiesEncapsulateState =
      fields()
          .that()
          .areDeclaredInClassesThat()
          .areAnnotatedWith(Entity.class)
          .should()
          .bePrivate();
}
