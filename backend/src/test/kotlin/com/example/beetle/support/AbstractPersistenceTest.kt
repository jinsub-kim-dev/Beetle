package com.example.beetle.support

import com.example.beetle.domain.repository.CategoryRepository
import com.example.beetle.domain.repository.InstallmentPlanRepository
import com.example.beetle.domain.repository.PaymentMethodRepository
import com.example.beetle.domain.repository.TransactionRepository
import com.example.beetle.infrastructure.persistence.adapter.CategoryRepositoryAdapter
import com.example.beetle.infrastructure.persistence.adapter.InstallmentPlanRepositoryAdapter
import com.example.beetle.infrastructure.persistence.adapter.PaymentMethodRepositoryAdapter
import com.example.beetle.infrastructure.persistence.adapter.TransactionRepositoryAdapter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import

/**
 * 영속성 통합 테스트 기반 클래스.
 *
 * 실제 MySQL 컨테이너를 사용하므로, 이 테스트가 통과하면 다음이 함께 검증된다.
 * - Flyway 마이그레이션이 정상 적용된다
 * - `ddl-auto=validate` 로 JPA 매핑과 실제 스키마가 일치한다
 * - 매퍼 왕복(도메인 -> 엔티티 -> DB -> 도메인)에서 정보가 유실되지 않는다
 *
 * 주의: `@Autowired` 는 `@field:` 타깃을 명시해야 한다. 컴파일러 옵션
 * `-Xannotation-default-target=param-property` 때문에 타깃을 생략하면 어노테이션이
 * 코틀린 프로퍼티에만 붙어 스프링이 인식하지 못한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// @DataJpaTest 슬라이스는 Flyway 자동 구성을 포함하지 않는다. 명시적으로 넣어야
// 마이그레이션이 적용되고 ddl-auto=validate 검증이 의미를 갖는다.
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
@Import(
    TestcontainersConfiguration::class,
    CategoryRepositoryAdapter::class,
    PaymentMethodRepositoryAdapter::class,
    TransactionRepositoryAdapter::class,
    InstallmentPlanRepositoryAdapter::class,
)
abstract class AbstractPersistenceTest {

    @field:Autowired
    protected lateinit var categoryRepository: CategoryRepository

    @field:Autowired
    protected lateinit var paymentMethodRepository: PaymentMethodRepository

    @field:Autowired
    protected lateinit var transactionRepository: TransactionRepository

    @field:Autowired
    protected lateinit var installmentPlanRepository: InstallmentPlanRepository

    @field:Autowired
    protected lateinit var testEntityManager: TestEntityManager

    /** 영속성 컨텍스트를 비워, 이후 조회가 실제 DB 를 다시 읽도록 만든다. */
    protected fun flushAndClear() {
        testEntityManager.flush()
        testEntityManager.clear()
    }
}
