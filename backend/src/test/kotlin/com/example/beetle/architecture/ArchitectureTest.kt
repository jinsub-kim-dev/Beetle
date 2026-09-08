package com.example.beetle.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture

/**
 * CLAUDE.md 3절 "핵심 아키텍처 규칙"을 CI 단계에서 강제하는 적합성 테스트.
 *
 * 규칙 위반은 코드 리뷰가 아니라 이 테스트의 실패로 드러나야 한다.
 * 실행: ./gradlew test --tests '*ArchitectureTest'
 *
 * 주의: `@field:ArchTest` 로 타깃을 명시해야 한다. 본 프로젝트는 컴파일러 옵션
 * `-Xannotation-default-target=param-property` 를 사용하므로, 타깃을 생략하면
 * 어노테이션이 프로퍼티에만 붙어 ArchUnit 이 규칙 필드를 발견하지 못한다.
 */
@AnalyzeClasses(
    packages = [ArchitectureTest.BASE_PACKAGE],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class ArchitectureTest {

    // --- 규칙 1: 의존성 역전 (DIP) ---

    @field:ArchTest
    val 도메인은_다른_레이어를_의존하지_않는다: ArchRule =
        noClasses()
            .that().resideInAPackage("$DOMAIN..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("$APPLICATION..", "$INFRASTRUCTURE..", "$PRESENTATION..")
            .because("도메인은 가장 안쪽 레이어이므로 외부 레이어를 알아서는 안 된다 (CLAUDE.md 3.1)")
            .allowEmptyShould(true)

    @field:ArchTest
    val 도메인은_프레임워크_타입을_의존하지_않는다: ArchRule =
        noClasses()
            .that().resideInAPackage("$DOMAIN..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "jakarta.validation..",
                "tools.jackson..",
                "com.fasterxml.jackson..",
            )
            .because("도메인은 순수 Kotlin 이어야 한다 (CLAUDE.md 3.1)")
            .allowEmptyShould(true)

    @field:ArchTest
    val 애플리케이션은_인프라와_프레젠테이션을_의존하지_않는다: ArchRule =
        noClasses()
            .that().resideInAPackage("$APPLICATION..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("$INFRASTRUCTURE..", "$PRESENTATION..")
            .because("유스케이스는 아웃바운드 포트(domain/repository)를 통해서만 외부와 통신한다 (CLAUDE.md 2)")
            .allowEmptyShould(true)

    // --- 규칙 2: 모델 분리 ---

    @field:ArchTest
    val JPA_엔티티는_인프라_레이어_밖으로_노출되지_않는다: ArchRule =
        noClasses()
            .that().resideOutsideOfPackage("$INFRASTRUCTURE..")
            .should().dependOnClassesThat()
            .resideInAPackage("$INFRASTRUCTURE.persistence.entity..")
            .because("JPA 엔티티는 매퍼를 통해서만 도메인 모델로 변환된다 (CLAUDE.md 3.2)")
            .allowEmptyShould(true)

    @field:ArchTest
    val JPA_어노테이션은_인프라_레이어에서만_사용한다: ArchRule =
        noClasses()
            .that().resideOutsideOfPackage("$INFRASTRUCTURE..")
            .should().dependOnClassesThat()
            .resideInAPackage("jakarta.persistence..")
            .because("영속성 관심사는 인프라 레이어에 격리한다 (CLAUDE.md 3.2)")
            .allowEmptyShould(true)

    // --- 규칙 3: 레이어 의존 방향 ---

    @field:ArchTest
    val 레이어_의존_방향은_안쪽을_향한다: ArchRule =
        layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer(DOMAIN_LAYER).definedBy("$DOMAIN..")
            .layer(APPLICATION_LAYER).definedBy("$APPLICATION..")
            .layer(INFRASTRUCTURE_LAYER).definedBy("$INFRASTRUCTURE..")
            .layer(PRESENTATION_LAYER).definedBy("$PRESENTATION..")
            .whereLayer(PRESENTATION_LAYER).mayNotBeAccessedByAnyLayer()
            .whereLayer(INFRASTRUCTURE_LAYER).mayNotBeAccessedByAnyLayer()
            .whereLayer(APPLICATION_LAYER)
            .mayOnlyBeAccessedByLayers(PRESENTATION_LAYER, INFRASTRUCTURE_LAYER)
            .withOptionalLayers(true)

    // --- 규칙 4: 아웃바운드 포트 정의 형태 ---

    @field:ArchTest
    val 도메인_리포지토리는_인터페이스로만_정의한다: ArchRule =
        classes()
            .that().resideInAPackage("$DOMAIN.repository..")
            .should().beInterfaces()
            .because("아웃바운드 포트는 인터페이스로 정의하고 인프라에서 구현한다 (CLAUDE.md 2)")
            .allowEmptyShould(true)

    companion object {
        const val BASE_PACKAGE = "com.example.beetle"

        const val DOMAIN = "$BASE_PACKAGE.domain"
        const val APPLICATION = "$BASE_PACKAGE.application"
        const val INFRASTRUCTURE = "$BASE_PACKAGE.infrastructure"
        const val PRESENTATION = "$BASE_PACKAGE.presentation"

        const val DOMAIN_LAYER = "Domain"
        const val APPLICATION_LAYER = "Application"
        const val INFRASTRUCTURE_LAYER = "Infrastructure"
        const val PRESENTATION_LAYER = "Presentation"
    }
}
