package com.example.beetle.presentation.controller

import com.example.beetle.application.port.CancelInstallmentPlanResult
import com.example.beetle.application.port.InstallmentPlanDetail
import com.example.beetle.application.port.InstallmentPlanUseCase
import com.example.beetle.application.port.RegisterInstallmentPlanCommand
import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.domain.exception.ResourceNotFoundException
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.InstallmentPlan
import com.example.beetle.domain.model.InstallmentPlanId
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.fixture.installmentTransaction
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@WebMvcTest(InstallmentPlanController::class)
@Import(InstallmentPlanControllerTest.MockUseCaseConfiguration::class)
@DisplayName("InstallmentPlanController API")
class InstallmentPlanControllerTest {

    @field:Autowired
    private lateinit var mockMvc: MockMvc

    @field:Autowired
    private lateinit var installmentPlanUseCase: InstallmentPlanUseCase

    @BeforeEach
    fun resetMocks() {
        clearMocks(installmentPlanUseCase)
    }

    private fun plan(
        totalAmount: Long = 1_000_000L,
        months: Int = 3,
        merchant: String = "삼성전자 냉장고",
        id: Long = 1L,
    ): InstallmentPlan = InstallmentPlan.reconstitute(
        id = InstallmentPlanId(id),
        categoryId = CategoryId(1L),
        paymentMethodId = PaymentMethodId(1L),
        totalAmount = Money.of(totalAmount),
        installmentMonths = months,
        merchant = merchant,
        spentDate = LocalDate.of(2026, 1, 10),
    )

    @Nested
    @DisplayName("POST /api/installment-plans")
    inner class Register {

        @Test
        fun `할부 계획을 등록하면 201 과 회차 거래가 함께 반환된다`() {
            // given
            every { installmentPlanUseCase.register(any()) } returns InstallmentPlanDetail(
                plan = plan(totalAmount = 1_000_000L, months = 3),
                parts = listOf(
                    installmentTransaction(sequence = 1, amount = 333_334L, id = 1L),
                    installmentTransaction(sequence = 2, amount = 333_333L, id = 2L),
                    installmentTransaction(sequence = 3, amount = 333_333L, id = 3L),
                ),
            )

            // when & then
            mockMvc.perform(
                post("/api/installment-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"categoryId":1,"paymentMethodId":1,"totalAmount":1000000,
                         "installmentMonths":3,"merchant":"삼성전자 냉장고","spentDate":"2026-01-10"}
                        """.trimIndent(),
                    ),
            )
                .andExpect(status().isCreated)
                .andExpect(header().string("Location", "/api/installment-plans/1"))
                .andExpect(jsonPath("$.plan.id").value(1))
                .andExpect(jsonPath("$.plan.totalAmount").value(1000000))
                .andExpect(jsonPath("$.plan.installmentMonths").value(3))
                .andExpect(jsonPath("$.plan.monthlyAmount").value(333333))
                .andExpect(jsonPath("$.plan.firstInstallmentAmount").value(333334))
                .andExpect(jsonPath("$.plan.spentDate").value("2026-01-10"))
                .andExpect(jsonPath("$.parts.length()").value(3))
                .andExpect(jsonPath("$.parts[0].amount").value(333334))
                .andExpect(jsonPath("$.parts[0].installmentSequence").value(1))
                .andExpect(jsonPath("$.parts[0].installment").value(true))
        }

        @Test
        fun `요청 값이 명령 객체로 그대로 전달된다`() {
            // given
            every { installmentPlanUseCase.register(any()) } returns
                InstallmentPlanDetail(plan(), emptyList())

            // when
            mockMvc.perform(
                post("/api/installment-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"categoryId":2,"paymentMethodId":3,"totalAmount":2400000,
                         "installmentMonths":24,"merchant":"  치과 임플란트  ",
                         "spentDate":"2026-01-05"}
                        """.trimIndent(),
                    ),
            ).andExpect(status().isCreated)

            // then
            verify {
                installmentPlanUseCase.register(
                    RegisterInstallmentPlanCommand(
                        categoryId = CategoryId(2L),
                        paymentMethodId = PaymentMethodId(3L),
                        totalAmount = Money.of(2_400_000),
                        installmentMonths = 24,
                        merchant = "치과 임플란트",
                        spentDate = LocalDate.of(2026, 1, 5),
                    ),
                )
            }
        }

        @Test
        fun `필수 필드가 없으면 400 을 반환한다`() {
            // when & then
            mockMvc.perform(
                post("/api/installment-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"merchant":"냉장고"}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(
                    jsonPath("$.fieldErrors[*].field").value(
                        containsInAnyOrder(
                            "categoryId", "paymentMethodId", "totalAmount",
                            "installmentMonths", "spentDate",
                        ),
                    ),
                )
        }

        @Test
        fun `1개월 할부는 400 을 반환한다`() {
            // when & then
            mockMvc.perform(
                post("/api/installment-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"categoryId":1,"paymentMethodId":1,"totalAmount":100000,
                         "installmentMonths":1,"merchant":"냉장고","spentDate":"2026-01-10"}
                        """.trimIndent(),
                    ),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.fieldErrors[0].field").value("installmentMonths"))
        }

        @Test
        fun `최대 개월 수를 넘으면 400 을 반환한다`() {
            // when & then
            mockMvc.perform(
                post("/api/installment-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"categoryId":1,"paymentMethodId":1,"totalAmount":100000,
                         "installmentMonths":61,"merchant":"냉장고","spentDate":"2026-01-10"}
                        """.trimIndent(),
                    ),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.fieldErrors[0].field").value("installmentMonths"))
        }

        @Test
        fun `총액이 0 이하이면 400 을 반환한다`() {
            mockMvc.perform(
                post("/api/installment-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"categoryId":1,"paymentMethodId":1,"totalAmount":0,
                         "installmentMonths":3,"merchant":"냉장고","spentDate":"2026-01-10"}
                        """.trimIndent(),
                    ),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.fieldErrors[0].field").value("totalAmount"))
        }

        @Test
        fun `월 납부액이 1원 미만이면 도메인 불변식 위반으로 400 이 된다`() {
            // given
            every { installmentPlanUseCase.register(any()) } throws
                InvariantViolationException("월 납부액이 1원 미만입니다.")

            // when & then
            mockMvc.perform(
                post("/api/installment-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"categoryId":1,"paymentMethodId":1,"totalAmount":2,
                         "installmentMonths":3,"merchant":"냉장고","spentDate":"2026-01-10"}
                        """.trimIndent(),
                    ),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVARIANT_VIOLATION"))
        }

        @Test
        fun `존재하지 않는 결제 수단을 참조하면 404 를 반환한다`() {
            // given
            every { installmentPlanUseCase.register(any()) } throws
                ResourceNotFoundException("결제 수단", 99L)

            // when & then
            mockMvc.perform(
                post("/api/installment-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"categoryId":1,"paymentMethodId":99,"totalAmount":100000,
                         "installmentMonths":3,"merchant":"냉장고","spentDate":"2026-01-10"}
                        """.trimIndent(),
                    ),
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
        }
    }

    @Nested
    @DisplayName("조회")
    inner class Query {

        @Test
        fun `전체 목록을 조회한다`() {
            // given
            every { installmentPlanUseCase.getAll() } returns listOf(
                plan(merchant = "냉장고", id = 1L),
                plan(merchant = "세탁기", id = 2L),
            )

            // when & then
            mockMvc.perform(get("/api/installment-plans"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].merchant").value("냉장고"))
        }

        @Test
        fun `단건 조회 시 회차 거래가 함께 반환된다`() {
            // given
            every { installmentPlanUseCase.getById(InstallmentPlanId(1L)) } returns
                InstallmentPlanDetail(
                    plan = plan(months = 3),
                    parts = (1..3).map { installmentTransaction(sequence = it, id = it.toLong()) },
                )

            // when & then
            mockMvc.perform(get("/api/installment-plans/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.plan.merchant").value("삼성전자 냉장고"))
                .andExpect(jsonPath("$.parts.length()").value(3))
        }

        @Test
        fun `존재하지 않는 계획은 404 를 반환한다`() {
            // given
            every { installmentPlanUseCase.getById(InstallmentPlanId(99L)) } throws
                ResourceNotFoundException("할부 계획", 99L)

            // when & then
            mockMvc.perform(get("/api/installment-plans/99"))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `0 이하의 식별자는 400 을 반환한다`() {
            mockMvc.perform(get("/api/installment-plans/0"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVARIANT_VIOLATION"))
        }
    }

    @Nested
    @DisplayName("DELETE /api/installment-plans/{id} - 중도 해지")
    inner class Cancel {

        @Test
        fun `모든 회차가 미정산이면 계획까지 삭제된다`() {
            // given
            every { installmentPlanUseCase.cancel(InstallmentPlanId(1L)) } returns
                CancelInstallmentPlanResult(
                    deletedPartCount = 3, keptSettledPartCount = 0, planDeleted = true,
                )

            // when & then
            mockMvc.perform(delete("/api/installment-plans/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.deletedPartCount").value(3))
                .andExpect(jsonPath("$.keptSettledPartCount").value(0))
                .andExpect(jsonPath("$.planDeleted").value(true))
        }

        @Test
        fun `이미 출금된 회차가 있으면 계획은 유지된다`() {
            // given
            every { installmentPlanUseCase.cancel(InstallmentPlanId(1L)) } returns
                CancelInstallmentPlanResult(
                    deletedPartCount = 2, keptSettledPartCount = 1, planDeleted = false,
                )

            // when & then
            mockMvc.perform(delete("/api/installment-plans/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.deletedPartCount").value(2))
                .andExpect(jsonPath("$.keptSettledPartCount").value(1))
                .andExpect(jsonPath("$.planDeleted").value(false))
        }

        @Test
        fun `존재하지 않는 계획 해지는 404 를 반환한다`() {
            // given
            every { installmentPlanUseCase.cancel(InstallmentPlanId(99L)) } throws
                ResourceNotFoundException("할부 계획", 99L)

            // when & then
            mockMvc.perform(delete("/api/installment-plans/99"))
                .andExpect(status().isNotFound)
        }
    }

    @TestConfiguration
    class MockUseCaseConfiguration {
        @Bean
        fun installmentPlanUseCase(): InstallmentPlanUseCase = mockk()
    }
}
