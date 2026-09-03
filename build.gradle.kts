plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    // Mutation testing. Line coverage proves a line ran; mutation score proves a
    // test would have noticed if that line were wrong.
    id("info.solidsoft.pitest") version "1.15.0"
}

group = "com.omarfraser"
version = "0.1.0-SNAPSHOT"
description = "BugTrail — defect tracking with a computed triage engine"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // --- Web and persistence -------------------------------------------------
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // --- Schema migrations ---------------------------------------------------
    // flyway-database-postgresql is a separate artifact as of Flyway 10 and the
    // build fails without it. This trips up a lot of people.
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    runtimeOnly("org.postgresql:postgresql")

    // --- Test layer 1: unit --------------------------------------------------
    // spring-boot-starter-test brings JUnit 5, AssertJ, Mockito and Hamcrest.
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter-params")

    // --- Test layer 2: integration against real PostgreSQL -------------------
    // Uncomment in phase 2, when you write the first repository test.
    // Spring Boot 4's BOM does NOT manage Testcontainers versions, so the
    // version has to be pinned here -- that is what the testcontainers-bom is for.
    // Not H2. H2 accepts SQL that Postgres rejects, so an H2-backed suite passes
    // on queries that fail in production.
    //
    // testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))
    // testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // testImplementation("org.testcontainers:junit-jupiter")
    // testImplementation("org.testcontainers:postgresql")

    // --- Test layer 3: API contract ------------------------------------------
    // Uncomment in phase 3, with the first controller test.
    // testImplementation("io.rest-assured:rest-assured:5.5.0")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

// Coverage gate. Starts deliberately low so it passes on day one, and gets raised
// as the suite grows — a gate you have to disable to commit is a gate you delete.
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

pitest {
    junit5PluginVersion = "1.2.1"
    // Only the packages with real logic. Running mutation tests over entities and
    // DTOs is slow and tells you nothing.
    targetClasses = listOf(
        "com.omarfraser.bugtrail.triage.*",
        "com.omarfraser.bugtrail.workflow.*",
        "com.omarfraser.bugtrail.domain.*"
    )
    targetTests = listOf("com.omarfraser.bugtrail.*")
    mutationThreshold = 75
    outputFormats = listOf("HTML", "XML")
    timestampedReports = false
}
