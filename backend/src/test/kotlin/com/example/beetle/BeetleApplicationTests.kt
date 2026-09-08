package com.example.beetle

import com.example.beetle.support.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

/**
 * 전체 애플리케이션 컨텍스트가 정상적으로 구성되는지 확인하는 유일한 스모크 테스트.
 *
 * CLAUDE.md 5.2절: `@SpringBootTest` 는 최소 개수만 유지한다.
 * 도메인 규칙 검증은 Spring 없는 순수 단위 테스트로 수행한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class BeetleApplicationTests {

    @Test
    fun contextLoads() {
    }
}
