import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.10"
}
val rapid_version: String by project
val kotlin_logging_version: String by project
val konfig_version: String by project
val kafka_version: String by project
val influxdb_version: String by project
val klaxon_version: String by project
val ojdbc_version: String by project
val postgres_version: String by project
val hikari_version: String by project
val flyway_version: String by project
val kotliquery_version: String by project

group = "no.nav.hjelpemidler"
version = "1.0-SNAPSHOT"

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
    implementation("org.apache.kafka:kafka-clients:$kafka_version")
    implementation("org.influxdb:influxdb-java:$influxdb_version")
    implementation("com.beust:klaxon:$klaxon_version")
    implementation("com.oracle.database.jdbc:ojdbc8:$ojdbc_version")
    implementation("org.postgresql:postgresql:$postgres_version")
    implementation("com.zaxxer:HikariCP:$hikari_version")
    implementation("org.flywaydb:flyway-core:$flyway_version")
    implementation("com.github.seratch:kotliquery:$kotliquery_version")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
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
