package com.example.beetle.presentation.controller

import com.example.beetle.application.port.RegisterTransactionCommand
import com.example.beetle.application.port.TransactionSearchQuery
import com.example.beetle.application.port.TransactionUseCase
import com.example.beetle.application.port.UpdateTransactionCommand
import com.example.beetle.domain.exception.DomainStateException
import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.domain.exception.ResourceNotFoundException
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.TransactionId
import com.example.beetle.fixture.installmentTransaction
import com.example.beetle.fixture.transaction
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@WebMvcTest(TransactionController::class)
@Import(TransactionControllerTest.MockUseCaseConfiguration::class)
@DisplayName("TransactionController API")
class TransactionControllerTest {

    @field:Autowired
    private lateinit var mockMvc: MockMvc

    @field:Autowired
    private lateinit var transactionUseCase: TransactionUseCase

    @BeforeEach
    fun resetMocks() {
        clearMocks(transactionUseCase)
    }

    @Nested
    @DisplayName("POST /api/transactions")
    inner class Register {

        @Test
        fun `거래를 등록하면 201 과 Location 헤더를 반환한다`() {
            // given
            every { transactionUseCase.register(any()) } returns
                transaction(amount = 45_000L, memo = "이마트", id = 1L)

            // when & then
            mockMvc.perform(
                post("/api/transactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"categoryId":1,"paymentMethodId":1,"amount":45000,
                         "spentDate":"2026-01-10","memo":"이마트"}
                        """.trimIndent(),
                    ),
            )
                .andExpect(status().isCreated)
                .andExpect(header().string("Location", "/api/transactions/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.amount").value(45000))
                .andExpect(jsonPath("$.memo").value("이마트"))
                .andExpect(jsonPath("$.settled").value(false))
                .andExpect(jsonPath("$.installment").value(false))
        }

        @Test
        fun `날짜는 ISO-8601 문자열로 직렬화된다`() {
            // given
            every { transactionUseCase.register(any()) } returns
                transaction(
                    spentDate = LocalDate.of(2026, 1, 10),
                    billDate = LocalDate.of(2026, 2, 14),
                    id = 1L,
                )

            // when & then: 타임스탬프 숫자가 아니라 문자열이어야 한다
            mockMvc.perform(
                post("/api/transactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"categoryId":1,"paymentMethodId":1,"amount":10000,"spentDate":"2026-01-10"}""",
                    ),
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.spentDate").value("2026-01-10"))
                .andExpect(jsonPath("$.billDate").value("2026-02-14"))
        }

        @Test
        fun `요청 값이 명령 객체로 그대로 전달된다`() {
            // given
            every { transactionUseCase.register(any()) } returns transaction(id = 1L)

            // when
            mockMvc.perform(
                post("/api/transactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"categoryId":2,"paymentMethodId":3,"amount":45000,
                         "spentDate":"2026-01-10","memo":"이마트","billDate":"2026-03-20",
                         "settled":true,"excludedFromStats":true}
                        """.trimIndent(),
                    ),
            ).andExpect(status().isCreated)

            // then
            verify {
                transactionUseCase.register(
                    RegisterTransactionCommand(
                        categoryId = CategoryId(2L),
                        paymentMethodId = PaymentMethodId(3L),
                        amount = Money.of(45_000),
                        spentDate = LocalDate.of(2026, 1, 10),
                        memo = "이마트",
                        billDate = LocalDate.of(2026, 3, 20),
                        isSettled = true,
                        isExcludedFromStats = true,
                    ),
                )
            }
        }

        @Test
        fun `할부 회차 거래는 회차 정보가 응답에 포함된다`() {
            // given
            every { transactionUseCase.register(any()) } returns
                installmentTransaction(sequence = 3, planId = 7L, id = 1L)

            // when & then
            mockMvc.perform(
                post("/api/transactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"categoryId":1,"paymentMethodId":1,"amount":100000,"spentDate":"2026-01-10"}""",
                    ),
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.installment").value(true))
                .andExpect(jsonPath("$.installmentPlanId").value(7))
                .andExpect(jsonPath("$.installmentSequence").value(3))
        }

        @Test
        fun `필수 필드가 없으면 400 과 필드 오류를 반환한다`() {
            // when & then
            mockMvc.perform(
                post("/api/transactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"amount":10000}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(3))

            verify(exactly = 0) { transactionUseCase.register(any()) }
        }

        @Test
        fun `금액이 0 이하이면 400 을 반환한다`() {
            // when & then
            mockMvc.perform(
                post("/api/transactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"categoryId":1,"paymentMethodId":1,"amount":0,"spentDate":"2026-01-10"}""",
                    ),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"))
        }

        @Test
        fun `잘못된 날짜 형식은 400 을 반환한다`() {
            // when & then
            mockMvc.perform(
                post("/api/transactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"categoryId":1,"paymentMethodId":1,"amount":1000,"spentDate":"2026-13-99"}""",
                    ),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
        }

        @Test
        fun `존재하지 않는 카테고리를 참조하면 404 를 반환한다`() {
            // given
            every { transactionUseCase.register(any()) } throws
                ResourceNotFoundException("카테고리", 99L)

            // when & then
            mockMvc.perform(
                post("/api/transactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"categoryId":99,"paymentMethodId":1,"amount":1000,"spentDate":"2026-01-10"}""",
                    ),
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
        }

        @Test
        fun `청구일이 소비일보다 앞서면 400 을 반환한다`() {
            // given
            every { transactionUseCase.register(any()) } throws
                InvariantViolationException("청구일은 소비일보다 앞설 수 없습니다.")

            // when & then
            mockMvc.perform(
                post("/api/transactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"categoryId":1,"paymentMethodId":1,"amount":1000,
                         "spentDate":"2026-02-10","billDate":"2026-01-10"}
                        """.trimIndent(),
                    ),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVARIANT_VIOLATION"))
        }
    }

    @Nested
    @DisplayName("GET /api/transactions - 기준일 축 전환")
    inner class Search {

        @Test
        fun `기본 기준일은 소비일이다`() {
            // given
            every { transactionUseCase.search(any()) } returns listOf(transaction(id = 1L))

            // when
            mockMvc.perform(
                get("/api/transactions").param("from", "2026-01-01").param("to", "2026-01-31"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            // then
            verify {
                transactionUseCase.search(
                    TransactionSearchQuery(
                        DateBasis.SPENT, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                    ),
                )
            }
        }

        @Test
        fun `청구일 기준으로 조회한다`() {
            // given
            every { transactionUseCase.search(any()) } returns emptyList()

            // when
            mockMvc.perform(
                get("/api/transactions")
                    .param("basis", "BILL")
                    .param("from", "2026-02-01")
                    .param("to", "2026-02-28"),
            ).andExpect(status().isOk)

            // then
            verify {
                transactionUseCase.search(
                    TransactionSearchQuery(
                        DateBasis.BILL, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                    ),
                )
            }
        }

        @Test
        fun `카테고리와 결제 수단으로 필터링한다`() {
            // given
            every { transactionUseCase.search(any()) } returns emptyList()

            // when
            mockMvc.perform(
                get("/api/transactions")
                    .param("from", "2026-01-01")
                    .param("to", "2026-01-31")
                    .param("categoryId", "2")
                    .param("paymentMethodId", "3"),
            ).andExpect(status().isOk)

            // then
            verify {
                transactionUseCase.search(
                    TransactionSearchQuery(
                        basis = DateBasis.SPENT,
                        from = LocalDate.of(2026, 1, 1),
                        to = LocalDate.of(2026, 1, 31),
                        categoryId = CategoryId(2L),
                        paymentMethodId = PaymentMethodId(3L),
                    ),
                )
            }
        }

        @Test
        fun `기간 파라미터가 없으면 400 을 반환한다`() {
            mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
        }

        @Test
        fun `지원하지 않는 HTTP 메서드는 405 를 반환한다`() {
            // MVC 표준 예외가 500 으로 뭉개지지 않고 자체 상태 코드로 응답되는지 확인한다
            mockMvc.perform(patch("/api/transactions"))
                .andExpect(status().isMethodNotAllowed)
                .andExpect(jsonPath("$.code").value("REQUEST_ERROR"))
        }

        @Test
        fun `알 수 없는 기준일 값은 400 을 반환한다`() {
            mockMvc.perform(
                get("/api/transactions")
                    .param("basis", "UNKNOWN")
                    .param("from", "2026-01-01")
                    .param("to", "2026-01-31"),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
        }

        @Test
        fun `시작일이 종료일보다 늦으면 400 을 반환한다`() {
            // given
            every { transactionUseCase.search(any()) } throws
                InvariantViolationException("조회 시작일이 종료일보다 늦습니다.")

            // when & then
            mockMvc.perform(
                get("/api/transactions").param("from", "2026-02-01").param("to", "2026-01-01"),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVARIANT_VIOLATION"))
        }

        @Test
        fun `식별자로 단건 조회한다`() {
            // given
            every { transactionUseCase.getById(TransactionId(1L)) } returns
                transaction(amount = 45_000L, id = 1L)

            // when & then
            mockMvc.perform(get("/api/transactions/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.amount").value(45000))
        }

        @Test
        fun `존재하지 않는 거래는 404 를 반환한다`() {
            // given
            every { transactionUseCase.getById(TransactionId(99L)) } throws
                ResourceNotFoundException("거래 내역", 99L)

            // when & then
            mockMvc.perform(get("/api/transactions/99"))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("수정과 상태 변경")
    inner class Modify {

        @Test
        fun `금액과 메모를 수정한다`() {
            // given
            every { transactionUseCase.update(any()) } returns
                transaction(amount = 50_000L, memo = "정정", id = 1L)

            // when & then
            mockMvc.perform(
                patch("/api/transactions/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"amount":50000,"memo":"정정"}"""),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.amount").value(50000))
                .andExpect(jsonPath("$.memo").value("정정"))

            verify {
                transactionUseCase.update(
                    UpdateTransactionCommand(
                        id = TransactionId(1L),
                        amount = Money.of(50_000),
                        memo = "정정",
                    ),
                )
            }
        }

        @Test
        fun `clearMemo 플래그가 명령으로 전달된다`() {
            // given
            every { transactionUseCase.update(any()) } returns transaction(id = 1L)

            // when
            mockMvc.perform(
                patch("/api/transactions/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"clearMemo":true}"""),
            ).andExpect(status().isOk)

            // then
            verify {
                transactionUseCase.update(
                    UpdateTransactionCommand(id = TransactionId(1L), clearMemo = true),
                )
            }
        }

        @Test
        fun `결제 완료된 거래 수정은 409 를 반환한다`() {
            // given
            every { transactionUseCase.update(any()) } throws
                DomainStateException("결제 완료된 거래의 금액은 정정할 수 없습니다.")

            // when & then
            mockMvc.perform(
                patch("/api/transactions/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"amount":50000}"""),
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("DOMAIN_STATE_CONFLICT"))
        }

        @Test
        fun `결제 완료로 표시한다`() {
            // given
            every { transactionUseCase.settle(TransactionId(1L)) } returns
                transaction(isSettled = true, id = 1L)

            // when & then
            mockMvc.perform(post("/api/transactions/1/settlement"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.settled").value(true))
        }

        @Test
        fun `이미 결제 완료된 거래는 409 를 반환한다`() {
            // given
            every { transactionUseCase.settle(TransactionId(1L)) } throws
                DomainStateException("이미 결제 완료된 거래입니다.")

            // when & then
            mockMvc.perform(post("/api/transactions/1/settlement"))
                .andExpect(status().isConflict)
        }

        @Test
        fun `결제 완료를 되돌린다`() {
            // given
            every { transactionUseCase.unsettle(TransactionId(1L)) } returns
                transaction(isSettled = false, id = 1L)

            // when & then
            mockMvc.perform(delete("/api/transactions/1/settlement"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.settled").value(false))
        }

        @Test
        fun `통계 집계에서 제외한다`() {
            // given
            every { transactionUseCase.changeStatsExclusion(TransactionId(1L), true) } returns
                transaction(isExcludedFromStats = true, id = 1L)

            // when & then
            mockMvc.perform(
                patch("/api/transactions/1/stats-exclusion")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"excluded":true}"""),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.excludedFromStats").value(true))
        }

        @Test
        fun `통계 제외를 해제한다`() {
            // given
            every { transactionUseCase.changeStatsExclusion(TransactionId(1L), false) } returns
                transaction(isExcludedFromStats = false, id = 1L)

            // when & then
            mockMvc.perform(
                patch("/api/transactions/1/stats-exclusion")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"excluded":false}"""),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.excludedFromStats").value(false))
        }

        @Test
        fun `excluded 값이 없으면 400 을 반환한다`() {
            mockMvc.perform(
                patch("/api/transactions/1/stats-exclusion")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        }

        @Test
        fun `삭제하면 204 를 반환한다`() {
            // given
            every { transactionUseCase.delete(TransactionId(1L)) } returns Unit

            // when & then
            mockMvc.perform(delete("/api/transactions/1"))
                .andExpect(status().isNoContent)
        }
    }

    @TestConfiguration
    class MockUseCaseConfiguration {
        @Bean
        fun transactionUseCase(): TransactionUseCase = mockk()
    }
}
