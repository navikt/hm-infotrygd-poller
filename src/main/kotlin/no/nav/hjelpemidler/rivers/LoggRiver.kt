package no.nav.hjelpemidler.rivers

import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.configuration.Configuration

private val logg = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class LoggRiver(
    rapidsConnection: RapidsConnection
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        if (Configuration.application["APP_PROFILE"]!! != "prod") {
            val rawJson = packet.toJson()
            sikkerlogg.info("DEBUG: Mottok pakke med Json: $rawJson")
        }
    }
}