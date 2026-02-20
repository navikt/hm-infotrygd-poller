package no.nav.hjelpemidler

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.hjelpemidler.db.BrevstatistikkStore
import no.nav.hjelpemidler.service.infotrygdproxy.Infotrygd
import no.nav.hjelpemidler.service.soknadsbehandlingdb.SoknadsbehandlingDb
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
        data class Request(
            val enheter: Set<String>,
            val minVedtaksdato: LocalDate,
            val maksVedtaksdato: LocalDate,
        )
        val req = call.receive<Request>()

        logg.info { "brevstatistikk2 (1/3): Henter infotrygd pker for digitale vedtak fra soknadsbehandling-db" }
        val pker = SoknadsbehandlingDb().hentInfotrygdPker(
            req.minVedtaksdato.minusDays(5),
            req.maksVedtaksdato.plusDays(5),
        )

        // Oppdater brevstatistikk
        logg.info { "brevstatistikk2 (2/3): Henter brevstatistikk (manuelt): enhet=${req.enheter}, minVedtaksdato=${req.minVedtaksdato}, maksVedtaksdato=${req.maksVedtaksdato}, antallPkerForDigitaleVedtak=${pker.count()}" }
        val brevstatistikk = Infotrygd().hentBrevstatistikk2(
            req.enheter,
            req.minVedtaksdato,
            req.maksVedtaksdato,
            pker,
        )

        val eldste = brevstatistikk.fold(LocalDate.EPOCH) { eldste, row ->
            if (eldste == LocalDate.EPOCH) return@fold row.dato
            if (row.dato.isBefore(eldste)) {
                row.dato
            } else {
                eldste
            }
        }
        logg.info { "brevstatistikk2 (3/3): Fant ${brevstatistikk.count()} rader med brevstatistikk (eldste=$eldste, enheterMedStatistikk=${brevstatistikk.distinctBy { it.enhet }.map { it.enhet }})" }
        brevstatistikkStore.slettPeriode2(req.enheter, req.minVedtaksdato, req.maksVedtaksdato)
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
    }
}
