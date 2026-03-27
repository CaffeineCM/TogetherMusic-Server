plugins {
    java
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.togethermusic"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

extra["saTokenVersion"] = "1.39.0"
extra["hutoolVersion"] = "5.8.38"
extra["fastjson2Version"] = "2.0.57"
extra["unirestVersion"] = "3.14.5"
extra["jqwikVersion"] = "1.9.3"

dependencies {
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // PostgreSQL
    runtimeOnly("org.postgresql:postgresql")

    // Flyway
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Sa-Token
    implementation("cn.dev33:sa-token-spring-boot3-starter:${property("saTokenVersion")}")
    implementation("cn.dev33:sa-token-redis-jackson:${property("saTokenVersion")}")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Fastjson2
    implementation("com.alibaba.fastjson2:fastjson2:${property("fastjson2Version")}")

    // Hutool
    implementation("cn.hutool:hutool-all:${property("hutoolVersion")}")

    // Unirest (HTTP client for music API calls)
    implementation("com.konghq:unirest-java:${property("unirestVersion")}")

    // JAudioTagger (audio metadata parsing)
    implementation("net.jthink:jaudiotagger:3.0.1")

    // Apache Commons Lang3
    implementation("org.apache.commons:commons-lang3")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("net.jqwik:jqwik:${property("jqwikVersion")}")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
    useJUnitPlatform {
        includeEngines("junit-jupiter", "jqwik")
    }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName = "together-music-backend.jar"
}
