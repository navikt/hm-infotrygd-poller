package no.nav.hjelpemidler.rivers

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
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

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        if (Configuration.application["APP_PROFILE"]!! != "prod") {
            val rawJson = packet.toJson()
            logg.teamDebug { "Mottok pakke med Json: '$rawJson'" }
        }
    }
}
