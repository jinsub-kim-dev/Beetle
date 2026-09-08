package com.example.beetle.application.service

import com.example.beetle.application.port.PaymentMethodUseCase
import com.example.beetle.application.port.RegisterPaymentMethodCommand
import com.example.beetle.application.port.UpdatePaymentMethodCommand
import com.example.beetle.domain.exception.DomainStateException
import com.example.beetle.domain.exception.ResourceNotFoundException
import com.example.beetle.domain.model.PaymentMethod
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.repository.PaymentMethodRepository
import com.example.beetle.domain.repository.TransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 결제 수단 유스케이스 오케스트레이션.
 */
@Service
@Transactional(readOnly = true)
class PaymentMethodService(
    private val paymentMethodRepository: PaymentMethodRepository,
    private val transactionRepository: TransactionRepository,
) : PaymentMethodUseCase {

    @Transactional
    override fun register(command: RegisterPaymentMethodCommand): PaymentMethod {
        val name = command.name.trim()
        if (paymentMethodRepository.existsByName(name)) {
            throw DomainStateException("이미 존재하는 결제 수단 이름입니다: $name")
        }
        return paymentMethodRepository.save(
            PaymentMethod.create(
                name = name,
                type = command.type,
                paymentDay = command.paymentDay,
                closingDay = command.closingDay,
            ),
        )
    }

    @Transactional
    override fun update(command: UpdatePaymentMethodCommand): PaymentMethod {
        val paymentMethod = getById(command.id)

        val renamed = command.name
            ?.trim()
            ?.let { newName ->
                if (newName != paymentMethod.name && paymentMethodRepository.existsByName(newName)) {
                    throw DomainStateException("이미 존재하는 결제 수단 이름입니다: $newName")
                }
                paymentMethod.rename(newName)
            }
            ?: paymentMethod

        // 결제일/마감일 변경 가능 여부는 도메인이 판단한다.
        val withPaymentDay = command.paymentDay?.let(renamed::changePaymentDay) ?: renamed
        val updated = when {
            command.clearClosingDay -> withPaymentDay.changeClosingDay(null)
            command.closingDay != null -> withPaymentDay.changeClosingDay(command.closingDay)
            else -> withPaymentDay
        }

        return paymentMethodRepository.save(updated)
    }

    override fun getById(id: PaymentMethodId): PaymentMethod =
        paymentMethodRepository.findById(id) ?: throw ResourceNotFoundException("결제 수단", id)

    override fun getAll(): List<PaymentMethod> = paymentMethodRepository.findAll()

    @Transactional
    override fun delete(id: PaymentMethodId) {
        val paymentMethod = getById(id)
        // 참조 무결성: 거래가 남아 있으면 삭제를 막는다 (CategoryService 와 동일한 정책).
        if (transactionRepository.existsByPaymentMethodId(id)) {
            throw DomainStateException(
                "이 결제 수단을 사용하는 거래가 있어 삭제할 수 없습니다. 결제 수단: ${paymentMethod.name}",
            )
        }
        paymentMethodRepository.deleteById(id)
    }
}
