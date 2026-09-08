package com.example.beetle.presentation.controller

import com.example.beetle.application.port.CategoryUseCase
import com.example.beetle.application.port.RegisterCategoryCommand
import com.example.beetle.domain.exception.DomainStateException
import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.domain.exception.ResourceNotFoundException
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.fixture.expenseCategory
import com.example.beetle.fixture.incomeCategory
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(CategoryController::class)
@Import(CategoryControllerTest.MockUseCaseConfiguration::class)
@DisplayName("CategoryController API")
class CategoryControllerTest {

    @field:Autowired
    private lateinit var mockMvc: MockMvc

    @field:Autowired
    private lateinit var categoryUseCase: CategoryUseCase

    @BeforeEach
    fun resetMocks() {
        clearMocks(categoryUseCase)
    }

    @Nested
    @DisplayName("POST /api/categories")
    inner class Register {

        @Test
        fun `카테고리를 등록하면 201 과 Location 헤더를 반환한다`() {
            // given
            every { categoryUseCase.register(any()) } returns
                expenseCategory(name = "식비", nature = ExpenseNature.VARIABLE, id = 1L)

            // when & then
            mockMvc.perform(
                post("/api/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"식비","type":"EXPENSE","nature":"VARIABLE"}"""),
            )
                .andExpect(status().isCreated)
                .andExpect(header().string("Location", "/api/categories/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("식비"))
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.nature").value("VARIABLE"))
                .andExpect(jsonPath("$.fixedExpense").value(false))
        }

        @Test
        fun `수입 카테고리는 성격 없이 등록된다`() {
            // given
            every { categoryUseCase.register(any()) } returns incomeCategory(name = "급여", id = 2L)

            // when & then
            mockMvc.perform(
                post("/api/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"급여","type":"INCOME"}"""),
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value("급여"))
                // non_null 직렬화 정책에 따라 null 필드는 응답에서 생략된다
                .andExpect(jsonPath("$.nature").doesNotExist())
        }

        @Test
        fun `명령 객체에 요청 값이 그대로 전달된다`() {
            // given
            every { categoryUseCase.register(any()) } returns expenseCategory(id = 1L)

            // when
            mockMvc.perform(
                post("/api/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"  식비  ","type":"EXPENSE","nature":"FIXED"}"""),
            ).andExpect(status().isCreated)

            // then
            verify {
                categoryUseCase.register(
                    RegisterCategoryCommand("식비", CategoryType.EXPENSE, ExpenseNature.FIXED),
                )
            }
        }

        @Test
        fun `이름이 비어 있으면 400 과 필드 오류를 반환한다`() {
            // when & then
            mockMvc.perform(
                post("/api/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"","type":"EXPENSE","nature":"VARIABLE"}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))

            verify(exactly = 0) { categoryUseCase.register(any()) }
        }

        @Test
        fun `타입이 없으면 400 을 반환한다`() {
            // when & then
            mockMvc.perform(
                post("/api/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"식비"}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        }

        @Test
        fun `이름이 30자를 넘으면 400 을 반환한다`() {
            // given
            val tooLong = "가".repeat(31)

            // when & then
            mockMvc.perform(
                post("/api/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$tooLong","type":"EXPENSE","nature":"VARIABLE"}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
        }

        @Test
        fun `알 수 없는 타입 값은 400 을 반환한다`() {
            // when & then
            mockMvc.perform(
                post("/api/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"식비","type":"UNKNOWN"}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
        }

        @Test
        fun `이름이 중복되면 409 를 반환한다`() {
            // given
            every { categoryUseCase.register(any()) } throws
                DomainStateException("이미 존재하는 카테고리 이름입니다: 식비")

            // when & then
            mockMvc.perform(
                post("/api/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"식비","type":"EXPENSE","nature":"VARIABLE"}"""),
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("DOMAIN_STATE_CONFLICT"))
                .andExpect(jsonPath("$.message").value("이미 존재하는 카테고리 이름입니다: 식비"))
        }

        @Test
        fun `도메인 불변식 위반은 400 으로 변환된다`() {
            // given
            every { categoryUseCase.register(any()) } throws
                InvariantViolationException("지출 카테고리는 고정비/변동비 성격을 지정해야 합니다.")

            // when & then
            mockMvc.perform(
                post("/api/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"식비","type":"EXPENSE"}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVARIANT_VIOLATION"))
        }
    }

    @Nested
    @DisplayName("GET /api/categories")
    inner class Query {

        @Test
        fun `전체 목록을 조회한다`() {
            // given
            every { categoryUseCase.getAll(null) } returns
                listOf(expenseCategory(name = "식비", id = 1L), incomeCategory(name = "급여", id = 2L))

            // when & then
            mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("식비"))
                .andExpect(jsonPath("$[1].name").value("급여"))
        }

        @Test
        fun `타입으로 필터링해 조회한다`() {
            // given
            every { categoryUseCase.getAll(CategoryType.EXPENSE) } returns
                listOf(expenseCategory(name = "식비", id = 1L))

            // when & then
            mockMvc.perform(get("/api/categories").param("type", "EXPENSE"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))

            verify { categoryUseCase.getAll(CategoryType.EXPENSE) }
        }

        @Test
        fun `잘못된 타입 파라미터는 400 을 반환한다`() {
            // when & then
            mockMvc.perform(get("/api/categories").param("type", "WRONG"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
        }

        @Test
        fun `식별자로 단건 조회한다`() {
            // given
            every { categoryUseCase.getById(CategoryId(1L)) } returns
                expenseCategory(name = "월세", nature = ExpenseNature.FIXED, id = 1L)

            // when & then
            mockMvc.perform(get("/api/categories/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("월세"))
                .andExpect(jsonPath("$.fixedExpense").value(true))
        }

        @Test
        fun `존재하지 않는 식별자는 404 를 반환한다`() {
            // given
            every { categoryUseCase.getById(CategoryId(99L)) } throws
                ResourceNotFoundException("카테고리", 99L)

            // when & then
            mockMvc.perform(get("/api/categories/99"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
        }

        @Test
        fun `0 이하의 식별자는 400 을 반환한다`() {
            // when & then: CategoryId 값 객체의 불변식이 경로 변수 단계에서 걸린다
            mockMvc.perform(get("/api/categories/0"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVARIANT_VIOLATION"))
        }
    }

    @Nested
    @DisplayName("PATCH / DELETE /api/categories/{id}")
    inner class Modify {

        @Test
        fun `이름을 수정한다`() {
            // given
            every { categoryUseCase.update(any()) } returns
                expenseCategory(name = "외식비", id = 1L)

            // when & then
            mockMvc.perform(
                patch("/api/categories/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"외식비"}"""),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("외식비"))
        }

        @Test
        fun `성격을 수정한다`() {
            // given
            every { categoryUseCase.update(any()) } returns
                expenseCategory(name = "통신비", nature = ExpenseNature.FIXED, id = 1L)

            // when & then
            mockMvc.perform(
                patch("/api/categories/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nature":"FIXED"}"""),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.nature").value("FIXED"))
                .andExpect(jsonPath("$.fixedExpense").value(true))
        }

        @Test
        fun `빈 본문으로도 수정 요청이 가능하다`() {
            // given
            every { categoryUseCase.update(any()) } returns expenseCategory(id = 1L)

            // when & then
            mockMvc.perform(
                patch("/api/categories/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            ).andExpect(status().isOk)
        }

        @Test
        fun `삭제하면 204 를 반환한다`() {
            // given
            every { categoryUseCase.delete(CategoryId(1L)) } returns Unit

            // when & then
            mockMvc.perform(delete("/api/categories/1"))
                .andExpect(status().isNoContent)

            verify { categoryUseCase.delete(CategoryId(1L)) }
        }

        @Test
        fun `존재하지 않는 카테고리 삭제는 404 를 반환한다`() {
            // given
            every { categoryUseCase.delete(CategoryId(99L)) } throws
                ResourceNotFoundException("카테고리", 99L)

            // when & then
            mockMvc.perform(delete("/api/categories/99"))
                .andExpect(status().isNotFound)
        }
    }

    /**
     * 컨트롤러 슬라이스 테스트에 유스케이스 목(mock)을 제공한다.
     * MockK 는 스프링 부트의 목 어노테이션 지원 대상이 아니므로 직접 빈으로 등록한다.
     */
    @TestConfiguration
    class MockUseCaseConfiguration {
        @Bean
        fun categoryUseCase(): CategoryUseCase = mockk()
    }
}
