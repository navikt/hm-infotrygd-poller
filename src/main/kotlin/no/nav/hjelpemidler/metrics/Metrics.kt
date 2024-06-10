package no.nav.hjelpemidler.metrics

import no.nav.helse.rapids_rivers.MessageContext
import org.slf4j.LoggerFactory

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
        const val RESULT_TYPE = "$POLLER.result.type"
        const val TIME_ELAPSED_IN_POLLING_LIST = "$POLLER.poll.time.elapsed.in.list"
    }
}
