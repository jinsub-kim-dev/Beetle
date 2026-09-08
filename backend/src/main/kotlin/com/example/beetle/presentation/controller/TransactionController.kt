package com.example.beetle.presentation.controller

import com.example.beetle.application.port.TransactionSearchQuery
import com.example.beetle.application.port.TransactionUseCase
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.TransactionId
import com.example.beetle.presentation.dto.ChangeStatsExclusionRequest
import com.example.beetle.presentation.dto.RegisterTransactionRequest
import com.example.beetle.presentation.dto.TransactionResponse
import com.example.beetle.presentation.dto.UpdateTransactionRequest
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
import java.time.LocalDate

/**
 * 거래 내역 REST 컨트롤러.
 *
 * 조회는 `basis` 파라미터로 기준일 축을 전환한다 (PRD 2-①).
 * - `SPENT`: 소비일 기준. 소비 패턴 분석용
 * - `BILL`: 청구일 기준. 현금 흐름 통제용
 */
@RestController
@RequestMapping("/api/transactions")
class TransactionController(
    private val transactionUseCase: TransactionUseCase,
) {

    @PostMapping
    fun register(
        @Valid @RequestBody request: RegisterTransactionRequest,
    ): ResponseEntity<TransactionResponse> {
        val transaction = transactionUseCase.register(request.toCommand())
        val response = TransactionResponse.from(transaction)
        return ResponseEntity.created(URI.create("/api/transactions/${response.id}")).body(response)
    }

    @GetMapping
    fun search(
        @RequestParam(defaultValue = "SPENT") basis: DateBasis,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) categoryId: Long?,
        @RequestParam(required = false) paymentMethodId: Long?,
    ): List<TransactionResponse> = transactionUseCase.search(
        TransactionSearchQuery(
            basis = basis,
            from = from,
            to = to,
            categoryId = categoryId?.let(::CategoryId),
            paymentMethodId = paymentMethodId?.let(::PaymentMethodId),
        ),
    ).map(TransactionResponse::from)

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): TransactionResponse =
        TransactionResponse.from(transactionUseCase.getById(TransactionId(id)))

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateTransactionRequest,
    ): TransactionResponse =
        TransactionResponse.from(transactionUseCase.update(request.toCommand(TransactionId(id))))

    /** 청구일에 실제 출금이 일어났음을 표시한다. */
    @PostMapping("/{id}/settlement")
    fun settle(@PathVariable id: Long): TransactionResponse =
        TransactionResponse.from(transactionUseCase.settle(TransactionId(id)))

    /** 잘못 표시한 결제 완료를 되돌린다. */
    @DeleteMapping("/{id}/settlement")
    fun unsettle(@PathVariable id: Long): TransactionResponse =
        TransactionResponse.from(transactionUseCase.unsettle(TransactionId(id)))

    /** 통계 집계 제외 여부를 변경한다. (회사 전액 지원 통신비 등) */
    @PatchMapping("/{id}/stats-exclusion")
    fun changeStatsExclusion(
        @PathVariable id: Long,
        @Valid @RequestBody request: ChangeStatsExclusionRequest,
    ): TransactionResponse = TransactionResponse.from(
        transactionUseCase.changeStatsExclusion(TransactionId(id), request.excluded!!),
    )

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = transactionUseCase.delete(TransactionId(id))
}
