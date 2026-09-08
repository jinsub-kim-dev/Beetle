package com.example.beetle.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * API 문서 설정.
 *
 * 프론트엔드 확장(모노레포의 `frontend/`)을 대비해 스펙을 공개한다.
 * - 문서 UI: `/swagger-ui.html`
 * - 스펙(JSON): `/v3/api-docs`
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    @Bean
    fun beetleOpenApi(): OpenAPI = OpenAPI().info(
        Info()
            .title("Beetle 가계부 API")
            .version("v0.0.1")
            .description(
                """
                개인 맞춤형 가계부 백엔드 API.

                핵심 개념
                - 소비일(spentDate)과 청구일(billDate)을 분리해 관리한다.
                  조회/통계의 `basis` 파라미터로 두 축을 전환한다.
                  - `SPENT`: 소비 패턴 분석 ("이번 달에 얼마를 썼나")
                  - `BILL`: 현금 흐름 통제 ("이번 달에 통장에서 얼마가 나가나")
                - 지출 카테고리는 고정비(FIXED)/변동비(VARIABLE) 성격을 갖는다.
                - 할부는 계획 등록 시 회차별 거래가 자동 생성된다.
                - 회사 전액 지원 통신비처럼 실지출이 없는 항목은 거래 단위
                  `excludedFromStats` 플래그로 통계에서 제외한다.
                """.trimIndent(),
            ),
    )
}
