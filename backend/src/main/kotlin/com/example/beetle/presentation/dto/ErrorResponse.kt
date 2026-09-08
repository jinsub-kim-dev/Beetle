package com.example.beetle.presentation.dto

/**
 * 통일된 오류 응답 포맷.
 *
 * @param code 클라이언트가 분기 처리할 수 있는 오류 코드
 * @param message 사람이 읽을 수 있는 설명
 * @param fieldErrors 요청 필드 검증 실패 상세 (검증 오류에만 존재)
 */
data class ErrorResponse(
    val code: String,
    val message: String,
    val fieldErrors: List<FieldErrorDetail>? = null,
) {
    data class FieldErrorDetail(
        val field: String,
        val message: String,
    )
}
