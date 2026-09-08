package com.example.beetle.domain.model

import com.example.beetle.domain.exception.checkInvariant

/** 결제 수단 식별자. */
@JvmInline
value class PaymentMethodId(val value: Long) {
    init {
        checkInvariant(value > 0) { "결제 수단 ID 는 양수여야 합니다. 입력값: $value" }
    }

    override fun toString(): String = value.toString()
}

/**
 * 월 중 특정 일자를 표현하는 값 객체.
 *
 * 1~31 을 허용한다. 해당 월에 존재하지 않는 일자(예: 2월 31일)는
 * 청구일 계산 시점에 그 달의 말일로 보정한다.
 */
@JvmInline
value class DayOfMonthValue(val value: Int) {
    init {
        checkInvariant(value in MIN..MAX) { "일자는 $MIN~$MAX 범위여야 합니다. 입력값: $value" }
    }

    override fun toString(): String = "${value}일"

    companion object {
        const val MIN: Int = 1
        const val MAX: Int = 31
    }
}

/** 결제 수단의 종류. PRD 2-③ 의 타입 구분. */
enum class PaymentMethodType {
    /** 신용카드: 소비일과 청구일이 분리된다. */
    CREDIT_CARD,

    /** 체크카드: 즉시 출금된다. */
    CHECK_CARD,

    /** 계좌 이체/자동이체: 즉시 출금된다. */
    BANK_ACCOUNT,

    /** 현금: 즉시 지출된다. */
    CASH,
    ;

    /** 신용카드만 청구일이 소비일보다 뒤로 밀린다. */
    val hasDeferredBilling: Boolean get() = this == CREDIT_CARD
}

/**
 * 결제 수단 애그리거트 루트.
 *
 * CLAUDE.md 4.2절에 따라 `data class` 를 사용하지 않는다.
 *
 * 불변식:
 * - 이름은 공백이 아니며 [NAME_MAX_LENGTH] 자 이하이다.
 * - [paymentDay] 와 [closingDay] 는 [PaymentMethodType.CREDIT_CARD] 에서만 사용한다.
 * - 신용카드는 [paymentDay] 가 필수다. 청구일을 산출할 수 없으면 현금 흐름을 통제할 수 없다.
 */
class PaymentMethod private constructor(
    override val id: PaymentMethodId?,
    val name: String,
    val type: PaymentMethodType,
    val paymentDay: DayOfMonthValue?,
    val closingDay: DayOfMonthValue?,
) : AggregateRoot<PaymentMethodId> {

    init {
        checkInvariant(name.isNotBlank()) { "결제 수단 이름은 비어 있을 수 없습니다." }
        checkInvariant(name.length <= NAME_MAX_LENGTH) {
            "결제 수단 이름은 ${NAME_MAX_LENGTH}자 이하여야 합니다. 입력값 길이: ${name.length}"
        }
        if (type.hasDeferredBilling) {
            checkInvariant(paymentDay != null) {
                "신용카드는 결제일(paymentDay)이 필요합니다. 결제 수단: $name"
            }
        } else {
            checkInvariant(paymentDay == null) {
                "${type.name} 에는 결제일을 지정할 수 없습니다. 즉시 결제되는 수단입니다. 결제 수단: $name"
            }
            checkInvariant(closingDay == null) {
                "${type.name} 에는 마감일을 지정할 수 없습니다. 결제 수단: $name"
            }
        }
    }

    /** 소비일과 청구일이 같은 즉시 결제 수단인지 여부. */
    val isImmediateSettlement: Boolean get() = !type.hasDeferredBilling

    /** 결제일을 변경한다. 신용카드에서만 허용된다. */
    fun changePaymentDay(newPaymentDay: DayOfMonthValue): PaymentMethod {
        checkInvariant(type.hasDeferredBilling) {
            "${type.name} 에는 결제일을 지정할 수 없습니다. 즉시 결제되는 수단입니다. 결제 수단: $name"
        }
        return PaymentMethod(id, name, type, newPaymentDay, closingDay)
    }

    /**
     * 마감일을 변경한다. 신용카드에서만 허용된다.
     * `null` 로 지정하면 마감일 미설정 상태(익월 결제 간주)로 되돌린다.
     */
    fun changeClosingDay(newClosingDay: DayOfMonthValue?): PaymentMethod {
        checkInvariant(type.hasDeferredBilling) {
            "${type.name} 에는 마감일을 지정할 수 없습니다. 결제 수단: $name"
        }
        return PaymentMethod(id, name, type, paymentDay, newClosingDay)
    }

    fun rename(newName: String): PaymentMethod =
        PaymentMethod(id, newName.trim(), type, paymentDay, closingDay)

    /** 영속화 후 부여된 식별자를 반영한 새 인스턴스를 반환한다. */
    fun assignId(assignedId: PaymentMethodId): PaymentMethod =
        PaymentMethod(assignedId, name, type, paymentDay, closingDay)

    /** 식별자 기반 동일성. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PaymentMethod) return false
        val thisId = id ?: return false
        return thisId == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String =
        "PaymentMethod(id=$id, name='$name', type=$type, paymentDay=$paymentDay, closingDay=$closingDay)"

    companion object {
        const val NAME_MAX_LENGTH: Int = 30

        /**
         * 신규 결제 수단을 생성한다.
         *
         * @param closingDay 카드 마감일. 미설정(`null`) 시 청구일 계산에서 익월 결제로 간주한다.
         */
        fun create(
            name: String,
            type: PaymentMethodType,
            paymentDay: DayOfMonthValue? = null,
            closingDay: DayOfMonthValue? = null,
        ): PaymentMethod = PaymentMethod(
            id = null,
            name = name.trim(),
            type = type,
            paymentDay = paymentDay,
            closingDay = closingDay,
        )

        /** 저장된 데이터로부터 애그리거트를 복원한다. 매퍼에서만 사용한다. */
        fun reconstitute(
            id: PaymentMethodId,
            name: String,
            type: PaymentMethodType,
            paymentDay: DayOfMonthValue?,
            closingDay: DayOfMonthValue?,
        ): PaymentMethod = PaymentMethod(
            id = id,
            name = name,
            type = type,
            paymentDay = paymentDay,
            closingDay = closingDay,
        )
    }
}
