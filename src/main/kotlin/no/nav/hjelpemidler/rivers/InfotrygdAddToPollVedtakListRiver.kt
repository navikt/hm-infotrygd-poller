package no.nav.hjelpemidler.rivers

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.db.PollListStore
import java.util.UUID

private val logg = KotlinLogging.logger {}

internal class InfotrygdAddToPollVedtakListRiver(
    rapidsConnection: RapidsConnection,
    val store: PollListStore,
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            this.validate { it.demandValue("eventName", "hm-InfotrygdAddToPollVedtakList") }
            this.validate { it.requireKey("søknadId", "fnrBruker", "trygdekontorNr", "saksblokk", "saksnr") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val søknadId = UUID.fromString(packet["søknadId"].asText())
        val fnrBruker = packet["fnrBruker"].asText()
        val trygdekontorNr = packet["trygdekontorNr"].asText()
        val saksblokk = packet["saksblokk"].asText()
        val saksnr = packet["saksnr"].asText()

        kotlin.runCatching {
            store.add(søknadId, fnrBruker, trygdekontorNr, saksblokk, saksnr)
        }.onSuccess {
            if (it > 0) {
                logg.info { "La til søknad i listen for polling i Infotrygd: søknadId: $søknadId" }
            } else {
                logg.warn { "Feilet i å legge inn søknad i polling liste, kanskje den allerede er i listen(?), søknadId: $søknadId" }
            }
        }.onFailure {
            logg.error { "Feilet i å legge søknad inn i polling listen: søknadId: $søknadId" }
        }.getOrThrow()
    }
}
