package no.nav.hjelpemidler.db

import io.github.oshai.kotlinlogging.KotlinLogging
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.hjelpemidler.service.infotrygdproxy.Infotrygd
import java.time.LocalDate
import javax.sql.DataSource

private val logg = KotlinLogging.logger {}

class VedtaksstatistikkStore(private val ds: DataSource) {
    fun slettPeriode(minVedtaksdato: LocalDate, maksVedtaksdato: LocalDate) =
        using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    """
                        DELETE FROM public.v1_vedtaksstatistikk
                        WHERE dato >= :minVedtaksdato AND dato <= :maksVedtaksdato
                    """.trimIndent().split("\n").joinToString(" "),
                    mapOf(
                        "minVedtaksdato" to minVedtaksdato,
                        "maksVedtaksdato" to maksVedtaksdato,
                    ),
                ).asUpdate,
            )
        }

    fun lagre(vedtaksstatistikk: List<Infotrygd.Vedtaksstatistikk>): List<Int> =
        using(sessionOf(ds)) { session ->
            session.batchPreparedNamedStatement(
                """
                    INSERT INTO public.v1_vedtaksstatistikk (
                        enhet, dato, valg, undervalg, type, resultat, antall
                    ) VALUES (:enhet, :dato, :valg, :undervalg, :type, :resultat, :antall)
                """.trimIndent(),
                vedtaksstatistikk.map {
                    mapOf(
                        "enhet" to it.enhet,
                        "dato" to it.dato,
                        "valg" to it.valg,
                        "undervalg" to it.undervalg,
                        "type" to it.type,
                        "resultat" to it.resultat,
                        "antall" to it.antall,
                    )
                },
            )
        }
}
