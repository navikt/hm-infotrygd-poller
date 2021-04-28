package no.nav.hjelpemidler

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.KafkaConfig
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.db.PollListStorePostgres
import no.nav.hjelpemidler.db.dataSource
import no.nav.hjelpemidler.db.migrate
import no.nav.hjelpemidler.db.waitForDB
import no.nav.hjelpemidler.metrics.SensuMetrics
import no.nav.hjelpemidler.rivers.InfotrygdAddToPollVedtakListRiver
import no.nav.hjelpemidler.rivers.LoggRiver
import no.nav.hjelpemidler.service.infotrygdproxy.Infotrygd
import java.net.InetAddress
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
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
    var rapidApp: RapidsConnection?
    rapidApp = RapidApplication.Builder(
        RapidApplication.RapidApplicationConfig(
            Configuration.rapidConfig["RAPID_APP_NAME"],
            InetAddress.getLocalHost().hostName,
            Configuration.rapidConfig["KAFKA_RAPID_TOPIC"]!!,
            emptyList(),
            KafkaConfig(
                Configuration.rapidConfig["KAFKA_BROKERS"]!!,
                Configuration.rapidConfig["KAFKA_CONSUMER_GROUP_ID"]!!,
                Configuration.rapidConfig["KAFKA_CLIENT_ID"]!!,
                null,
                null,
                Configuration.rapidConfig["KAFKA_TRUSTSTORE_PATH"]!!,
                Configuration.rapidConfig["KAFKA_CREDSTORE_PASSWORD"]!!,
                "jks",
                "PKCS12",
                Configuration.rapidConfig["KAFKA_KEYSTORE_PATH"]!!,
                Configuration.rapidConfig["KAFKA_CREDSTORE_PASSWORD"]!!,
                Configuration.rapidConfig["KAFKA_RESET_POLICY"]!!,
                false,
                null,
                null,
            ),
            8080,
        )
    ).build().apply {
        if (Configuration.application["APP_PROFILE"] != "prod") LoggRiver(this)
        InfotrygdAddToPollVedtakListRiver(this, store)
    }

    // Run background daemon for polling Infotrygd
    thread(isDaemon = true) {
        logg.info("Poller daemon starting")

        var firstNoticedInfotrygdWasDown: LocalDateTime? = null
        while (true) {
            // Check every 10s
            Thread.sleep(1000 * 10)

            // Catch any and all database errors
            try {
                // Get the next batch to check for results:
                val list = store.getPollingBatch(100)
                if (list.isEmpty()) continue

                // Report total size of poll list to sensu
                val pollListSize = store.getPollListSize()
                if (pollListSize != null) SensuMetrics().pollListSize(pollListSize)

                // Report batch size we are polling to sensu
                SensuMetrics().batchSize(list.size)

                logg.info("Batch processing starting (size: ${list.size})")
                if (Configuration.application["APP_PROFILE"] != "prod") logg.debug("DEBUG: Batch: $list")

                val innerList: MutableList<Infotrygd.Request> = mutableListOf()
                for (poll in list) {
                    innerList.add(
                        Infotrygd.Request(
                            poll.søknadID.toString(),
                            poll.fnrBruker,
                            poll.trygdekontorNr,
                            poll.saksblokk,
                            poll.saksnr,
                        )
                    )
                }

                var results: List<Infotrygd.Response>?

                // Catch any Infotrygd related errors specially here since we expect lots of downtime
                try {
                    results = Infotrygd().batchQueryVedtakResultat(innerList)
                    SensuMetrics().infotrygdDowntime(0.0)
                    if (firstNoticedInfotrygdWasDown != null) {
                        firstNoticedInfotrygdWasDown = null
                    }
                } catch (e: Exception) {
                    logg.warn("warn: problem polling Infotrygd, some downtime is expected though (up to 24hrs now and then) so we only warn here: $e")
                    e.printStackTrace()

                    if (firstNoticedInfotrygdWasDown == null) firstNoticedInfotrygdWasDown = LocalDateTime.now()
                    val elapsed = Duration.between(firstNoticedInfotrygdWasDown, LocalDateTime.now())
                    if (elapsed.toSeconds().toInt() == 0) {
                        // Lets us notify in the panel right away, even if we just set firstNoticedInfotrygdWasDown above.
                        SensuMetrics().infotrygdDowntime(0.01)
                    } else {
                        SensuMetrics().infotrygdDowntime((elapsed.toSeconds()).toDouble() / 60.0)
                    }

                    logg.warn("warn: sleeping for 1min due to error, before continuing")
                    Thread.sleep(1000 * 60)
                    continue
                }

                if (Configuration.application["APP_PROFILE"] != "prod") {
                    logg.debug("DEBUG: Infotrygd results:")
                    for (result in results) logg.debug("DEBUG: - result: $result")
                }

                // We have successfully batch checked for decisions on Vedtaker, now updating
                // last polled timestamp and number of pulls for each of the items in the list
                store.postPollingUpdate(list)

                // Check for decisions found:
                var decisionsMade = 0
                var avgQueryTimeElapsed_counter = 0.0
                var avgQueryTimeElapsed_total = 0.0

                for (result in results) {
                    if (Configuration.application["APP_PROFILE"] == "dev") {
                        val mockVedtaksresultat = "A"

                        // NOTE: Mocking out answer due to dev having a static database
                        avgQueryTimeElapsed_counter += result.queryTimeElapsedMs
                        avgQueryTimeElapsed_total += 1.0

                        decisionsMade++
                        try {
                            rapidApp.publish(
                                mapper.writeValueAsString(
                                    VedtakResultat(
                                        "hm-VedtaksResultatFraInfotrygd",
                                        UUID.fromString(result.req.id),
                                        mockVedtaksresultat,
                                        LocalDate.now(),
                                        result.req.fnr
                                    )
                                )
                            )
                            SensuMetrics().meldingTilRapidSuksess()
                        } catch (e: Exception) {
                            logg.error("error: sending hm-VedtaksResultatFraInfotrygd to rapid failed: $e")
                            e.printStackTrace()
                            SensuMetrics().meldingTilRapidFeilet()
                            throw e
                        }

                        // Metrics on the different possible result types
                        SensuMetrics().vedtaksResultatType(mockVedtaksresultat)

                        logg.debug("DEBUG: Removing from store: $result")
                        store.remove(UUID.fromString(result.req.id))

                        logg.info("Vedtak decision found for søknadsID=${result.req.id} queryTimeElapsed=${result.queryTimeElapsedMs}ms")

                        continue
                    }

                    if (result.error != null) {
                        logg.error("error: decision polling failed with error: ${result.error}")
                        continue
                    }

                    avgQueryTimeElapsed_counter += result.queryTimeElapsedMs
                    avgQueryTimeElapsed_total += 1.0

                    if (result.vedtaksResult == "") { // No decision made yet
                        continue
                    }

                    // Decision made, lets send it out on the rapid and then delete it from the polling list
                    decisionsMade++
                    try {
                        rapidApp.publish(
                            mapper.writeValueAsString(
                                VedtakResultat(
                                    "hm-VedtaksResultatFraInfotrygd",
                                    UUID.fromString(result.req.id),
                                    result.vedtaksResult!!,
                                    result.vedtaksDate!!,
                                    result.req.fnr
                                )
                            )
                        )
                        SensuMetrics().meldingTilRapidSuksess()
                    } catch (e: Exception) {
                        logg.error("error: sending hm-VedtaksResultatFraInfotrygd to rapid failed: $e")
                        e.printStackTrace()
                        SensuMetrics().meldingTilRapidFeilet()
                        throw e
                    }

                    // Find original item in list that matches this result and report the elapsed time in the polling
                    // list before the decision was made (note: number in calendar days)
                    var created: LocalDateTime? = null
                    for (item in list) {
                        if (item.søknadID.toString() == result.req.id) {
                            created = item.created
                            break
                        }
                    }
                    if (created != null) {
                        val elapsed = Duration.between(created, LocalDateTime.now())
                        SensuMetrics().timeElapsedInPollingList(elapsed.toDays())
                    }

                    // Metrics on the different possible result types
                    SensuMetrics().vedtaksResultatType(result.vedtaksResult)

                    store.remove(UUID.fromString(result.req.id))
                    logg.info("Vedtak decision found for søknadsID=${result.req.id} queryTimeElapsed=${result.queryTimeElapsedMs}ms")
                }

                val avgQueryTime = avgQueryTimeElapsed_counter / avgQueryTimeElapsed_total
                logg.info("Processed batch successfully (decisions made / total batch size): $decisionsMade/${list.size}. Avg. time elapsed: $avgQueryTime")

                SensuMetrics().avgQueryTimeMS(avgQueryTime)
                SensuMetrics().decisionsMadeInPolling(decisionsMade.toLong())

                val oldest = store.getOldestInPollList()
                if (oldest != null) {
                    logg.info("oldest in polling: $oldest")
                    SensuMetrics().oldestInPolling(oldest)
                } else {
                    logg.info("getOldestInPollList returned null")
                }

                // Report total size of poll list to sensu after results have come in
                val pollListSize2 = store.getPollListSize()
                if (pollListSize2 != null && pollListSize2 != pollListSize) {
                    SensuMetrics().pollListSize(pollListSize2)
                }
            } catch (e: Exception) {
                logg.error("error: encountered an exception while processing Infotrygd polls: $e")
                e.printStackTrace()

                // logg.error("error: sleeping for 10min due to error, before continuing")
                // Thread.sleep(1000*60*10)

                Thread.sleep(1000)
                continue
            }
        }
    }

    // Run our rapid and rivers implementation
    logg.info("Starting Rapid & Rivers app")
    rapidApp.start()
    logg.info("Application ending.")
}

data class VedtakResultat(
    @JsonProperty("eventName")
    val eventName: String,
    @JsonProperty("søknadID")
    val søknadID: UUID,
    @JsonProperty("vedtaksResultat")
    val vedtaksResultat: String,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonProperty("vedtaksDato")
    val vedtaksDato: LocalDate,
    @JsonProperty("fnrBruker")
    val fnrBruker: String,
)
