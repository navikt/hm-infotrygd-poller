package no.nav.hjelpemidler.rivers

import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.teamInfo

private val logg = KotlinLogging.logger {}

class RiverRequiredKeyMissingException(msg: String) : Exception(msg)

interface PacketListenerWithOnError : River.PacketListener {
    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        logg.teamInfo { "River required keys had problems in parsing message from rapid: ${problems.toExtendedReport()}" }
        throw RiverRequiredKeyMissingException("River required keys had problems in parsing message from rapid, see Team Logs for details")
    }
}
