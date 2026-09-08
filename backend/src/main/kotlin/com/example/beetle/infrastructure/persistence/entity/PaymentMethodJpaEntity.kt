package com.example.beetle.infrastructure.persistence.entity

import com.example.beetle.domain.model.PaymentMethodType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 결제 수단 영속화 엔티티.
 */
@Entity
@Table(name = "payment_method")
class PaymentMethodJpaEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "name", nullable = false, length = 30)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    var type: PaymentMethodType,

    @Column(name = "payment_day")
    var paymentDay: Int? = null,

    @Column(name = "closing_day")
    var closingDay: Int? = null,

) : BaseTimeEntity()
