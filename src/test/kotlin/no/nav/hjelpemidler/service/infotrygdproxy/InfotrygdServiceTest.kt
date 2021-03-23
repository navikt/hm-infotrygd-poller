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
        val mapper = ObjectMapper()
        mapper.registerModule(JavaTimeModule())

        val result: Infotrygd.Response = mapper.readValue("""
            {
                "req": {
                    "id": "c8a92a89-dedd-42b2-a977-1447bcc2a121",
                    "fnr": "07010589518",
                    "tknr": "2103",
                    "saksblokk": "A",
                    "saksnr": "04"
                },
                "result": "IM",
                "vedtaksDate": "2012-04-04",
                "queryTimeElapsedMs": 1.480892
            }
        """.trimIndent())

        assertEquals("c8a92a89-dedd-42b2-a977-1447bcc2a121", result.req.id)
        assertEquals("07010589518", result.req.fnr)
        assertEquals("2103", result.req.tknr)
        assertEquals("A", result.req.saksblokk)
        assertEquals("04", result.req.saksnr)
        assertEquals("IM", result.result)
        assertEquals(LocalDate.of(2012, 4, 4), result.vedtaksDate)
        assertEquals(1.480892, result.queryTimeElapsedMs)
    }

}