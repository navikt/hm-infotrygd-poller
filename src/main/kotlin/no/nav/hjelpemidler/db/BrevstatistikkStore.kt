package no.nav.hjelpemidler.db

import io.github.oshai.kotlinlogging.KotlinLogging
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import java.time.LocalDate
import javax.sql.DataSource

private val logg = KotlinLogging.logger {}

internal class BrevstatistikkStore(private val ds: DataSource) {
    fun lagre(
        enhet: String,
        dato: LocalDate,
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
                            enhet, dato, brevkode, valg, undervalg, type, resultat, antall
                        ) VALUES (:enhet, :dato, :brevkode, :valg, :undervalg, :type, :resultat, :antall)
                        ON CONFLICT DO UPDATE SET antall = :antall, oppdatert = NOW();
                    """.trimIndent().split("\n").joinToString(" "),
                    enhet,
                    mapOf(
                        "enhet" to enhet,
                        "dato" to dato,
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
