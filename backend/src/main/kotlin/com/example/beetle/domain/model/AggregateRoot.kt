package com.example.beetle.domain.model

/**
 * 애그리거트 루트를 표시하는 마커 인터페이스.
 *
 * CLAUDE.md 4.1절: 애그리거트 루트는 일관성 경계의 유일한 진입점이며,
 * 다른 애그리거트는 이 루트의 식별자로만 참조한다.
 *
 * 이 인터페이스를 구현하면 다음 규약이 테스트로 강제된다.
 * - `data class` 로 선언할 수 없다. (동일성은 식별자로 판단해야 한다)
 * - 동일성은 [id] 기준으로 구현해야 한다.
 *
 * @param ID 애그리거트 식별자 타입
 */
interface AggregateRoot<ID : Any> {
    /** 영속화 이전에는 `null` 이다. */
    val id: ID?
}
