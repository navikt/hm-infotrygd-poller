package no.nav.hjelpemidler.service.infotrygdproxy

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

internal class InfotrygdServiceTest {

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

}