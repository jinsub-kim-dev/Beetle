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
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	implementation("org.flywaydb:flyway-core")
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

tasks.withType<Test> {
	useJUnitPlatform()
	finalizedBy(tasks.jacocoTestReport)
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
