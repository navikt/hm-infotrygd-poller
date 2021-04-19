package no.nav.hjelpemidler.metrics

import no.nav.hjelpemidler.configuration.Configuration
import org.influxdb.dto.Point
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit

class SensuMetrics {
    private val log = LoggerFactory.getLogger(SensuMetrics::class.java)
    private val sensuURL = Configuration.application["SENSU_URL"] ?: "http://localhost/unconfigured"
    private val sensuName = "hm-infotrygd-poller-events"

    private val httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    fun meldingTilRapidSuksess() {
        registerPoint(MELDING_TIL_RAPID_SUKSESS, mapOf("counter" to 1L), emptyMap())
    }

    fun meldingTilRapidFeilet() {
        registerPoint(MELDING_TIL_RAPID_FEILET, mapOf("counter" to 1L), emptyMap())
    }

    fun pollListSize(size: Int) {
        registerPoint(POLL_LIST_SIZE, mapOf("gauge" to size), emptyMap())
    }

    fun batchSize(size: Int) {
        registerPoint(BATCH_SIZE, mapOf("gauge" to size), emptyMap())
    }

    fun avgQueryTimeMS(qt: Double) {
        registerPoint(AVG_QUERY_TIME, mapOf("avg_time" to qt), emptyMap())
    }

    fun decisionsMadeInPolling(decisionsMade: Long) {
        registerPoint(POLL_DECISIONSMADE, mapOf("gauge" to decisionsMade), emptyMap())
    }

    fun infotrygdDowntime(downtime: Double) {
        registerPoint(INFOTRYGD_DOWNTIME, mapOf("down_time" to downtime), emptyMap())
    }

    fun oldestInPolling(created: LocalDateTime) {
        registerPoint(OLDEST_VEDTAK_IN_POLLING, mapOf("created" to created), emptyMap())
    }

    private fun registerPoint(measurement: String, fields: Map<String, Any>, tags: Map<String, String>) {
        val point = Point.measurement(measurement)
            .time(TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()), TimeUnit.NANOSECONDS)
            .tag(tags)
            .tag(DEFAULT_TAGS)
            .fields(fields)
            .build()

        sendEvent(SensuEvent(sensuName, point.lineProtocol()))
    }

    private fun sendEvent(sensuEvent: SensuEvent) {
        val body = HttpRequest.BodyPublishers.ofString(sensuEvent.json)
        val request = HttpRequest.newBuilder()
            .POST(body)
            .uri(URI.create(sensuURL))
            .setHeader("User-Agent", "Java 11 HttpClient Bot") // add request header
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", UUID.randomUUID().toString())
            .header("Accepts", "application/json")
            .build()
        val response: HttpResponse<String> = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            log.error("sensu metrics unexpected response code from proxy: {}", response.statusCode())
            log.error("sensu metrics response: {}", response.body().toString())
        }
    }

    private class SensuEvent(sensuName: String, output: String) {
        val json: String = "{" +
            "\"name\":\"" + sensuName + "\"," +
            "\"type\":\"metric\"," +
            "\"handlers\":[\"events_nano\"]," +
            "\"output\":\"" + output.replace("\\", "\\\\", true) + "\"," +
            "\"status\":0" +
            "}"
    }

    companion object {
        private val DEFAULT_TAGS: Map<String, String> = mapOf(
            "application" to (Configuration.application["NAIS_APP_NAME"] ?: "hm-infotrygd-poller"),
            "cluster" to (Configuration.application["NAIS_CLUSTER_NAME"] ?: "dev-fss"),
            "namespace" to (Configuration.application["NAIS_NAMESPACE"] ?: "teamdigihot")
        )

        private const val POLLER = "hm-infotrygd-poller"
        const val MELDING_TIL_RAPID_SUKSESS = "$POLLER.vedtaksresultat.rapid.suksess"
        const val MELDING_TIL_RAPID_FEILET = "$POLLER.vedtaksresultat.rapid.feilet"
        const val POLL_LIST_SIZE = "$POLLER.poll.list.size"
        const val BATCH_SIZE = "$POLLER.batch.size"
        const val AVG_QUERY_TIME = "$POLLER.avg.query.time.test"
        const val POLL_DECISIONSMADE = "$POLLER.poll.decisionsmade"
        const val INFOTRYGD_DOWNTIME = "$POLLER.infotrygd.downtime"
        const val OLDEST_VEDTAK_IN_POLLING = "$POLLER.poll.oldest"
    }
}
