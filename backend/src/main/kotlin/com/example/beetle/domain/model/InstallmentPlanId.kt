package com.example.beetle.domain.model

import com.example.beetle.domain.exception.checkInvariant

/**
 * 할부 계획 식별자.
 *
 * [Transaction] 이 할부 회차를 가리키기 위해 참조한다.
 * 애그리거트 간 참조는 식별자로만 한다 (CLAUDE.md 4.1).
 */
@JvmInline
value class InstallmentPlanId(val value: Long) {
    init {
        checkInvariant(value > 0) { "할부 계획 ID 는 양수여야 합니다. 입력값: $value" }
    }

    override fun toString(): String = value.toString()
}
