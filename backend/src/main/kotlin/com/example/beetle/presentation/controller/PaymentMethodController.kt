package com.example.beetle.presentation.controller

import com.example.beetle.application.port.PaymentMethodUseCase
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.presentation.dto.PaymentMethodResponse
import com.example.beetle.presentation.dto.RegisterPaymentMethodRequest
import com.example.beetle.presentation.dto.UpdatePaymentMethodRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * 결제 수단 REST 컨트롤러.
 */
@RestController
@RequestMapping("/api/payment-methods")
class PaymentMethodController(
    private val paymentMethodUseCase: PaymentMethodUseCase,
) {

    @PostMapping
    fun register(
        @Valid @RequestBody request: RegisterPaymentMethodRequest,
    ): ResponseEntity<PaymentMethodResponse> {
        val paymentMethod = paymentMethodUseCase.register(request.toCommand())
        val response = PaymentMethodResponse.from(paymentMethod)
        return ResponseEntity.created(URI.create("/api/payment-methods/${response.id}")).body(response)
    }

    @GetMapping
    fun getAll(): List<PaymentMethodResponse> =
        paymentMethodUseCase.getAll().map(PaymentMethodResponse::from)

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): PaymentMethodResponse =
        PaymentMethodResponse.from(paymentMethodUseCase.getById(PaymentMethodId(id)))

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdatePaymentMethodRequest,
    ): PaymentMethodResponse =
        PaymentMethodResponse.from(paymentMethodUseCase.update(request.toCommand(PaymentMethodId(id))))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = paymentMethodUseCase.delete(PaymentMethodId(id))
}
