package com.example.beetle.domain.repository

import com.example.beetle.domain.model.PaymentMethod
import com.example.beetle.domain.model.PaymentMethodId

/**
 * 결제 수단 애그리거트의 아웃바운드 포트.
 */
interface PaymentMethodRepository {

    fun save(paymentMethod: PaymentMethod): PaymentMethod

    fun findById(id: PaymentMethodId): PaymentMethod?

    fun findAll(): List<PaymentMethod>

    fun existsByName(name: String): Boolean

    fun deleteById(id: PaymentMethodId)
}
