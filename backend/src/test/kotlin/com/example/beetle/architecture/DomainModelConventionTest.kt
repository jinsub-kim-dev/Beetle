package com.example.beetle.architecture

import com.example.beetle.domain.model.AggregateRoot
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.reflect.full.declaredMemberFunctions

/**
 * CLAUDE.md 4.2절 "엔티티와 값 객체의 구분" 규약을 강제하는 테스트.
 *
 * 하드코딩된 클래스 목록이 아니라 [AggregateRoot] 구현체를 스캔하므로,
 * 새 애그리거트를 추가해도 이 테스트가 자동으로 검사한다.
 */
@DisplayName("도메인 모델 규약")
class DomainModelConventionTest {

    private val aggregateRoots: List<JavaClass> = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages(DOMAIN_MODEL_PACKAGE)
        .filter { it.isAssignableTo(AggregateRoot::class.java) && !it.isInterface }

    @Test
    fun `애그리거트 루트가 하나 이상 탐지된다`() {
        // 스캔이 실패해 규약 검사가 조용히 통과하는 상황을 막는다.
        assertThat(aggregateRoots)
            .`as`("$DOMAIN_MODEL_PACKAGE 에서 AggregateRoot 구현체를 찾지 못했다")
            .isNotEmpty()
    }

    @Test
    fun `애그리거트 루트는 data class 로 선언하지 않는다`() {
        val softly = SoftAssertions()
        aggregateRoots.forEach { javaClass ->
            val kotlinClass = Class.forName(javaClass.name).kotlin
            softly.assertThat(kotlinClass.isData)
                .`as`(
                    "${javaClass.simpleName} 은 data class 여서는 안 된다. " +
                        "data class 의 equals 는 전체 필드 기반이며 copy() 가 불변식 검증을 우회한다 (CLAUDE.md 4.2)",
                )
                .isFalse()
        }
        softly.assertAll()
    }

    @Test
    fun `애그리거트 루트는 copy 메서드를 노출하지 않는다`() {
        val softly = SoftAssertions()
        aggregateRoots.forEach { javaClass ->
            val kotlinClass = Class.forName(javaClass.name).kotlin
            val hasCopy = kotlinClass.declaredMemberFunctions.any { it.name == "copy" }
            softly.assertThat(hasCopy)
                .`as`("${javaClass.simpleName} 은 copy() 를 노출해서는 안 된다 (불변식 우회 경로)")
                .isFalse()
        }
        softly.assertAll()
    }

    @Test
    fun `애그리거트 루트는 equals 와 hashCode 를 직접 구현한다`() {
        val softly = SoftAssertions()
        aggregateRoots.forEach { javaClass ->
            val declaredMethods = javaClass.methods.map { it.name }
            softly.assertThat(declaredMethods)
                .`as`("${javaClass.simpleName} 은 식별자 기반 equals/hashCode 를 직접 구현해야 한다 (CLAUDE.md 4.2)")
                .contains("equals", "hashCode")
        }
        softly.assertAll()
    }

    companion object {
        const val DOMAIN_MODEL_PACKAGE = "com.example.beetle.domain.model"
    }
}
