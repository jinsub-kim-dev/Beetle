package com.example.beetle.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * 환경별 설정 파일의 정책을 고정하는 테스트.
 *
 * 문서에 적어 두는 것만으로는 지켜지지 않는다. "배포 환경에 시드 데이터를 넣지 않는다",
 * "접속 정보에 기본값을 두지 않는다" 같은 규칙은 설정 파일을 고칠 때 조용히 깨지므로
 * 테스트로 강제한다 (CLAUDE.md 3.2).
 *
 * 스프링 컨텍스트를 띄우지 않고 YAML 자체를 읽는다. 배포 프로필은 환경 변수 없이
 * 기동될 수 없으므로(그것이 이 프로필의 요구사항이다) 컨텍스트로는 검증할 수 없다.
 */
@DisplayName("환경별 설정 파일")
class ProfileConfigurationTest {

    private val common = propertiesOf("application.yml")
    private val dev = propertiesOf("application-dev.yml")
    private val prod = propertiesOf("application-prod.yml")

    @Nested
    @DisplayName("공통 설정")
    inner class Common {

        @Test
        fun `프로필을 지정하지 않으면 로컬 개발로 동작한다`() {
            // 실수로 프로필을 빼먹었을 때 운영 설정이 아니라 로컬 설정으로 떠야 한다
            assertThat(common["spring.profiles.default"]).isEqualTo("dev")
        }

        @Test
        fun `스키마는 Flyway 로만 관리한다`() {
            assertThat(common["spring.jpa.hibernate.ddl-auto"]).isEqualTo("validate")
        }

        @Test
        fun `접속 URL 은 한 곳에만 둔다`() {
            // 프로필마다 URL 을 따로 쓰면 타임존·인코딩 옵션이 환경별로 어긋난다
            val url = common.getValue("spring.datasource.url")
            assertThat(url).contains("\${beetle.db.host}", "\${beetle.db.port}", "\${beetle.db.name}")
            assertThat(url).contains("serverTimezone=Asia/Seoul", "characterEncoding=UTF-8")
            assertThat(dev).doesNotContainKey("spring.datasource.url")
            assertThat(prod).doesNotContainKey("spring.datasource.url")
        }

        @Test
        fun `공통 설정에는 시드 데이터를 넣지 않는다`() {
            // 시드 적용 여부는 프로필이 정한다
            assertThat(common.getValue("spring.flyway.locations")).doesNotContain("db/seed")
        }
    }

    @Nested
    @DisplayName("dev 프로필 - 로컬")
    inner class Dev {

        @Test
        fun `환경 변수 없이도 기동할 수 있게 기본값을 둔다`() {
            // 받아서 바로 뜨는 것이 로컬 환경의 요구사항이다
            assertThat(dev.getValue("beetle.db.host")).isEqualTo("\${DB_HOST:localhost}")
            assertThat(dev.getValue("beetle.db.name")).contains(":beetle")
            assertThat(dev.getValue("beetle.db.user")).contains(":beetle")
            assertThat(dev.getValue("beetle.db.password")).contains(":")
        }

        @Test
        fun `DB 포트 기본값이 compose 가 노출하는 포트와 같다`() {
            // 두 파일이 어긋나면 컨테이너 없이 백엔드만 띄웠을 때 조용히 접속에 실패한다.
            // 로컬은 표준 포트(3306)를 피한다. 호스트에 이미 MySQL 이 있으면 충돌한다.
            val composeFile = File("../docker-compose.override.yml")
            assumeTrue(composeFile.exists(), "모노레포 루트 기준으로 실행할 때만 검증할 수 있다")

            // `${DB_PORT:-13306}:3306` 에서 호스트 쪽 기본 포트를 뽑는다.
            val published = publishedMysqlPortOf(composeFile)

            assertThat(dev.getValue("beetle.db.port"))
                .describedAs("dev 프로필의 DB 포트 기본값은 compose 노출 포트와 같아야 한다")
                .isEqualTo("\${DB_PORT:$published}")
        }

        @Test
        fun `시드 데이터를 함께 적용한다`() {
            // 손으로 데이터를 만들지 않아도 화면이 채워져 있어야 확인이 빠르다
            assertThat(dev.getValue("spring.flyway.locations"))
                .contains("classpath:db/migration")
                .contains("classpath:db/seed")
        }

        @Test
        fun `API 문서를 노출한다`() {
            assertThat(dev["springdoc.api-docs.enabled"]).isEqualTo("true")
            assertThat(dev["springdoc.swagger-ui.enabled"]).isEqualTo("true")
        }

        @Test
        fun `쿼리와 도메인 로그를 볼 수 있다`() {
            assertThat(dev["spring.jpa.show-sql"]).isEqualTo("true")
            assertThat(dev["logging.level.com.example.beetle"]).isEqualTo("DEBUG")
        }
    }

    @Nested
    @DisplayName("prod 프로필 - 배포")
    inner class Prod {

        @Test
        fun `접속 정보에 기본값을 두지 않는다`() {
            // 기본값이 있으면 설정을 빠뜨린 채로 떠서 엉뚱한 DB 에 붙을 수 있다.
            // 기동 시점에 실패해야 한다
            listOf("beetle.db.host", "beetle.db.name", "beetle.db.user", "beetle.db.password")
                .forEach { key ->
                    assertThat(prod.getValue(key))
                        .describedAs("$key 에 기본값이 있으면 설정 누락이 드러나지 않는다")
                        .doesNotContain(":")
                }
        }

        @Test
        fun `시드 데이터를 적용하지 않는다`() {
            // db/seed 는 데모 데이터다. 배포 환경에 들어가면 실제 데이터와 섞인다
            assertThat(prod.getValue("spring.flyway.locations"))
                .isEqualTo("classpath:db/migration")
        }

        @Test
        fun `기존 스키마를 자동으로 기준선 처리하지 않는다`() {
            // 비어 있지 않은 DB 에 처음 배포하면 실패해야 하며, 그때 사람이 판단해야 한다
            assertThat(prod["spring.flyway.baseline-on-migrate"]).isEqualTo("false")
            assertThat(prod["spring.flyway.clean-disabled"]).isEqualTo("true")
        }

        @Test
        fun `API 문서를 노출하지 않는다`() {
            assertThat(prod["springdoc.api-docs.enabled"]).isEqualTo("false")
            assertThat(prod["springdoc.swagger-ui.enabled"]).isEqualTo("false")
        }

        @Test
        fun `예외 메시지와 스택을 응답에 담지 않는다`() {
            // 오류 응답은 code/message 규약(PRD 7.1)만 노출한다
            assertThat(prod["server.error.include-message"]).isEqualTo("never")
            assertThat(prod["server.error.include-stacktrace"]).isEqualTo("never")
        }

        @Test
        fun `헬스체크와 지표 외의 관리 엔드포인트를 노출하지 않는다`() {
            // env/beans 는 설정과 내부 구조를 드러낸다.
            // prometheus 는 모니터링에 필요하지만 호스트에 공개되지 않는다. 배포 구성은
            // 백엔드 포트를 발행하지 않고 nginx 도 /actuator/health 만 프록시한다.
            assertThat(prod.getValue("management.endpoints.web.exposure.include"))
                .isEqualTo("health,prometheus")
            assertThat(prod["management.endpoint.health.show-details"]).isEqualTo("never")
        }

        @Test
        fun `쿼리 로그를 남기지 않는다`() {
            assertThat(prod["spring.jpa.show-sql"]).isEqualTo("false")
            assertThat(prod["logging.level.com.example.beetle"]).isEqualTo("INFO")
        }
    }

    /** compose 파일에서 mysql 이 호스트에 노출하는 포트의 기본값을 읽는다. */
    private fun publishedMysqlPortOf(composeFile: File): String {
        val compose = composeFile.inputStream().use { Yaml().load<Map<String, Any?>>(it) }
        val services = compose["services"] as Map<*, *>
        val mysql = services["mysql"] as Map<*, *>
        val mapping = (mysql["ports"] as List<*>).first().toString()

        return mapping
            .substringBefore(":3306")
            .substringAfter(":-")
            .removeSuffix("}")
    }

    /** YAML 을 `a.b.c` 형태의 평면 맵으로 읽는다. */
    private fun propertiesOf(resource: String): Map<String, String> {
        val loaded = ClassPathResource(resource).inputStream.use { input ->
            Yaml().load<Map<String, Any?>>(input)
        }
        return flatten(prefix = "", node = loaded)
    }

    private fun flatten(prefix: String, node: Map<*, *>): Map<String, String> = node.entries
        .flatMap { (key, value) ->
            val path = if (prefix.isEmpty()) key.toString() else "$prefix.$key"
            when (value) {
                is Map<*, *> -> flatten(path, value).toList()
                else -> listOf(path to value.toString())
            }
        }
        .toMap()
}
