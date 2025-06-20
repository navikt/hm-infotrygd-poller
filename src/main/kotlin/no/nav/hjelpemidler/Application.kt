package no.nav.hjelpemidler

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.db.PollListStorePostgres
import no.nav.hjelpemidler.db.dataSource
import no.nav.hjelpemidler.db.migrate
import no.nav.hjelpemidler.db.waitForDB
import no.nav.hjelpemidler.metrics.Metrics
import no.nav.hjelpemidler.rivers.InfotrygdAddToPollVedtakListRiver
import no.nav.hjelpemidler.rivers.LoggRiver
import no.nav.hjelpemidler.service.infotrygdproxy.Infotrygd
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

private val logg = KotlinLogging.logger {}

private val mapper = jacksonObjectMapper().registerModule(JavaTimeModule())

@ExperimentalTime
fun main() {
    logg.teamInfo { "TEAM_LOGS_TEST" }
    logg.info { "LOGS_TEST" }

    if (!waitForDB(10.minutes)) {
        error("Databasen ble ikke tilgjengelig innen 10 minutter")
    }

    // Make sure our database migrations are up to date
    migrate()

    // Set up our database connection
    val store = PollListStorePostgres(dataSource())

    // Define our rapid and rivers app
    val rapidApp: RapidsConnection = RapidApplication.create(System.getenv()).apply {
        if (Configuration.application["APP_PROFILE"] != "prod") LoggRiver(this)
        InfotrygdAddToPollVedtakListRiver(this, store)
    }

    val metrics = Metrics(rapidApp)

    // Run background daemon for polling Infotrygd
    thread(isDaemon = true) {
        logg.info { "Poller daemon starting" }

        while (true) {
            // Check every 10s
            Thread.sleep(1000 * 10)

            // Catch any and all database errors
            try {
                // Get the next batch to check for results:
                val list = store.getPollingBatch(100)
                if (list.isEmpty()) continue

                logg.info { "Batch processing starting (size: ${list.size})" }
                if (Configuration.application["APP_PROFILE"] != "prod") logg.debug { "Batch: $list" }

                val innerList: MutableList<Infotrygd.Request> = mutableListOf()
                for (poll in list) {
                    innerList.add(
                        Infotrygd.Request(
                            poll.søknadID.toString(),
                            poll.fnrBruker,
                            poll.trygdekontorNr,
                            poll.saksblokk,
                            poll.saksnr,
                        ),
                    )
                }

                var results: List<Infotrygd.Response>?

                // Catch any Infotrygd related errors specially here since we expect lots of downtime
                try {
                    results = Infotrygd().batchQueryVedtakResultat(innerList)
                } catch (e: Exception) {
                    logg.warn(e) {
                        """
                            Problem polling Infotrygd, some downtime is expected though (up to 24hrs now and then) 
                            so we only warn here
                        """.trimIndent()
                    }

                    logg.warn(e) { "Sleeping for 1 minute due to error, before continuing" }
                    Thread.sleep(1000 * 60)
                    continue
                }

                if (Configuration.application["APP_PROFILE"] != "prod") {
                    logg.debug { "Infotrygd results:" }
                    for (result in results) logg.debug { "result: $result" }
                }

                // We have successfully batch checked for decisions on Vedtaker, now updating
                // last polled timestamp and number of pulls for each of the items in the list
                store.postPollingUpdate(list)

                // Check for decisions found:
                var decisionsMade = 0
                var avgQueryTimeElapsedCounter = 0.0
                var avgQueryTimeElapsedTotal = 0.0

                for (result in results) {
                    if (Configuration.application["APP_PROFILE"] == "dev") {
                        val mockVedtaksresultat = "IM"

                        // NOTE: Mocking out answer due to dev having a static database
                        avgQueryTimeElapsedCounter += result.queryTimeElapsedMs
                        avgQueryTimeElapsedTotal += 1.0

                        decisionsMade++
                        try {
                            rapidApp.publish(
                                mapper.writeValueAsString(
                                    VedtakResultat(
                                        "hm-VedtaksResultatFraInfotrygd",
                                        UUID.fromString(result.req.id),
                                        mockVedtaksresultat,
                                        LocalDate.now(),
                                        result.req.fnr,
                                        result.req.tknr,
                                        result.soknadsType ?: "",
                                    ),
                                ),
                            )
                            metrics.meldingTilRapidSuksess()
                        } catch (e: Exception) {
                            logg.error(e) { "Sending hm-VedtaksResultatFraInfotrygd to rapid failed" }
                            metrics.meldingTilRapidFeilet()
                            throw e
                        }

                        // Metrics on the different possible result types
                        metrics.vedtakResultatType(mockVedtaksresultat)

                        logg.debug { "Removing from store: $result" }
                        store.remove(UUID.fromString(result.req.id))

                        logg.info { "Vedtak decision found for søknadId: ${result.req.id}, queryTimeElapsed: ${result.queryTimeElapsedMs}ms" }

                        continue
                    }

                    if (result.error != null) {
                        logg.warn { "Decision polling failed with error: ${result.error}" }
                        continue
                    }

                    avgQueryTimeElapsedCounter += result.queryTimeElapsedMs
                    avgQueryTimeElapsedTotal += 1.0

                    if (result.vedtaksResult == "") { // No decision made yet
                        continue
                    }

                    logg.debug { "Søknadstype: ${result.soknadsType}" }

                    // Make the result available to the rest of the infrastructure some time after 06:00 the following
                    // day after the decision is made. This allows caseworkers to fix mistakes within the same day.
                    val waitUntil = result.vedtaksDate!!.plusDays(1).atTime(6, 0, 0)
                    if (LocalDateTime.now().isBefore(waitUntil)) {
                        logg.debug {
                            """
                                Decision has been made but we will wait until after 06:00 the next day before 
                                passing it on to the rapid, requestId: ${result.req.id}, 
                                vedtaksdato: ${result.vedtaksDate}, waitUntil: $waitUntil, now: ${LocalDateTime.now()}
                            """.trimIndent()
                        }
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
                                    result.vedtaksDate,
                                    result.req.fnr,
                                    result.req.tknr,
                                    result.soknadsType ?: "",
                                ),
                            ),
                        )
                        logg.info { "Sent vedtaksresultat to rapid with søknadstype: ${result.soknadsType}" }
                        metrics.meldingTilRapidSuksess()
                    } catch (e: Exception) {
                        logg.error(e) { "Sending hm-VedtaksResultatFraInfotrygd to rapid failed" }
                        metrics.meldingTilRapidFeilet()
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
                        metrics.timeElapsedInPollingList(elapsed.toDays())
                    }

                    // Metrics on the different possible result types
                    metrics.vedtakResultatType(result.vedtaksResult)

                    store.remove(UUID.fromString(result.req.id))
                    logg.info { "Vedtak decision found for søknadId: ${result.req.id}, queryTimeElapsed: ${result.queryTimeElapsedMs}ms" }
                }

                val avgQueryTime = avgQueryTimeElapsedCounter / avgQueryTimeElapsedTotal
                logg.info {
                    """
                        Processed batch successfully (decisions made / total batch size): $decisionsMade/${list.size}. 
                        Avg. time elapsed: $avgQueryTime
                    """.trimIndent()
                }

                val oldest = store.getOldestInPollList()
                if (oldest != null) {
                    logg.info { "Oldest in polling: $oldest" }
                } else {
                    logg.info { "getOldestInPollList returned null" }
                }
            } catch (e: Exception) {
                logg.error(e) { "Encountered an exception while processing Infotrygd polls" }

                // logg.error("error: sleeping for 10min due to error, before continuing")
                // Thread.sleep(1000*60*10)

                Thread.sleep(1000)
                continue
            }
        }
    }

    // Run our rapid and rivers implementation
    logg.info { "Starting Rapid & Rivers app" }
    rapidApp.start()
    logg.info { "Application ending." }
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
    @JsonProperty("trygdekontorNr")
    val trygdekontorNr: String,
    @JsonProperty("soknadsType")
    val soknadsType: String,
)
