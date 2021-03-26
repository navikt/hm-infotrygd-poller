package no.nav.hjelpemidler

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import java.time.LocalDate
import java.util.*
import kotlin.concurrent.thread
import kotlin.time.*

private val logg = KotlinLogging.logger {}
// private val sikkerlogg = KotlinLogging.logger("tjenestekall")

private val mapper = jacksonObjectMapper().registerModule(JavaTimeModule())

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
        logg.info("Poller daemon starting")
        while (true) {
            // Check every 10s
            Thread.sleep(1000*10)

            // Catch any and all database errors
            try {

                // Get the next batch to check for results:
                val list = store.getPollingBatch(100)
                if (list.isEmpty()) continue

                logg.info("Batch processing starting (size: ${list.size})")
                logg.debug("DEBUG: Batch: $list")

                val innerList: MutableList<Infotrygd.Request> = mutableListOf()
                for (poll in list) {
                    logg.debug("DEBUG: innerList: poll: $poll")
                    innerList.add(Infotrygd.Request(
                        poll.søknadID.toString(),
                        poll.fnrBruker,
                        poll.trygdekontorNr,
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

                    logg.warn("warn: sleeping for 1min due to error, before continuing")
                    Thread.sleep(1000*60)
                    continue
                }

                logg.debug("DEBUG: Infotrygd results:")
                for (result in results) logg.debug("DEBUG: - result: $result")

                // We have successfully batch checked for decisions on Vedtaker, now updating
                // last polled timestamp and number of pulls for each of the items in the list
                store.postPollingUpdate(list)

                // Check for decisions found:
                var decisionsMade = 0
                var avgQueryTimeElapsed_counter = 0.0
                var avgQueryTimeElapsed_total = 0.0
                for (result in results) {
                    if (result.error != null) {
                        logg.error("error: decision polling failed with error: ${result.error}")
                        continue
                    }

                    avgQueryTimeElapsed_counter += result.queryTimeElapsedMs
                    avgQueryTimeElapsed_total += 1.0

                    if (result.vedtaksResult == "") continue // No decision made yet

                    // Decision made, lets send it out on the rapid and then delete it from the polling list
                    decisionsMade++
                    rapidApp.publish(mapper.writeValueAsString(VedtakResultat(
                        "hm-VedtaksResultatFraInfotrygd",
                        UUID.fromString(result.req.id),
                        result.vedtaksResult!!,
                        result.vedtaksDate!!,
                    )))

                    logg.debug("DEBUG: Removing from store: $result")
                    store.remove(UUID.fromString(result.req.id))

                    logg.info("Vedtak decision found for søknadsID=${result.req.id} queryTimeElapsed=${result.queryTimeElapsedMs}ms")
                }

                logg.info("Processed batch successfully (decisions made / total batch size): $decisionsMade/${list.size}. Avg. time elapsed: ${avgQueryTimeElapsed_counter/avgQueryTimeElapsed_total}ms")

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

data class VedtakResultat (
    val eventName: String,
    val søknadID: UUID,
    val vedtaksResultat: String,
    val vedtaksDato: LocalDate,
)