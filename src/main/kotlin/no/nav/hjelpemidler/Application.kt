package no.nav.hjelpemidler

import mu.KotlinLogging
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.helse.rapids_rivers.KafkaConfig
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.db.PollListStorePostgres
import no.nav.hjelpemidler.rivers.LoggRiver
import no.nav.hjelpemidler.service.infotrygdproxy.Infotrygd
import no.nav.hjelpemidler.db.dataSource
import no.nav.hjelpemidler.db.migrate
import no.nav.hjelpemidler.db.waitForDB
import no.nav.hjelpemidler.rivers.InfotrygdAddToPollVedtakListRiver
import java.net.InetAddress
import kotlin.concurrent.thread
import kotlin.time.*

private val logg = KotlinLogging.logger {}
// private val sikkerlogg = KotlinLogging.logger("tjenestekall")

@ExperimentalTime
fun main() {
    if (!waitForDB(10.minutes)) {
        throw Exception("database never became available withing the deadline")
    }

    // Make sure our database migrations are up to date
    migrate()

    // Set up our database connection
    val store = PollListStorePostgres(dataSource())

    // Define our rapid and rivers app
    var rapidApp: RapidsConnection? = null
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
        InfotrygdAddToPollVedtakListRiver(this, store)
    }

    // Run background daemon for polling Infotrygd
    thread(isDaemon = true) {
        logg.info("DEBUG: Polling starting")
        while (true) {
            // Check every 10s
            logg.info("DEBUG: sleeping 10s")
            Thread.sleep(1000*10)

            // Catch any and all database errors
            try {

                // Get the next batch to check for results:
                val list = store.getPollingBatch(100)
                logg.info("DEBUG: fetched list: $list")
                if (list.isEmpty()) continue

                val innerList: MutableList<Infotrygd.Request> = mutableListOf()
                for (poll in list) {
                    logg.info("DEBUG: innerList: poll: $poll")
                    innerList.add(Infotrygd.Request(
                        poll.søknadsID,
                        poll.fnr,
                        poll.tknr,
                        poll.saksblokk,
                        poll.saksnr,
                    ))
                }

                var results: List<Infotrygd.Response>? = null

                // Catch any Infotrygd related errors specially here since we expect lots of downtime
                try {
                    results = Infotrygd().batchQueryVedtakResultat(innerList)
                } catch(e: Exception) {
                    logg.warn("warn: problem polling Infotrygd, some downtime is expected though (up to 24hrs now and then) so we only warn here: $e")
                    e.printStackTrace()

                    logg.warn("warn: sleeping for 10min due to error, before continuing")
                    Thread.sleep(1000*60*10)
                    continue
                }

                logg.info("DEBUG: Infotrygd results:")
                for (result in results) logg.info("DEBUG: - result: $result")

                // We have successfully batch checked for decisions on Vedtaker, now updating
                // last polled timestamp and number of pulls for each of the items in the list
                store.postPollingUpdate(list)

                // Check for decisions found:
                for (result in results) {
                    if (result.result == "") continue // No decision made yet

                    // Decision made, lets send it out on the rapid and then delete it from the polling list
                    rapidApp.publish("""
                        {
                            "eventName": "VedtaksResultat",
                            "søknadsID": "${result.req.id}",
                            "resultat": "${result.result}",
                            "vedtaksDate": "${result.vedtaksDate}"
                        }
                    """.trimIndent())

                    logg.info("DEBUG: Removing from store: $result")
                    store.remove(result.req.id)
                }

            } catch (e: Exception) {
                logg.error("error: encountered an exception while processing Infotrygd polls: $e")
                e.printStackTrace()

                logg.error("error: sleeping for 10min due to error, before continuing")
                Thread.sleep(1000*60*10)
                continue
            }
        }
    }

    // Run our rapid and rivers implementation
    logg.info("Starting Rapid & Rivers app")
    rapidApp.start()
    logg.info("Application ending.")
}
