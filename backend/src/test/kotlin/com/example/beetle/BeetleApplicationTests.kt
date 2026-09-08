package com.example.beetle

import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.repository.CategoryRepository
import com.example.beetle.domain.repository.PaymentMethodRepository
import com.example.beetle.support.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

/**
 * 전체 애플리케이션 컨텍스트 스모크 테스트.
 *
 * CLAUDE.md 5.2절: `@SpringBootTest` 는 최소 개수만 유지한다.
 * 도메인 규칙 검증은 Spring 없는 순수 단위 테스트로 수행한다.
 *
 * 이 테스트는 시드 데이터(`db/seed`)를 포함한 전체 마이그레이션이 적용되는 유일한
 * 지점이므로, 시드 SQL 의 정합성도 함께 검증한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
@DisplayName("애플리케이션 컨텍스트")
class BeetleApplicationTests {

    @field:Autowired
    private lateinit var categoryRepository: CategoryRepository

    @field:Autowired
    private lateinit var paymentMethodRepository: PaymentMethodRepository

    @Test
    fun `컨텍스트가 정상적으로 구성된다`() {
        // 빈 구성 오류, 순환 참조, JPA 매핑 불일치가 있으면 여기서 실패한다.
    }

    @Test
    fun `시드 데이터가 도메인 불변식을 만족한 상태로 적재된다`() {
        // when: 매퍼가 도메인으로 복원하는 과정에서 불변식이 검증된다
        val categories = categoryRepository.findAll()

        // then
        assertThat(categories).isNotEmpty()
        assertThat(categories.map { it.name }).contains("급여", "월세", "식비", "계좌이체")

        // 지출 카테고리는 성격이 있어야 하고, 그 외에는 없어야 한다
        assertThat(categories.filter { it.type == CategoryType.EXPENSE })
            .isNotEmpty()
            .allSatisfy { assertThat(it.nature).isNotNull() }
        assertThat(categories.filter { it.type != CategoryType.EXPENSE })
            .isNotEmpty()
            .allSatisfy { assertThat(it.nature).isNull() }
    }

    @Test
    fun `시드 결제 수단은 현금만 포함한다`() {
        // given: 신용카드는 결제일/마감일이 개인마다 달라 임의로 심지 않는다
        val paymentMethods = paymentMethodRepository.findAll()

        // then
        assertThat(paymentMethods).singleElement()
            .satisfies({
                assertThat(it.name).isEqualTo("현금")
                assertThat(it.isImmediateSettlement).isTrue()
            })
    }
}
