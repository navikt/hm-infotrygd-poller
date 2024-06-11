plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.spotless)
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.logging)
    implementation(libs.rapidsAndRivers)
    implementation(libs.konfig.deprecated)

    // Database
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)
    implementation(libs.kotliquery)
    implementation(libs.hikaricp)
    implementation(libs.postgresql)

    // Metrics
    implementation(libs.influxdb.client.kotlin)

    testImplementation(libs.kotlin.test.junit5)
}

spotless {
    kotlin {
        ktlint().editorConfigOverride(
            mapOf(
                "ktlint_standard_comment-wrapping" to "disabled",
                "ktlint_standard_max-line-length" to "disabled",
                "ktlint_standard_value-argument-comment" to "disabled",
                "ktlint_standard_value-parameter-comment" to "disabled",
            ),
        )
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

kotlin { jvmToolchain(21) }
tasks.test { useJUnitPlatform() }
tasks.shadowJar { mergeServiceFiles() }

application { mainClass.set("no.nav.hjelpemidler.ApplicationKt") }
