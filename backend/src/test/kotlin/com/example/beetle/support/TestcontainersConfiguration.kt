package com.example.beetle.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.mysql.MySQLContainer

/**
 * 영속성 통합 테스트용 MySQL 컨테이너 설정.
 *
 * CLAUDE.md 5.2절 정책에 따라 H2 가 아닌 실제 MySQL 을 사용한다.
 * H2 는 방언(dialect) 차이로 인해 거짓 통과/거짓 실패를 만들 수 있다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun mysqlContainer(): MySQLContainer =
        MySQLContainer(MYSQL_IMAGE)
            .withDatabaseName("beetle")
            .withUsername("beetle")
            .withPassword("beetlepassword")
            .withReuse(true)

    companion object {
        /** docker-compose.yml 의 MySQL 버전과 일치시킨다. */
        const val MYSQL_IMAGE: String = "mysql:8.4"
    }
}
