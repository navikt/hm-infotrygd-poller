package no.nav.hjelpemidler.service.infotrygdproxy

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
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
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.measureTime

private val logg = KotlinLogging.logger {}

private val azClient = AzureClient(
    Configuration.azureAD["AZURE_TENANT_BASEURL"]!! + "/" + Configuration.azureAD["AZURE_APP_TENANT_ID"]!!,
    Configuration.azureAD["AZURE_APP_CLIENT_ID"]!!,
    Configuration.azureAD["AZURE_APP_CLIENT_SECRET"]!!,
)
private var azTokenTimeout: LocalDateTime? = null
private var azToken: String? = null

private val mapper: JsonMapper = jacksonMapperBuilder()
    .addModules(JavaTimeModule())
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .build()

class Infotrygd {
    fun batchQueryVedtakResultat(requests: List<Request>): List<Response> {
        if (Configuration.application["APP_PROFILE"] != "prod") {
            logg.debug { "batchQueryVedtakResultat: requests: $requests" }
        }

        var results: List<Response>? = null
        val elapsed: Duration = measureTime {
            val json: String = mapper.writeValueAsString(requests.toTypedArray())

            if (azTokenTimeout == null || azTokenTimeout?.isBefore(LocalDateTime.now()) == true) {
                val token = azClient.getToken(Configuration.azureAD["AZURE_AD_SCOPE"]!!)
                azToken = token.accessToken
                azTokenTimeout = LocalDateTime.now()
                    .plusSeconds(token.expiresIn - 60 /* 60s leeway => renew 60s before token expiration */)
            }

            val authToken = azToken!!
            val url = Configuration.infotrygdProxy["INFOTRYGDPROXY_URL"]!! + "/vedtak-resultat"

            if (Configuration.application["APP_PROFILE"] != "prod") {
                logg.debug { "Making proxy request with url: $url and json: $json. Token: [MASKED]" }
            }

            // Execute request towards graphql API server
            val client: HttpClient = HttpClient.newHttpClient()
            val request: HttpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("Authorization", "Bearer $authToken")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build()
            val httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString())

            // Check response codes
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                val statusCode = httpResponse.statusCode()
                throw Exception(
                    """
                        invalid response status code when requesting infotrygd data: req=$requests statusCode=$statusCode 
                        responseBody: ${httpResponse.body()}
                    """.trimIndent(),
                )
            }

            val jsonResponse: String = httpResponse.body()
            if (Configuration.application["APP_PROFILE"] != "prod") {
                logg.debug { "Received response pre-parsing: $jsonResponse" }
            }

            results = mapper.readValue(jsonResponse)
        }

        if (Configuration.application["APP_PROFILE"] != "prod") {
            logg.debug {
                "Response received from infotrygd: $results. Total request time elapsed: ${
                    elapsed.toDouble(
                        DurationUnit.MILLISECONDS,
                    )
                }"
            }
        }

        return results!!.toList()
    }

    data class Request(
        @JsonProperty("id")
        val id: String,

        @JsonProperty("fnr")
        val fnr: String,

        @JsonProperty("tknr")
        val tknr: String,

        @JsonProperty("saksblokk")
        val saksblokk: String,

        @JsonProperty("saksnr")
        val saksnr: String,
    )

    data class Response(
        @JsonProperty("req")
        val req: Request,

        @JsonProperty("vedtaksResult")
        val vedtaksResult: String? = null, // null initialization required for Klaxon deserialization if not mentioned in response (due to null)

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @JsonProperty("vedtaksDate")
        val vedtaksDate: LocalDate? = null, // null initialization required for Klaxon deserialization if not mentioned in response (due to null)

        @JsonProperty("soknadsType")
        val soknadsType: String? = null, // null initialization required for Klaxon deserialization if not mentioned in response (due to null)

        @JsonProperty("error")
        val error: String? = null, // null initialization required for Klaxon deserialization if not mentioned in response (due to null)

        @JsonProperty("queryTimeElapsedMs")
        @JsonDeserialize
        val queryTimeElapsedMs: Double,
    )
}
