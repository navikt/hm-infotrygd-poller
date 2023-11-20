val rapid_version = "2023101613431697456627.0cdd93eb696f"
val kotlin_logging_version = "2.1.21"
val konfig_version = "1.6.10.0"
val kafka_version = "2.8.1"
val influxdb_version = "2.23"
val influxdb_kotlin_version = "6.10.0"
val klaxon_version = "5.5"
val ojdbc_version = "21.1.0.0"
val postgres_version = "42.3.8"
val hikari_version = "5.0.1"
val flyway_version = "8.5.2"
val kotliquery_version = "1.6.1"
val ktlint_version = "0.43.2"

group = "no.nav.hjelpemidler"
version = "1.0-SNAPSHOT"

plugins {
    kotlin("jvm") version "1.9.0"
    id("com.diffplug.spotless") version "6.2.1"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io") // Used for Rapids and rivers-dependency
    maven("https://packages.confluent.io/maven/") // Kafka-avro
}

dependencies {
    testImplementation(kotlin("test-junit"))

    implementation("com.github.navikt:rapids-and-rivers:$rapid_version")
    implementation("io.github.microutils:kotlin-logging:$kotlin_logging_version")
    implementation("com.natpryce:konfig:$konfig_version")
    implementation("org.influxdb:influxdb-java:$influxdb_version")
    implementation("com.influxdb:influxdb-client-kotlin:$influxdb_kotlin_version")
    implementation("com.beust:klaxon:$klaxon_version")
    implementation("com.oracle.database.jdbc:ojdbc8:$ojdbc_version")
    implementation("org.postgresql:postgresql:$postgres_version")
    implementation("com.zaxxer:HikariCP:$hikari_version")
    implementation("org.flywaydb:flyway-core:$flyway_version")
    implementation("com.github.seratch:kotliquery:$kotliquery_version")
}

spotless {
    kotlin {
        ktlint(ktlint_version)
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(ktlint_version)
    }
}

val fatJar = task("fatJar", type = org.gradle.jvm.tasks.Jar::class) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("${project.name}-fat")
    manifest {
        attributes["Main-Class"] = "no.nav.hjelpemidler.ApplicationKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get() as CopySpec)
}

tasks {
    "build" {
        dependsOn(fatJar)
    }
}
