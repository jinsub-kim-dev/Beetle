package com.example.beetle.config

import com.example.beetle.domain.service.BillDateCalculator
import com.example.beetle.domain.service.InstallmentScheduler
import com.example.beetle.domain.service.SpendingAnomalyDetector
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 도메인 서비스를 스프링 컨텍스트에 등록하는 구성(composition root).
 *
 * 도메인 서비스는 프레임워크에 독립적인 순수 클래스여야 하므로(CLAUDE.md 3.1)
 * `@Component` 를 붙이지 않는다. 대신 4개 레이어 밖의 이 구성 클래스에서 빈으로 등록한다.
 */
@Configuration(proxyBeanMethods = false)
class DomainServiceConfiguration {

    @Bean
    fun billDateCalculator(): BillDateCalculator = BillDateCalculator()

    @Bean
    fun installmentScheduler(billDateCalculator: BillDateCalculator): InstallmentScheduler =
        InstallmentScheduler(billDateCalculator)

    @Bean
    fun spendingAnomalyDetector(): SpendingAnomalyDetector = SpendingAnomalyDetector()
}
