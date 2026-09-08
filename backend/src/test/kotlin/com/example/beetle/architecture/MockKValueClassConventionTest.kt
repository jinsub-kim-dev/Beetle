package com.example.beetle.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberFunctions

/**
 * MockK 의 `any()` 매처를 값 객체(`@JvmInline value class`) 파라미터에 사용하지 못하게 막는
 * 규약 테스트 (CLAUDE.md 5.4).
 *
 * ## 왜 필요한가
 * MockK 는 매처의 서명 값을 만들 때 값 객체를 **난수**로 생성한다. 이 프로젝트의 식별자
 * 값 객체는 "양수" 불변식을 갖기 때문에, 난수가 음수로 나오면 그 순간 테스트가 실패한다.
 * 즉 실행할 때마다 통과 여부가 달라지는 flaky 테스트가 된다.
 *
 * 실제로 개발 중 세 번 발생했으므로, 주석 규칙이 아니라 테스트로 차단한다.
 *
 * ## 검사 방식
 * 포트 인터페이스를 리플렉션으로 훑어 값 객체 파라미터를 받는 메서드를 수집하고,
 * 테스트 소스에서 그 메서드를 `any()` 와 함께 호출하는 지점을 찾는다.
 * 포트가 바뀌어도 이 테스트를 수정할 필요가 없다.
 *
 * ## 위반 시 대안
 * - 구체적인 값을 넘긴다. 예: `verify { repo.deleteById(CategoryId(1L)) }`
 * - 호출이 전혀 없어야 한다면 `confirmVerified(repo)` 로 검증한다.
 */
@DisplayName("MockK 값 객체 매처 규약")
class MockKValueClassConventionTest {

    @Test
    fun `값 객체 파라미터를 받는 포트 메서드가 탐지된다`() {
        // 탐지가 실패해 검사가 조용히 통과하는 상황을 막는다.
        assertThat(methodsWithValueClassParameters())
            .`as`("값 객체 파라미터를 받는 포트 메서드를 찾지 못했다")
            .isNotEmpty()
    }

    @Test
    fun `테스트 소스는 값 객체 파라미터에 any 매처를 사용하지 않는다`() {
        // given
        val riskyMethods = methodsWithValueClassParameters()
        val violations = mutableListOf<String>()

        // when
        testSourceFiles().forEach { file ->
            val source = file.readText()
            riskyMethods.forEach { methodName ->
                findAnyMatcherUsages(source, methodName).forEach { lineNumber ->
                    violations += "${file.name}:$lineNumber -> $methodName(... any() ...)"
                }
            }
        }

        // then
        assertThat(violations)
            .`as`(
                "MockK 는 값 객체 파라미터의 any() 매처를 난수로 생성하므로 ID 양수 불변식을 " +
                    "위반해 테스트가 간헐적으로 실패한다. 구체적인 값이나 confirmVerified 를 사용하라 " +
                    "(CLAUDE.md 5.4).",
            )
            .isEmpty()
    }

    /** 포트 인터페이스에서 값 객체를 파라미터로 받는 메서드 이름을 수집한다. */
    private fun methodsWithValueClassParameters(): Set<String> = PORT_INTERFACES
        .flatMap { it.declaredMemberFunctions }
        .filter { function ->
            function.parameters
                .drop(1) // 수신 객체 제외
                .any { parameter -> (parameter.type.classifier as? KClass<*>)?.isValue == true }
        }
        .map { it.name }
        .toSet()

    private fun testSourceFiles(): List<File> = File(TEST_SOURCE_ROOT)
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()
        .also {
            check(it.isNotEmpty()) { "테스트 소스를 찾지 못했다: $TEST_SOURCE_ROOT" }
        }

    /**
     * `source` 에서 `methodName(...)` 호출의 괄호 범위 안에 `any(` 또는 `any<` 가 있는
     * 지점의 1-기반 줄 번호를 반환한다.
     */
    private fun findAnyMatcherUsages(source: String, methodName: String): List<Int> {
        val results = mutableListOf<Int>()
        val callPattern = Regex("""\.${Regex.escape(methodName)}\s*\(""")

        callPattern.findAll(source).forEach { match ->
            val openParenIndex = match.range.last
            val closeParenIndex = matchingCloseParen(source, openParenIndex) ?: return@forEach
            val arguments = source.substring(openParenIndex + 1, closeParenIndex)
            if (ANY_MATCHER_PATTERN.containsMatchIn(arguments)) {
                results += source.take(openParenIndex).count { it == '\n' } + 1
            }
        }
        return results
    }

    /** `openIndex` 위치의 여는 괄호에 대응하는 닫는 괄호 위치를 반환한다. */
    private fun matchingCloseParen(source: String, openIndex: Int): Int? {
        var depth = 0
        for (index in openIndex until source.length) {
            when (source[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return null
    }

    private companion object {
        const val TEST_SOURCE_ROOT = "src/test/kotlin"

        val ANY_MATCHER_PATTERN = Regex("""\bany\s*[(<]""")

        /** 값 객체를 파라미터로 받는 포트 인터페이스들. */
        val PORT_INTERFACES: List<KClass<*>> = listOf(
            com.example.beetle.domain.repository.CategoryRepository::class,
            com.example.beetle.domain.repository.PaymentMethodRepository::class,
            com.example.beetle.domain.repository.TransactionRepository::class,
            com.example.beetle.domain.repository.InstallmentPlanRepository::class,
            com.example.beetle.application.port.CategoryUseCase::class,
            com.example.beetle.application.port.PaymentMethodUseCase::class,
            com.example.beetle.application.port.TransactionUseCase::class,
            com.example.beetle.application.port.InstallmentPlanUseCase::class,
        )
    }
}
