package no.nav.hjelpemidler

import mu.KotlinLogging
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.helse.rapids_rivers.KafkaConfig
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.rivers.LoggRiver
import oracle.jdbc.OracleConnection
import oracle.jdbc.pool.OracleDataSource
import java.net.InetAddress
import java.util.Properties

private val logg = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

fun main() {
    /*var rapidApp: RapidsConnection? = null
    rapidApp = RapidApplication.Builder(
        RapidApplication.RapidApplicationConfig(
            Configuration.rapidConfig["RAPID_APP_NAME"],
            InetAddress.getLocalHost().hostName,
            Configuration.rapidConfig["KAFKA_RAPID_TOPIC"]!!,
            emptyList(),
            KafkaConfig(
                Configuration.rapidConfig["KAFKA_BOOTSTRAP_SERVERS"]!!,
                Configuration.rapidConfig["KAFKA_CONSUMER_GROUP_ID"]!!,
                Configuration.rapidConfig["KAFKA_CLIENT_ID"]!!,
                null,
                null,
                Configuration.rapidConfig["KAFKA_TRUSTSTORE_PATH"]!!,
                Configuration.rapidConfig["KAFKA_TRUSTSTORE_PASSWORD"]!!,
                "jks",
                "PKCS12",
                Configuration.rapidConfig["KAFKA_KEYSTORE_PATH"]!!,
                Configuration.rapidConfig["KAFKA_KEYSTORE_PASSWORD"]!!,
                Configuration.rapidConfig["KAFKA_RESET_POLICY"]!!,
                false,
                null,
                null,
            ),
            8080,
        )
    ).build().apply {
        LoggRiver(this)
    }
     */

    // Set up database connection
    val info = Properties()
    info[OracleConnection.CONNECTION_PROPERTY_USER_NAME] = Configuration.oracleDatabaseConfig["HM_INFOTRYGDKPOLLER_SRVUSER"]!!
    info[OracleConnection.CONNECTION_PROPERTY_PASSWORD] = Configuration.oracleDatabaseConfig["HM_INFOTRYGDPOLLER_SRVPWD"]!!
    info[OracleConnection.CONNECTION_PROPERTY_DEFAULT_ROW_PREFETCH] = "20"

    val ods = OracleDataSource()
    ods.url = Configuration.oracleDatabaseConfig["DATABASE_URL"]!!
    ods.connectionProperties = info

    try {
        val connection = ods.getConnection()

        val dbmd = connection.metaData
        println("Driver Name: " + dbmd.driverName)
        println("Driver Version: " + dbmd.driverVersion)

        println("")
        println("Tables we have access to:")
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT owner, table_name FROM all_tables").use { resultSet ->
                while (resultSet.next()) {
                    println(resultSet.getString(1) + " " + resultSet.getString(2) + " ")
                }
            }
        }

        println("")
        println("All tables:")
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT table_name FROM dba_tables").use { resultSet ->
                while (resultSet.next()) {
                    println(resultSet.getString(1) + " ")
                }
            }
        }

    } catch (e: Exception) {
        println("Exception: " + e.message.toString())
        e.printStackTrace()
    }

    Thread.sleep(1000*60*60*24)

    /*
    // Run our rapid and rivers implementation facing hm-rapid
    logg.info("Starting Rapid & Rivers app towards hm-rapid")
    rapidApp.start()
    logg.info("Application ending.")
     */
}
