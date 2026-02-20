package no.nav.hjelpemidler.service.soknadsbehandlingdb

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.service.azure.AzureClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.util.UUID

private val logg = KotlinLogging.logger {}

private val azClient = AzureClient(
    Configuration.azureAD["AZURE_TENANT_BASEURL"]!! + "/" + Configuration.azureAD["AZURE_APP_TENANT_ID"]!!,
    Configuration.azureAD["AZURE_APP_CLIENT_ID"]!!,
    Configuration.azureAD["AZURE_APP_CLIENT_SECRET"]!!,
)

private val mapper: JsonMapper = jacksonMapperBuilder()
    .addModules(JavaTimeModule())
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .build()

class SoknadsbehandlingDb {
    fun hentInfotrygdPker(fraOgMedDato: LocalDate, tilOgMedDato: LocalDate): List<InfotrygdPrimaryKey> {
        val token = azClient.getToken(Configuration.azureAD["AZURE_AD_SCOPE_SOKNADSBEHANDLING_DB"]!!)
        val url = Configuration.infotrygdProxy["SOKNADSBEHANDLING_DB_URL"]!! + "/api/infotrygd/digitale-vedtak-nokler"

        val body = mapper.writeValueAsString(
            mapOf(
                "fraOgMedDato" to fraOgMedDato,
                "tilOgMedDato" to tilOgMedDato,
            ),
        )

        if (Configuration.application["APP_PROFILE"] != "prod") {
            logg.debug { "Making request with url: $url and json: $body. Token: [MASKED]" }
        }

        // Execute request towards graphql API server
        val client: HttpClient = HttpClient.newHttpClient()
        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("Accept", "application/json")
            .header("X-Correlation-ID", UUID.randomUUID().toString())
            .header("Authorization", "Bearer ${token.accessToken}")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString())

        // Check response codes
        if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
            val statusCode = httpResponse.statusCode()
            throw Exception(
                """
                    invalid response status code when requesting infotrygd pker from sdb: statusCode=$statusCode 
                    responseBody: ${httpResponse.body()}
                """.trimIndent(),
            )
        }

        val jsonResponse: String = httpResponse.body()
        if (Configuration.application["APP_PROFILE"] != "prod") {
            logg.debug { "Received response pre-parsing: $jsonResponse" }
        }

        return mapper.readValue(jsonResponse)
    }

    data class InfotrygdPrimaryKey(
        val fnr: String,
        val trygdekontornummer: String,
        val saksblokk: String,
        val saksnummer: String,
    )
}
