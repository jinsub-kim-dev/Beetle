package com.example.beetle.infrastructure.persistence.mapper

import com.example.beetle.domain.model.DayOfMonthValue
import com.example.beetle.domain.model.PaymentMethod
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.infrastructure.persistence.entity.PaymentMethodJpaEntity

/**
 * 결제 수단의 도메인 모델과 JPA 엔티티 변환을 담당한다.
 */
object PaymentMethodMapper {

    fun toDomain(entity: PaymentMethodJpaEntity): PaymentMethod = PaymentMethod.reconstitute(
        id = PaymentMethodId(
            requireNotNull(entity.id) { "영속화되지 않은 엔티티는 도메인으로 변환할 수 없습니다." },
        ),
        name = entity.name,
        type = entity.type,
        paymentDay = entity.paymentDay?.let { DayOfMonthValue(it) },
        closingDay = entity.closingDay?.let { DayOfMonthValue(it) },
    )

    fun toEntity(paymentMethod: PaymentMethod): PaymentMethodJpaEntity = PaymentMethodJpaEntity(
        id = paymentMethod.id?.value,
        name = paymentMethod.name,
        type = paymentMethod.type,
        paymentDay = paymentMethod.paymentDay?.value,
        closingDay = paymentMethod.closingDay?.value,
    )

    fun applyTo(entity: PaymentMethodJpaEntity, paymentMethod: PaymentMethod) {
        entity.name = paymentMethod.name
        entity.type = paymentMethod.type
        entity.paymentDay = paymentMethod.paymentDay?.value
        entity.closingDay = paymentMethod.closingDay?.value
    }
}
