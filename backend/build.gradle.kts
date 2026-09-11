plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.3.21"
	jacoco
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	// API 문서화. springdoc 3.x 가 Spring Boot 4 대응 버전이다.
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	// Boot 4 는 자동 구성이 모듈별로 분리되어 있다. flyway-core 만으로는
	// FlywayAutoConfiguration 이 없어 마이그레이션이 실행되지 않는다.
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	runtimeOnly("org.flywaydb:flyway-mysql")
	runtimeOnly("com.mysql:mysql-connector-j")

	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.assertj:assertj-core")
	// 애플리케이션 서비스 단위 테스트용 목(mock)
	testImplementation("io.mockk:mockk:1.13.13")
	// 아키텍처 규칙(CLAUDE.md 3절) 강제
	testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
	// 영속성 통합 테스트는 H2 가 아닌 실제 MySQL 로 수행한다.
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-mysql")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

/**
 * Testcontainers 는 기본적으로 /var/run/docker.sock 을 탐색한다.
 * Rancher Desktop, Colima 등 대체 런타임을 쓰는 개발 환경에서도 별도 설정 없이
 * 통합 테스트가 동작하도록, 실제 소켓 위치를 자동으로 탐지해 주입한다.
 *
 * TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE 는 Ryuk 컨테이너가 VM 내부에서 마운트할
 * 경로이므로 호스트 경로와 달리 항상 /var/run/docker.sock 이다.
 */
fun Test.configureDockerSocket() {
	if (System.getenv("DOCKER_HOST") != null) return

	val home = System.getProperty("user.home")
	val defaultSocket = "/var/run/docker.sock"
	val candidates = listOf(
		defaultSocket,
		"$home/.rd/docker.sock",
		"$home/.colima/default/docker.sock",
		"$home/.docker/run/docker.sock",
	)

	val socket = candidates.firstOrNull { File(it).exists() } ?: return
	if (socket == defaultSocket) return

	environment("DOCKER_HOST", "unix://$socket")
	environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", defaultSocket)
}

tasks.withType<Test> {
	useJUnitPlatform()
	configureDockerSocket()
	finalizedBy(tasks.jacocoTestReport)
}

// bootJar 만 배포에 쓴다. plain jar 는 쓰이지 않는데 두 개가 남으면 배포용
// Dockerfile 에서 어느 것을 담을지 와일드카드로 특정할 수 없다.
tasks.jar {
	enabled = false
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		html.required = true
		xml.required = true
	}
}

/**
 * CLAUDE.md 5.2절 정책: 도메인 레이어는 분기 커버리지 90% 이상을 유지한다.
 * 도메인 규칙은 순수 단위 테스트로 전수 검증 가능하므로 예외를 두지 않는다.
 */
tasks.jacocoTestCoverageVerification {
	dependsOn(tasks.test)
	violationRules {
		rule {
			element = "PACKAGE"
			includes = listOf("com.example.beetle.domain.*")
			limit {
				counter = "BRANCH"
				value = "COVEREDRATIO"
				minimum = "0.90".toBigDecimal()
			}
		}
	}
}

tasks.check {
	dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.register("printTestCp") {
	doLast { println(sourceSets["test"].runtimeClasspath.asPath) }
}
