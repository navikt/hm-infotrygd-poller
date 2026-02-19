package no.nav.hjelpemidler

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.hjelpemidler.db.BrevstatistikkStore
import no.nav.hjelpemidler.service.infotrygdproxy.Infotrygd
import java.time.LocalDate

private val logg = KotlinLogging.logger {}

fun Route.internal(brevstatistikkStore: BrevstatistikkStore) {
    post("/internal/brevstatistikk") {
        data class Request(
            val enhet: String,
            val minVedtaksdato: LocalDate,
            val maksVedtaksdato: LocalDate,
        )
        val req = call.receive<Request>()

        // Oppdater brevstatistikk
        logg.info { "Oppdaterer brevstatistikk (manuelt): enhet=${req.enhet}, minVedtaksdato=${req.minVedtaksdato}, maksVedtaksdato=${req.maksVedtaksdato}" }
        val brevstatistikk = Infotrygd().hentBrevstatistikk(
            req.enhet,
            req.minVedtaksdato,
            req.maksVedtaksdato,
        )

        val eldste = brevstatistikk.fold(LocalDate.EPOCH) { eldste, row ->
            val radDato = LocalDate.parse("${row.år}-${row.måned}-${row.dag}")
            if (eldste == LocalDate.EPOCH) return@fold radDato
            if (radDato.isBefore(eldste)) {
                radDato
            } else {
                eldste
            }
        }
        logg.info { "Fant ${brevstatistikk.count()} rader med brevstatistikk (eldste=$eldste)" }

        brevstatistikkStore.slettPeriode(req.enhet, req.minVedtaksdato, req.maksVedtaksdato)
        brevstatistikk.forEach { row ->
            brevstatistikkStore.lagre(
                row.enhet,
                LocalDate.parse("${row.år}-${row.måned}-${row.dag}"),
                row.brevkode,
                row.valg,
                row.undervalg,
                row.type,
                row.resultat,
                row.antall,
            )
        }

        call.respondText("OK")
    }
    post("/internal/brevstatistikk2") {
        runCatching {
            data class Request(
                val enhet: String,
                val minVedtaksdato: LocalDate,
                val maksVedtaksdato: LocalDate,
                val digitaleOppgaveIder: Set<String>,
            )

            val req = call.receive<Request>()

            // Oppdater brevstatistikk
            logg.info { "Oppdaterer brevstatistikk2 (manuelt): enhet=${req.enhet}, minVedtaksdato=${req.minVedtaksdato}, maksVedtaksdato=${req.maksVedtaksdato}" }
            val brevstatistikk = Infotrygd().hentBrevstatistikk2(
                req.enhet,
                req.minVedtaksdato,
                req.maksVedtaksdato,
                req.digitaleOppgaveIder,
            )

            val eldste = brevstatistikk.fold(LocalDate.EPOCH) { eldste, row ->
                if (eldste == LocalDate.EPOCH) return@fold row.dato
                if (row.dato.isBefore(eldste)) {
                    row.dato
                } else {
                    eldste
                }
            }
            logg.info { "Fant ${brevstatistikk.count()} rader med brevstatistikk (eldste=$eldste)" }

            brevstatistikkStore.slettPeriode2(req.enhet, req.minVedtaksdato, req.maksVedtaksdato)
            brevstatistikk.forEach { row ->
                brevstatistikkStore.lagre2(
                    row.enhet,
                    row.dato,
                    row.digital,
                    row.brevkode,
                    row.valg,
                    row.undervalg,
                    row.type,
                    row.resultat,
                    row.antall,
                )
            }

            call.respondText("OK")
        }.getOrElse { e ->
            logg.error(e) { "Feilet i å hente brevstatistikk2" }
            call.respond(HttpStatusCode.InternalServerError, "Feilet i å hente brevstatistikk2")
        }
    }
}
