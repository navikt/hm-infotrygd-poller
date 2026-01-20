package no.nav.hjelpemidler.db

import io.github.oshai.kotlinlogging.KotlinLogging
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import javax.sql.DataSource

private val logg = KotlinLogging.logger {}

internal class BrevstatistikkStore(private val ds: DataSource) {
    fun lagre(
        enhet: String,
        år: String,
        måned: String,
        brevkode: String,
        valg: String,
        undervalg: String,
        type: String,
        resultat: String,
        antall: Int,
    ): Int =
        using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    """
                        INSERT INTO public.v1_brevstatistikk (
                            enhet, ar, maned, brevkode, valg, undervalg, type, resultat, antall
                        ) VALUES (:enhet, :ar, :maned, :brevkode, :valg, :undervalg, :type, :resultat, :antall)
                        ON CONFLICT DO UPDATE SET antall = :antall, oppdatert = NOW();
                    """.trimIndent().split("\n").joinToString(" "),
                    enhet,
                    mapOf(
                        "enhet" to enhet,
                        "ar" to år,
                        "maned" to måned,
                        "brevkode" to brevkode,
                        "valg" to valg,
                        "undervalg" to undervalg,
                        "type" to type,
                        "resultat" to resultat,
                        "antall" to antall,
                    ),
                ).asUpdate,
            )
        }
}
