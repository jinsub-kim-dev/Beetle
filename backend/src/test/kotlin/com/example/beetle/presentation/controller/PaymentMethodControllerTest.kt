package com.example.beetle.presentation.controller

import com.example.beetle.application.port.PaymentMethodUseCase
import com.example.beetle.application.port.RegisterPaymentMethodCommand
import com.example.beetle.application.port.UpdatePaymentMethodCommand
import com.example.beetle.domain.exception.DomainStateException
import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.domain.exception.ResourceNotFoundException
import com.example.beetle.domain.model.DayOfMonthValue
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.PaymentMethodType
import com.example.beetle.fixture.cash
import com.example.beetle.fixture.creditCard
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

@WebMvcTest(PaymentMethodController::class)
@Import(PaymentMethodControllerTest.MockUseCaseConfiguration::class)
@DisplayName("PaymentMethodController API")
class PaymentMethodControllerTest {

    @field:Autowired
    private lateinit var mockMvc: MockMvc

    @field:Autowired
    private lateinit var paymentMethodUseCase: PaymentMethodUseCase

    @BeforeEach
    fun resetMocks() {
        clearMocks(paymentMethodUseCase)
    }

    @Nested
    @DisplayName("POST /api/payment-methods")
    inner class Register {

        @Test
        fun `신용카드를 등록하면 201 을 반환한다`() {
            // given
            every { paymentMethodUseCase.register(any()) } returns
                creditCard(name = "삼성카드", paymentDay = 14, closingDay = 1, id = 1L)

            // when & then
            mockMvc.perform(
                post("/api/payment-methods")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"name":"삼성카드","type":"CREDIT_CARD","paymentDay":14,"closingDay":1}""",
                    ),
            )
                .andExpect(status().isCreated)
                .andExpect(header().string("Location", "/api/payment-methods/1"))
                .andExpect(jsonPath("$.name").value("삼성카드"))
                .andExpect(jsonPath("$.type").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.paymentDay").value(14))
                .andExpect(jsonPath("$.closingDay").value(1))
                .andExpect(jsonPath("$.immediateSettlement").value(false))

            verify {
                paymentMethodUseCase.register(
                    RegisterPaymentMethodCommand(
                        "삼성카드",
                        PaymentMethodType.CREDIT_CARD,
                        DayOfMonthValue(14),
                        DayOfMonthValue(1),
                    ),
                )
            }
        }

        @Test
        fun `현금을 등록하면 결제일 필드가 응답에서 생략된다`() {
            // given
            every { paymentMethodUseCase.register(any()) } returns cash(name = "현금", id = 2L)

            // when & then
            mockMvc.perform(
                post("/api/payment-methods")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"현금","type":"CASH"}"""),
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.immediateSettlement").value(true))
                .andExpect(jsonPath("$.paymentDay").doesNotExist())
                .andExpect(jsonPath("$.closingDay").doesNotExist())
        }

        @Test
        fun `이름이 비어 있으면 400 을 반환한다`() {
            mockMvc.perform(
                post("/api/payment-methods")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"","type":"CASH"}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
        }

        @Test
        fun `타입이 없으면 400 을 반환한다`() {
            mockMvc.perform(
                post("/api/payment-methods")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"현금"}"""),
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `결제일이 31을 넘으면 400 을 반환한다`() {
            mockMvc.perform(
                post("/api/payment-methods")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"삼성카드","type":"CREDIT_CARD","paymentDay":32}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.fieldErrors[0].field").value("paymentDay"))
        }

        @Test
        fun `결제일이 0 이하이면 400 을 반환한다`() {
            mockMvc.perform(
                post("/api/payment-methods")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"삼성카드","type":"CREDIT_CARD","paymentDay":0}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.fieldErrors[0].field").value("paymentDay"))
        }

        @Test
        fun `결제일 없는 신용카드는 도메인 불변식 위반으로 400 이 된다`() {
            // given
            every { paymentMethodUseCase.register(any()) } throws
                InvariantViolationException("신용카드는 결제일(paymentDay)이 필요합니다.")

            // when & then
            mockMvc.perform(
                post("/api/payment-methods")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"현대카드","type":"CREDIT_CARD"}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVARIANT_VIOLATION"))
        }

        @Test
        fun `이름이 중복되면 409 를 반환한다`() {
            // given
            every { paymentMethodUseCase.register(any()) } throws
                DomainStateException("이미 존재하는 결제 수단 이름입니다: 현금")

            // when & then
            mockMvc.perform(
                post("/api/payment-methods")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"현금","type":"CASH"}"""),
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("DOMAIN_STATE_CONFLICT"))
        }
    }

    @Nested
    @DisplayName("GET /api/payment-methods")
    inner class Query {

        @Test
        fun `전체 목록을 조회한다`() {
            // given
            every { paymentMethodUseCase.getAll() } returns
                listOf(creditCard(name = "삼성카드", id = 1L), cash(id = 2L))

            // when & then
            mockMvc.perform(get("/api/payment-methods"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("삼성카드"))
        }

        @Test
        fun `식별자로 단건 조회한다`() {
            // given
            every { paymentMethodUseCase.getById(PaymentMethodId(1L)) } returns
                creditCard(name = "우리카드", paymentDay = 25, id = 1L)

            // when & then
            mockMvc.perform(get("/api/payment-methods/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("우리카드"))
                .andExpect(jsonPath("$.paymentDay").value(25))
        }

        @Test
        fun `존재하지 않는 식별자는 404 를 반환한다`() {
            // given
            every { paymentMethodUseCase.getById(PaymentMethodId(99L)) } throws
                ResourceNotFoundException("결제 수단", 99L)

            // when & then
            mockMvc.perform(get("/api/payment-methods/99"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
        }
    }

    @Nested
    @DisplayName("PATCH / DELETE /api/payment-methods/{id}")
    inner class Modify {

        @Test
        fun `결제일을 수정한다`() {
            // given
            every { paymentMethodUseCase.update(any()) } returns
                creditCard(name = "삼성카드", paymentDay = 25, id = 1L)

            // when & then
            mockMvc.perform(
                patch("/api/payment-methods/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"paymentDay":25}"""),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.paymentDay").value(25))

            verify {
                paymentMethodUseCase.update(
                    UpdatePaymentMethodCommand(
                        PaymentMethodId(1L), null, DayOfMonthValue(25), null, false,
                    ),
                )
            }
        }

        @Test
        fun `clearClosingDay 플래그가 명령으로 전달된다`() {
            // given
            every { paymentMethodUseCase.update(any()) } returns
                creditCard(name = "삼성카드", paymentDay = 14, id = 1L)

            // when
            mockMvc.perform(
                patch("/api/payment-methods/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"clearClosingDay":true}"""),
            ).andExpect(status().isOk)

            // then
            verify {
                paymentMethodUseCase.update(
                    UpdatePaymentMethodCommand(PaymentMethodId(1L), null, null, null, true),
                )
            }
        }

        @Test
        fun `즉시 결제 수단의 결제일 변경은 400 이 된다`() {
            // given
            every { paymentMethodUseCase.update(any()) } throws
                InvariantViolationException("CASH 에는 결제일을 지정할 수 없습니다.")

            // when & then
            mockMvc.perform(
                patch("/api/payment-methods/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"paymentDay":14}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVARIANT_VIOLATION"))
        }

        @Test
        fun `삭제하면 204 를 반환한다`() {
            // given
            every { paymentMethodUseCase.delete(PaymentMethodId(1L)) } returns Unit

            // when & then
            mockMvc.perform(delete("/api/payment-methods/1"))
                .andExpect(status().isNoContent)
        }
    }

    @TestConfiguration
    class MockUseCaseConfiguration {
        @Bean
        fun paymentMethodUseCase(): PaymentMethodUseCase = mockk()
    }
}
