package com.example.beetle.fixture

import com.example.beetle.domain.model.Category
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.DayOfMonthValue
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.PaymentMethod
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.PaymentMethodType

/**
 * 도메인 테스트 픽스처.
 *
 * CLAUDE.md 5.4절: 픽스처는 팩토리 함수로 모아 재사용한다.
 * 각 테스트는 검증에 필요한 값만 명시하고 나머지는 기본값에 맡긴다.
 */

// --- Category ---

fun expenseCategory(
    name: String = "식비",
    nature: ExpenseNature = ExpenseNature.VARIABLE,
    id: Long? = null,
): Category = if (id == null) {
    Category.create(name = name, type = CategoryType.EXPENSE, nature = nature)
} else {
    Category.reconstitute(CategoryId(id), name, CategoryType.EXPENSE, nature)
}

fun incomeCategory(name: String = "급여", id: Long? = null): Category = if (id == null) {
    Category.create(name = name, type = CategoryType.INCOME)
} else {
    Category.reconstitute(CategoryId(id), name, CategoryType.INCOME, null)
}

fun transferCategory(name: String = "계좌이체", id: Long? = null): Category = if (id == null) {
    Category.create(name = name, type = CategoryType.TRANSFER)
} else {
    Category.reconstitute(CategoryId(id), name, CategoryType.TRANSFER, null)
}

// --- PaymentMethod ---

fun creditCard(
    name: String = "삼성카드",
    paymentDay: Int = 14,
    closingDay: Int? = null,
    id: Long? = null,
): PaymentMethod {
    val payment = DayOfMonthValue(paymentDay)
    val closing = closingDay?.let { DayOfMonthValue(it) }
    return if (id == null) {
        PaymentMethod.create(name, PaymentMethodType.CREDIT_CARD, payment, closing)
    } else {
        PaymentMethod.reconstitute(
            PaymentMethodId(id), name, PaymentMethodType.CREDIT_CARD, payment, closing,
        )
    }
}

fun checkCard(name: String = "국민체크", id: Long? = null): PaymentMethod =
    immediatePaymentMethod(name, PaymentMethodType.CHECK_CARD, id)

fun bankAccount(name: String = "신한주계좌", id: Long? = null): PaymentMethod =
    immediatePaymentMethod(name, PaymentMethodType.BANK_ACCOUNT, id)

fun cash(name: String = "현금", id: Long? = null): PaymentMethod =
    immediatePaymentMethod(name, PaymentMethodType.CASH, id)

private fun immediatePaymentMethod(
    name: String,
    type: PaymentMethodType,
    id: Long?,
): PaymentMethod = if (id == null) {
    PaymentMethod.create(name, type)
} else {
    PaymentMethod.reconstitute(PaymentMethodId(id), name, type, null, null)
}
