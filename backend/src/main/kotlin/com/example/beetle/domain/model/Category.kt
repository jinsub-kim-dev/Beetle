package com.example.beetle.domain.model

import com.example.beetle.domain.exception.checkInvariant

/** 카테고리 식별자. */
@JvmInline
value class CategoryId(val value: Long) {
    init {
        checkInvariant(value > 0) { "카테고리 ID 는 양수여야 합니다. 입력값: $value" }
    }

    override fun toString(): String = value.toString()
}

/** 거래의 성질. PRD 2-② 의 타입 구분. */
enum class CategoryType {
    /** 수입 */
    INCOME,

    /** 지출 */
    EXPENSE,

    /** 이체 (계좌 간 이동으로 순자산 변동이 없음) */
    TRANSFER,
    ;

    val isExpense: Boolean get() = this == EXPENSE
}

/** 지출의 성격. 고정비/변동비 분리는 예산 통제의 기준이 된다. */
enum class ExpenseNature {
    /** 고정비: 월세, 통신비 등 매달 고정적으로 발생 */
    FIXED,

    /** 변동비: 식비, 쇼핑 등 유동적으로 발생 */
    VARIABLE,
}

/**
 * 카테고리 애그리거트 루트.
 *
 * CLAUDE.md 4.2절에 따라 `data class` 를 사용하지 않는다.
 * 동일성은 식별자로 판단하고, 상태 변경은 의도를 드러내는 메서드로만 수행한다.
 *
 * 불변식:
 * - 이름은 공백이 아니며 [NAME_MAX_LENGTH] 자 이하이다.
 * - [nature] 는 [CategoryType.EXPENSE] 일 때만 존재한다. (지출에는 반드시 존재)
 */
class Category private constructor(
    override val id: CategoryId?,
    val name: String,
    val type: CategoryType,
    val nature: ExpenseNature?,
) : AggregateRoot<CategoryId> {

    init {
        checkInvariant(name.isNotBlank()) { "카테고리 이름은 비어 있을 수 없습니다." }
        checkInvariant(name.length <= NAME_MAX_LENGTH) {
            "카테고리 이름은 ${NAME_MAX_LENGTH}자 이하여야 합니다. 입력값 길이: ${name.length}"
        }
        if (type.isExpense) {
            checkInvariant(nature != null) {
                "지출 카테고리는 고정비/변동비 성격을 지정해야 합니다. 카테고리: $name"
            }
        } else {
            checkInvariant(nature == null) {
                "${type.name} 카테고리에는 지출 성격을 지정할 수 없습니다. 카테고리: $name"
            }
        }
    }

    /** 고정비 지출 카테고리인지 여부. 예산 통제 통계의 기준이다. */
    val isFixedExpense: Boolean get() = nature == ExpenseNature.FIXED

    fun rename(newName: String): Category = Category(id, newName.trim(), type, nature)

    /**
     * 지출 성격을 변경한다. 지출 카테고리에서만 허용된다.
     * 타입 자체는 변경할 수 없다. 이미 기록된 거래의 의미가 뒤바뀌기 때문이다.
     */
    fun changeNature(newNature: ExpenseNature): Category {
        checkInvariant(type.isExpense) {
            "${type.name} 카테고리에는 지출 성격을 지정할 수 없습니다. 카테고리: $name"
        }
        return Category(id, name, type, newNature)
    }

    /** 영속화 후 부여된 식별자를 반영한 새 인스턴스를 반환한다. */
    fun assignId(assignedId: CategoryId): Category = Category(assignedId, name, type, nature)

    /** 식별자 기반 동일성. 값이 달라도 같은 ID 면 같은 애그리거트다. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Category) return false
        val thisId = id ?: return false
        return thisId == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String = "Category(id=$id, name='$name', type=$type, nature=$nature)"

    companion object {
        const val NAME_MAX_LENGTH: Int = 30

        /** 신규 카테고리를 생성한다. 식별자는 영속화 시점에 부여된다. */
        fun create(name: String, type: CategoryType, nature: ExpenseNature? = null): Category =
            Category(id = null, name = name.trim(), type = type, nature = nature)

        /** 저장된 데이터로부터 애그리거트를 복원한다. 매퍼에서만 사용한다. */
        fun reconstitute(
            id: CategoryId,
            name: String,
            type: CategoryType,
            nature: ExpenseNature?,
        ): Category = Category(id = id, name = name, type = type, nature = nature)
    }
}
