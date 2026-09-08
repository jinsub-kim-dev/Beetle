package com.example.beetle.domain.exception

/**
 * 도메인 규칙 위반을 표현하는 예외 계층.
 *
 * CLAUDE.md 4.3절: 검증 실패는 `IllegalArgumentException` 이 아니라 도메인 예외로 던진다.
 * 프레젠테이션 레이어는 이 계층을 HTTP 상태로 변환한다.
 */
sealed class DomainException(message: String) : RuntimeException(message)

/**
 * 불변식(Invariant) 위반. 유효하지 않은 값으로 도메인 객체를 만들려 한 경우.
 * HTTP 400 으로 변환한다.
 */
class InvariantViolationException(message: String) : DomainException(message)

/**
 * 현재 상태에서 허용되지 않는 상태 전이를 시도한 경우.
 * HTTP 409 로 변환한다.
 */
class DomainStateException(message: String) : DomainException(message)

/**
 * 참조한 애그리거트를 찾을 수 없는 경우.
 * HTTP 404 로 변환한다.
 */
class ResourceNotFoundException(message: String) : DomainException(message) {
    constructor(resourceName: String, id: Any) : this("$resourceName(id=$id)을(를) 찾을 수 없습니다.")
}

/**
 * 불변식을 검증하고, 위반 시 [InvariantViolationException] 을 던진다.
 *
 * 코틀린 표준 `require` 대신 이 함수를 사용해 도메인 예외 계층을 유지한다.
 */
inline fun checkInvariant(condition: Boolean, message: () -> String) {
    if (!condition) throw InvariantViolationException(message())
}
