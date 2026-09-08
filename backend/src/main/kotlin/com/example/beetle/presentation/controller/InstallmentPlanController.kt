package com.example.beetle.presentation.controller

import com.example.beetle.application.port.InstallmentPlanUseCase
import com.example.beetle.domain.model.InstallmentPlanId
import com.example.beetle.presentation.dto.CancelInstallmentPlanResponse
import com.example.beetle.presentation.dto.InstallmentPlanDetailResponse
import com.example.beetle.presentation.dto.InstallmentPlanResponse
import com.example.beetle.presentation.dto.RegisterInstallmentPlanRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * 할부 계획 REST 컨트롤러.
 *
 * 등록 시 회차 거래가 함께 생성되어 응답에 포함된다.
 */
@RestController
@RequestMapping("/api/installment-plans")
class InstallmentPlanController(
    private val installmentPlanUseCase: InstallmentPlanUseCase,
) {

    @PostMapping
    fun register(
        @Valid @RequestBody request: RegisterInstallmentPlanRequest,
    ): ResponseEntity<InstallmentPlanDetailResponse> {
        val detail = installmentPlanUseCase.register(request.toCommand())
        val response = InstallmentPlanDetailResponse.from(detail)
        return ResponseEntity
            .created(URI.create("/api/installment-plans/${response.plan.id}"))
            .body(response)
    }

    @GetMapping
    fun getAll(): List<InstallmentPlanResponse> =
        installmentPlanUseCase.getAll().map(InstallmentPlanResponse::from)

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): InstallmentPlanDetailResponse =
        InstallmentPlanDetailResponse.from(
            installmentPlanUseCase.getById(InstallmentPlanId(id)),
        )

    /** 중도 해지한다. 미정산 회차만 삭제되고 이미 출금된 회차는 기록으로 남는다. */
    @DeleteMapping("/{id}")
    fun cancel(@PathVariable id: Long): CancelInstallmentPlanResponse =
        CancelInstallmentPlanResponse.from(
            installmentPlanUseCase.cancel(InstallmentPlanId(id)),
        )
}
