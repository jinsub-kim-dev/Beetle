package com.example.beetle.presentation.advice

import com.example.beetle.domain.exception.DomainStateException
import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.domain.exception.ResourceNotFoundException
import com.example.beetle.presentation.dto.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * 도메인 예외를 HTTP 응답으로 변환하는 전역 핸들러.
 *
 * 도메인 예외 계층과 HTTP 상태의 대응:
 * - [InvariantViolationException] -> 400 (유효하지 않은 값)
 * - [ResourceNotFoundException] -> 404 (참조 대상 없음)
 * - [DomainStateException] -> 409 (현재 상태에서 허용되지 않는 요청)
 */
@RestControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(InvariantViolationException::class)
    fun handleInvariantViolation(e: InvariantViolationException): ResponseEntity<ErrorResponse> {
        log.debug("불변식 위반: {}", e.message)
        return ResponseEntity.badRequest()
            .body(ErrorResponse(code = "INVARIANT_VIOLATION", message = e.message ?: "잘못된 요청입니다."))
    }

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFound(e: ResourceNotFoundException): ResponseEntity<ErrorResponse> {
        log.debug("리소스 없음: {}", e.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(code = "RESOURCE_NOT_FOUND", message = e.message ?: "대상을 찾을 수 없습니다."))
    }

    @ExceptionHandler(DomainStateException::class)
    fun handleDomainState(e: DomainStateException): ResponseEntity<ErrorResponse> {
        log.debug("상태 충돌: {}", e.message)
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(code = "DOMAIN_STATE_CONFLICT", message = e.message ?: "처리할 수 없는 상태입니다."))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationFailure(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val fieldErrors = e.bindingResult.fieldErrors.map {
            ErrorResponse.FieldErrorDetail(
                field = it.field,
                message = it.defaultMessage ?: "유효하지 않은 값입니다.",
            )
        }
        return ResponseEntity.badRequest().body(
            ErrorResponse(
                code = "VALIDATION_FAILED",
                message = "요청 값이 유효하지 않습니다.",
                fieldErrors = fieldErrors,
            ),
        )
    }

    /** 열거형(enum) 등 경로/쿼리 파라미터 타입 불일치. */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(
            ErrorResponse(
                code = "INVALID_PARAMETER",
                message = "파라미터 '${e.name}' 의 값이 유효하지 않습니다: ${e.value}",
            ),
        )

    /** 잘못된 JSON 본문 또는 역직렬화 불가능한 열거형 값. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        log.debug("요청 본문 해석 실패: {}", e.message)
        return ResponseEntity.badRequest().body(
            ErrorResponse(code = "MALFORMED_REQUEST", message = "요청 본문을 해석할 수 없습니다."),
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("처리되지 않은 예외", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(code = "INTERNAL_ERROR", message = "서버 내부 오류가 발생했습니다."),
        )
    }
}
