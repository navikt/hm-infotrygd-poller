package no.nav.hjelpemidler.metrics

import no.nav.helse.rapids_rivers.MessageContext
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class Metrics(
    private val influxClient: InfluxClient,
    messageContext: MessageContext,
) {
    private val log = LoggerFactory.getLogger(Metrics::class.java)
    private val metricsProducer = MetricsProducer(messageContext)

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
        registerPoint(OLDEST_VEDTAK_IN_POLLING, mapOf("oldest" to created.toString()), emptyMap())
    }

    fun vedtaksResultatType(resultat: String) {
        registerPoint(RESULT_TYPE, mapOf("counter" to 1L), mapOf("resultat_type" to resultat))
    }

    fun timeElapsedInPollingList(days: Long) {
        registerPoint(TIME_ELAPSED_IN_POLLING_LIST, mapOf("days_elapsed" to days), emptyMap())
    }

    private fun registerPoint(measurement: String, fields: Map<String, Any>, tags: Map<String, String>) {
        try {
            log.info("Posting point to Influx: measurement {} fields {} tags {} ", measurement, fields, tags)
            influxClient.writeEvent(measurement, fields, tags)
            metricsProducer.hendelseOpprettet(measurement, fields, tags)
        } catch (e: Exception) {
            log.warn("Sending av metrics feilet.", e)
        }
    }

    companion object {
        private const val POLLER = "hm-infotrygd-poller"
        const val MELDING_TIL_RAPID_SUKSESS = "$POLLER.vedtaksresultat.rapid.suksess"
        const val MELDING_TIL_RAPID_FEILET = "$POLLER.vedtaksresultat.rapid.feilet"
        const val POLL_LIST_SIZE = "$POLLER.poll.list.size"
        const val BATCH_SIZE = "$POLLER.batch.size"
        const val AVG_QUERY_TIME = "$POLLER.avg.query.time.test"
        const val POLL_DECISIONSMADE = "$POLLER.poll.decisionsmade"
        const val INFOTRYGD_DOWNTIME = "$POLLER.infotrygd.downtime"
        const val OLDEST_VEDTAK_IN_POLLING = "$POLLER.poll.oldest"
        const val RESULT_TYPE = "$POLLER.result.type"
        const val TIME_ELAPSED_IN_POLLING_LIST = "$POLLER.poll.time.elapsed.in.list"
    }
}
