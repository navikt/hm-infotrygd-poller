package no.nav.hjelpemidler.rivers

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.db.PollListStore
import java.util.*

private val logg = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class InfotrygdAddToPollVedtakListRiver(
    rapidsConnection: RapidsConnection,
    val store: PollListStore,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            this.validate{ it.demandValue("eventName", "hm-InfotrygdAddToPollVedtakList") }
            this.validate{ it.requireKey("søknadId", "fnrBruker", "trygdekontorNr", "saksblokk", "saksnr") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        val søknadId = UUID.fromString(packet["søknadId"].asText())
        val fnrBruker = packet["fnrBruker"].asText()
        val trygdekontorNr = packet["trygdekontorNr"].asText()
        val saksblokk = packet["saksblokk"].asText()
        val saksnr = packet["saksnr"].asText()

        logg.info("La til søknad i listen for polling i Infotrygd: $søknadId")
        store.add(søknadId, fnrBruker, trygdekontorNr, saksblokk, saksnr)
    }
}
