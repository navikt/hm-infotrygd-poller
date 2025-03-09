package no.nav.hjelpemidler.metrics

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.MessageContext

private val log = KotlinLogging.logger {}

class Metrics(
    messageContext: MessageContext,
) {
    private val metricsProducer = MetricsProducer(messageContext)

    fun meldingTilRapidSuksess() {
        registerPoint(MELDING_TIL_RAPID_SUKSESS, mapOf("counter" to 1L), emptyMap())
    }

    fun meldingTilRapidFeilet() {
        registerPoint(MELDING_TIL_RAPID_FEILET, mapOf("counter" to 1L), emptyMap())
    }

    fun vedtakResultatType(resultat: String) {
        registerPoint(RESULT_TYPE, mapOf("counter" to 1L), mapOf("resultat_type" to resultat))
    }

    fun timeElapsedInPollingList(days: Long) {
        registerPoint(TIME_ELAPSED_IN_POLLING_LIST, mapOf("days_elapsed" to days), emptyMap())
    }

    private fun registerPoint(measurement: String, fields: Map<String, Any>, tags: Map<String, String>) {
        try {
            log.debug { "Posting point to BigQuery, measurement: $measurement, fields: $fields, tags: $tags" }
            metricsProducer.hendelseOpprettet(measurement, fields, tags)
        } catch (e: Exception) {
            log.warn(e) { "Sending av metrics feilet." }
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
