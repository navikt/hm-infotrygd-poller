package no.nav.hjelpemidler.rivers

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import no.nav.hjelpemidler.db.PollListStore
import java.util.UUID

private val logg = KotlinLogging.logger {}

internal class InfotrygdAddToPollVedtakListRiver(
    rapidsConnection: RapidsConnection,
    val store: PollListStore,
) : PacketListenerWithOnError {

    init {
        River(rapidsConnection).apply {
            this.precondition { it.requireValue("eventName", "hm-InfotrygdAddToPollVedtakList") }
            this.validate { it.requireKey("søknadId", "fnrBruker", "trygdekontorNr", "saksblokk", "saksnr") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
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
