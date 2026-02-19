package no.nav.hjelpemidler.db

import io.github.oshai.kotlinlogging.KotlinLogging
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import java.time.LocalDate
import javax.sql.DataSource

private val logg = KotlinLogging.logger {}

class BrevstatistikkStore(private val ds: DataSource) {
    fun slettPeriode(enhet: String, minVedtaksdato: LocalDate, maksVedtaksdato: LocalDate) =
        using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    """
                        DELETE FROM public.v1_brevstatistikk
                        WHERE enhet = :enhet AND dato >= :minVedtaksdato AND dato <= :maksVedtaksdato
                    """.trimIndent().split("\n").joinToString(" "),
                    enhet,
                    mapOf(
                        "enhet" to enhet,
                        "minVedtaksdato" to minVedtaksdato,
                        "maksVedtaksdato" to maksVedtaksdato,
                    ),
                ).asUpdate,
            )
        }

    fun slettPeriode2(enhet: String, minVedtaksdato: LocalDate, maksVedtaksdato: LocalDate) =
        using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    """
                        DELETE FROM public.v1_brevstatistikk2
                        WHERE enhet = :enhet AND dato >= :minVedtaksdato AND dato <= :maksVedtaksdato
                    """.trimIndent().split("\n").joinToString(" "),
                    enhet,
                    mapOf(
                        "enhet" to enhet,
                        "minVedtaksdato" to minVedtaksdato,
                        "maksVedtaksdato" to maksVedtaksdato,
                    ),
                ).asUpdate,
            )
        }

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
                        ON CONFLICT (enhet, dato, brevkode, valg, undervalg, type, resultat)
                        DO UPDATE SET antall = :antall, oppdatert = NOW()
                    """.trimIndent().split("\n").joinToString(" "),
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

    fun lagre2(
        enhet: String,
        dato: LocalDate,
        digital: Boolean,
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
                        INSERT INTO public.v1_brevstatistikk2 (
                            enhet, dato, digital, brevkode, valg, undervalg, type, resultat, antall
                        ) VALUES (:enhet, :dato, :digital, :brevkode, :valg, :undervalg, :type, :resultat, :antall)
                        ON CONFLICT (enhet, dato, digital, brevkode, valg, undervalg, type, resultat)
                        DO UPDATE SET antall = :antall, oppdatert = NOW()
                    """.trimIndent().split("\n").joinToString(" "),
                    mapOf(
                        "enhet" to enhet,
                        "dato" to dato,
                        "digital" to digital,
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
