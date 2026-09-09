package com.example.beetle.presentation.controller

import com.example.beetle.application.port.BudgetUseCase
import com.example.beetle.domain.model.BudgetId
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.presentation.dto.BudgetPerformanceResponse
import com.example.beetle.presentation.dto.BudgetResponse
import com.example.beetle.presentation.dto.RegisterBudgetRequest
import com.example.beetle.presentation.dto.UpdateBudgetRequest
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.YearMonth

/**
 * 예산 REST 컨트롤러.
 *
 * 도메인 모델을 직접 반환하지 않고 응답 DTO 로 변환한다 (CLAUDE.md 3.2).
 */
@RestController
@RequestMapping("/api/budgets")
class BudgetController(
    private val budgetUseCase: BudgetUseCase,
) {

    @PostMapping
    fun register(
        @Valid @RequestBody request: RegisterBudgetRequest,
    ): ResponseEntity<BudgetResponse> {
        val budget = budgetUseCase.register(request.toCommand())
        val response = BudgetResponse.from(budget)
        return ResponseEntity.created(URI.create("/api/budgets/${response.id}")).body(response)
    }

    @GetMapping
    fun getAll(
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM") month: YearMonth,
    ): List<BudgetResponse> = budgetUseCase.getAll(month).map(BudgetResponse::from)

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): BudgetResponse =
        BudgetResponse.from(budgetUseCase.getById(BudgetId(id)))

    /** 예산 대비 실적. 예산은 지출 통제 개념이므로 기본 기준일 축은 소비일이다. */
    @GetMapping("/performance")
    fun performance(
        @RequestParam(defaultValue = "SPENT") basis: DateBasis,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM") month: YearMonth,
    ): BudgetPerformanceResponse =
        BudgetPerformanceResponse.from(budgetUseCase.performance(basis, month))

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateBudgetRequest,
    ): BudgetResponse =
        BudgetResponse.from(budgetUseCase.update(request.toCommand(BudgetId(id))))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = budgetUseCase.delete(BudgetId(id))
}
