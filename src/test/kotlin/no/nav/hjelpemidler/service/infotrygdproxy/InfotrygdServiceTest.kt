package no.nav.hjelpemidler.service.infotrygdproxy

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import no.nav.hjelpemidler.VedtakResultat
import okhttp3.internal.wait
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

internal class InfotrygdServiceTest {
    private val logg = KotlinLogging.logger {}

    @ExperimentalTime
    @Test
    fun `Parse vedtaksdato to LocalDate`() {
        val mapper = ObjectMapper().registerModule(JavaTimeModule())

        val result: Infotrygd.Response = mapper.readValue("""
            {
                "req": {
                    "id": "c8a92a89-dedd-42b2-a977-1447bcc2a121",
                    "fnr": "07010589518",
                    "tknr": "2103",
                    "saksblokk": "A",
                    "saksnr": "04"
                },
                "vedtaksResult": "IM",
                "vedtaksDate": "2021-03-23",
                "queryTimeElapsedMs": 1.480892
            }
        """.trimIndent())

        println(result)
        println(mapper.writeValueAsString(result))

        assertEquals("c8a92a89-dedd-42b2-a977-1447bcc2a121", result.req.id)
        assertEquals("07010589518", result.req.fnr)
        assertEquals("2103", result.req.tknr)
        assertEquals("A", result.req.saksblokk)
        assertEquals("04", result.req.saksnr)
        assertEquals("IM", result.vedtaksResult)
        assertEquals(LocalDate.of(2021, 3, 23), result.vedtaksDate)
        assertEquals(1.480892, result.queryTimeElapsedMs)
    }

    @ExperimentalTime
    @Test
    fun `Marshal LocalDate with jackson`() {
        val mapper = ObjectMapper().registerModule(JavaTimeModule())

        val expectedDate = LocalDate.now()
        val rawJson = mapper.writeValueAsString(VedtakResultat(
            "abc",
            UUID.randomUUID(),
            "I",
            expectedDate,
            "10127622634",
            "",
            "",
        ))

        val contains = expectedDate.format(DateTimeFormatter.ISO_DATE)

        //println(rawJson)
        //println(contains)

        assertTrue(rawJson.contains(contains))
    }

    @Test
    fun `Test escaping of strings when manually composing json`() {
        val someString = "hm-infotrygd-poller.poll.oldest,application=hm-infotrygd-poller,cluster=prod-gcp,namespace=teamdigihot oldest=\"2021-04-14T08:16:59.185724\" 1618837776003000000"
        var someOtherString = "hello: \"$someString\""
        println("Before: $someOtherString")

        someOtherString = "hello: \"${someString.replace("\"", "\\\"", true)}\""
        println("After: $someOtherString")
        println("Test: \\,\\\\")
    }

    @ExperimentalTime
    @Test
    fun `Test delaying results based on vedtaksDato`() {
        val now = LocalDateTime.of(2021, 11, 1, 6, 5, 32)

        val results = listOf(
            Infotrygd.Response(Infotrygd.Request("", "", "", "", ""), "I", LocalDate.of(2021, 10, 31), "", "", 1.0),
            Infotrygd.Response(Infotrygd.Request("", "", "", "", ""), "A", LocalDate.of(2021, 11, 1), "", "", 1.0),
            Infotrygd.Response(Infotrygd.Request("", "", "", "", ""), "DI", LocalDate.of(2021, 11, 2), "", "", 1.0),
        )

        for (result in results) {
            val waitUntil = result.vedtaksDate!!.plusDays(1).atTime(6, 0, 0)
            if (now.isBefore(waitUntil)) {
                logg.info(
                    "DEBUG: Decision has been made but we will wait until after 06:00 the next day before passing it on to the rapid: vedtaksDato={}, waitUntil={}, now={}",
                    result.vedtaksDate,
                    waitUntil,
                    now,
                )
                continue
            }
            logg.info(
                "Decision made {} < {}",
                waitUntil,
                now
            )
        }
    }

}