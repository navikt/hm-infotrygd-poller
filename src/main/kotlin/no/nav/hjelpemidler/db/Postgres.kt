package no.nav.hjelpemidler.soknad.mottak.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.hjelpemidler.configuration.Configuration
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult

internal fun migrate() =
    HikariDataSource(hikariConfig()).use { migrate(it) }

internal fun hikariConfig() =
    HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://${Configuration.db["DB_HOST"]!!}:${Configuration.db["DB_PORT"]!!}/${Configuration.db["DB_DATABASE"]!!}"
        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
        username = Configuration.db["DB_USERNAME"]!!
        password = Configuration.db["DB_PASSWORD"]!!
    }

internal fun dataSource(): HikariDataSource = HikariDataSource(hikariConfig())

internal fun migrate(dataSource: HikariDataSource, initSql: String = ""): MigrateResult? =
    Flyway.configure().dataSource(dataSource).initSql(initSql).load().migrate()

internal fun clean(dataSource: HikariDataSource) = Flyway.configure().dataSource(dataSource).load().clean()
