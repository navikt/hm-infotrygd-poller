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
            this.validate{ it.demandValue("eventName", "InfotrygdAddToPollVedtakList") }
            this.validate{ it.requireKey("søknadsID", "fnr", "tknr", "saksblokk", "saksnr") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        val søknadsID = UUID.fromString(packet["søknadsID"].asText())
        val fnr = packet["fnr"].asText()
        val tknr = packet["tknr"].asText()
        val saksblokk = packet["saksblokk"].asText()
        val saksnr = packet["saksnr"].asText()

        store.add(søknadsID, fnr, tknr, saksblokk, saksnr)
    }
}