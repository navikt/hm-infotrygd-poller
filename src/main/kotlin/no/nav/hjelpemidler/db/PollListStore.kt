package no.nav.hjelpemidler.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import javax.sql.DataSource

private val logg = KotlinLogging.logger {}

internal interface PollListStore {
    fun add(søknadsID: String, fnr: String, tknr: String, saksblokk: String, saksnr: String)
    fun remove(søknadsID: String)
    fun getPollingBatch(size: Int): List<Poll>
    fun postPollingUpdate(list: List<Poll>)
}

data class Poll (
    val søknadsID: String,
    val fnr: String,
    val tknr: String,
    val saksblokk: String,
    val saksnr: String,
    val numberOfPollings: Int,
    val lastPolled: LocalDateTime,
)

internal class PollListStorePostgres(private val ds: DataSource) : PollListStore {

    override fun add(søknadsID: String, fnr: String, tknr: String, saksblokk: String, saksnr: String) {
        @Language("PostgreSQL") val statement = """
            INSERT INTO public.V1_POLL_LIST (
                SOKNADS_ID,
                FNR_BRUKER,
                TKNR,
                SAKSBLOKK,
                SAKSNR
            ) VALUES (?, ?, ?, ?, ?)
        """.trimIndent().split("\n").joinToString(" ")

        val effectedRowCount = time("add") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        statement,
                        søknadsID,
                        fnr,
                        tknr,
                        saksblokk,
                        saksnr,
                    ).asUpdate
                )
            }
        }

        if (effectedRowCount != 1) throw Exception("unexpected effected row count of $effectedRowCount (expected 1) when adding a Vedtak to monitor/poll")
    }

    override fun remove(søknadsID: String) {
        @Language("PostgreSQL") val statement = """
            DELETE FROM public.V1_POLL_LIST
            WHERE SOKNADS_ID = ?
        """.trimIndent().split("\n").joinToString(" ")

       if (!time("remove") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        statement,
                        søknadsID,
                    ).asExecute
                )
            }
        }) {
           throw Exception("removing poll item from poll list failed unexpectedly with the command returning false")
       }
    }

    override fun getPollingBatch(size: Int): List<Poll> {
        @Language("PostgreSQL") val statement = """
            SELECT
                SOKNADS_ID,
                FNR_BRUKER,
                TKNR,
                SAKSBLOKK,
                SAKSNR,
                NUMBER_OF_POLLINGS,
                LAST_POLL
            FROM public.V1_POLL_LIST
            LIMIT $size
        """.trimIndent().split("\n").joinToString(" ")

        return time("getPollingBatch") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        statement,
                    ).map {
                        logg.info("DEBUG: here: ${it.toString()}")
                        Poll(
                            it.string("SOKNADS_ID"),
                            it.string("FNR_BRUKER"),
                            it.string("TKNR"),
                            it.string("SAKSBLOKK"),
                            it.string("SAKSNR"),
                            it.int("NUMBER_OF_POLLINGS"),
                            it.localDateTime("LAST_POLL"),
                        )
                    }.asList
                )
            }
        }
    }

    override fun postPollingUpdate(list: List<Poll>) {
        // Update NUMBER_OF_POLLINGS and LAST_POLL columns for the rows we picked out to poll:
        val søknadsIDs: MutableList<String> = mutableListOf()
        for (poll in list) {
            søknadsIDs.add("'${poll.søknadsID}'")
        }

        @Language("PostgreSQL") val statement = """
            UPDATE public.V1_POLL_LIST
            SET NUMBER_OF_POLLINGS = NUMBER_OF_POLLINGS + 1 
            SET LAST_POLL = now()
            WHERE SOKNADS_ID IN (${søknadsIDs.joinToString(", ")})
        """.trimIndent().split("\n").joinToString(" ")

        val effectedRows = time("postPollingUpdate") {
            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        statement,
                    ).asUpdate
                )
            }
        }

        // The number of updated rows does not match the number of rows selected for polling
        if (effectedRows != list.size) {
            throw Exception("during attempt to update column LAST_POLL ")
        }
    }

    private inline fun <T : Any?> time(queryName: String, function: () -> T) =
            function()
        /*
        Prometheus.dbTimer.labels(queryName).startTimer().let { timer ->
            function().also {
                timer.observeDuration()
            }
        }*/

}
