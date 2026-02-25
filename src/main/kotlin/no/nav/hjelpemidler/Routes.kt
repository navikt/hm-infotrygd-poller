package no.nav.hjelpemidler

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.hjelpemidler.db.BrevstatistikkStore
import no.nav.hjelpemidler.db.VedtaksstatistikkStore
import no.nav.hjelpemidler.service.infotrygdproxy.Infotrygd
import no.nav.hjelpemidler.service.soknadsbehandlingdb.SoknadsbehandlingDb
import java.time.LocalDate

private val logg = KotlinLogging.logger {}

fun Route.internal(brevstatistikkStore: BrevstatistikkStore, vedtaksstatistikkStore: VedtaksstatistikkStore) {
    post("/internal/brevstatistikk") {
        data class Request(
            val enheter: Set<String>,
            val minVedtaksdato: LocalDate,
            val maksVedtaksdato: LocalDate,
        )
        val req = call.receive<Request>()

        logg.info { "brevstatistikk (1/3): Henter infotrygd pker for digitale vedtak fra soknadsbehandling-db" }
        val pker = SoknadsbehandlingDb().hentInfotrygdPker(
            req.minVedtaksdato.minusDays(5),
            req.maksVedtaksdato.plusDays(5),
        )

        // Oppdater brevstatistikk
        logg.info { "brevstatistikk (2/3): Henter brevstatistikk (manuelt): enhet=${req.enheter}, minVedtaksdato=${req.minVedtaksdato}, maksVedtaksdato=${req.maksVedtaksdato}, antallPkerForDigitaleVedtak=${pker.count()}" }
        val brevstatistikk = Infotrygd().hentBrevstatistikk(
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
        logg.info { "brevstatistikk (3/3): Fant ${brevstatistikk.count()} rader med brevstatistikk (eldste=$eldste, enheterMedStatistikk=${brevstatistikk.distinctBy { it.enhet }.map { it.enhet }})" }
        brevstatistikkStore.slettPeriode(req.enheter, req.minVedtaksdato, req.maksVedtaksdato)
        brevstatistikk.forEach { row ->
            brevstatistikkStore.lagre(
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

    post("/internal/vedtaksstatistikk") {
        data class Request(
            val minVedtaksdato: LocalDate,
            val maksVedtaksdato: LocalDate,
        )
        val req = call.receive<Request>()

        // Oppdater brevstatistikk
        logg.info { "vedtaksstatistikk (1/2): Henter vedtaksstatistikk (manuelt): minVedtaksdato=${req.minVedtaksdato}, maksVedtaksdato=${req.maksVedtaksdato}" }
        val vedtaksstatistikk = Infotrygd().hentVedtaksstatistikk(
            req.minVedtaksdato,
            req.maksVedtaksdato,
        )

        val eldste = vedtaksstatistikk.fold(LocalDate.EPOCH) { eldste, row ->
            if (eldste == LocalDate.EPOCH) return@fold row.dato
            if (row.dato.isBefore(eldste)) {
                row.dato
            } else {
                eldste
            }
        }
        logg.info { "vedtaksstatistikk (2/2): Fant ${vedtaksstatistikk.count()} rader med vedtaksstatistikk (eldste=$eldste, enheterMedStatistikk=${vedtaksstatistikk.distinctBy { it.enhet }.map { it.enhet }})" }
        vedtaksstatistikkStore.slettPeriode(req.minVedtaksdato, req.maksVedtaksdato)
        vedtaksstatistikkStore.lagre(vedtaksstatistikk)
        call.respondText("OK")
    }
}
