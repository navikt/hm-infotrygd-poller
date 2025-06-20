package no.nav.hjelpemidler.rivers

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.teamDebug

private val logg = KotlinLogging.logger {}

class LoggRiver(
    rapidsConnection: RapidsConnection,
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        if (Configuration.application["APP_PROFILE"]!! != "prod") {
            val rawJson = packet.toJson()
            logg.teamDebug { "Mottok pakke med Json: '$rawJson'" }
        }
    }
}
