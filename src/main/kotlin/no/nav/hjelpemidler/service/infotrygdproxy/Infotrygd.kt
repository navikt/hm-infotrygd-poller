package no.nav.hjelpemidler.service.infotrygdproxy

import com.beust.klaxon.Klaxon
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import mu.KotlinLogging
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.service.azure.AzureClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDateTime
import java.util.*
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

private val logg = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

private val mapper = jacksonObjectMapper()

private val azClient = AzureClient(Configuration.azureAD["AZURE_TENANT_BASEURL"]!! + "/" + Configuration.azureAD["AZURE_APP_TENANT_ID"]!!, Configuration.azureAD["AZURE_APP_CLIENT_ID"]!!, Configuration.azureAD["AZURE_APP_CLIENT_SECRET"]!!)
private var azTokenTimeout: LocalDateTime? = null
private var azToken: String? = null

@ExperimentalTime
class Infotrygd {

    fun batchQueryVedtakResultat(reqs: List<Request>): List<Response> {
        logg.info("DEBUG: batchQueryVedtakResultat: reqs=$reqs")

        var results: List<Response>? = null
        val elapsed: Duration = measureTime {
            val json: String = mapper.writeValueAsString(reqs.toTypedArray())

            if (azTokenTimeout == null || azTokenTimeout?.isBefore(LocalDateTime.now()) == true) {
                val token = azClient.getToken(Configuration.azureAD["AZURE_AD_SCOPE"]!!)
                azToken = token.accessToken
                azTokenTimeout = LocalDateTime.now()
                    .plusSeconds(token.expiresIn - 60 /* 60s leeway => renew 60s before token expiration */)
            }

            val authToken = azToken!!
            val url = Configuration.infotrygdProxy["INFOTRYGDPROXY_URL"]!! + "/vedtak-resultat"

            logg.info("DEBUG: Making proxy request with url: $url and json: $json. Token: [MASKED]")

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
                throw Exception("invalid response status code when requesting infotrygd data: req=$reqs statusCode=$statusCode responseBody: ${httpResponse.body()}")
            }

            val jsonResp = httpResponse.body().toString()
            logg.info("DEBUG: received response pre-parsing: $jsonResp")
            // val resultsInner: List<Response> = Klaxon().parseArray(jsonResp)!!
            results = Klaxon().parseArray(jsonResp)
        }

        logg.info("DEBUG: Response received from infotrygd: $results. Total request time elapsed: ${elapsed.inMilliseconds}")

        return results!!.toList()
    }

    data class Request(
        val id: String,
        val tknr: String,
        val fnr: String,
        val saksblokk: String,
        val saksnr: String,
    )

    data class Response(
        val req: Request,
        val result: String?,
        val error: String?,
        val queryTimeElapsedMs: Double,
    )
}

/*
[
    {
        \"queryTimeElapsedMs\" : 5.734309,
        \"req\" :
            {\"fnr\" : \"07010589518\", \"id\" : \"\", \"saksblokk\" : \"A\", \"saksnr\" : \"04\", \"tknr\" : \"2103\"},
        \"result\" : \"IM\"
    },
    {\"queryTimeElapsedMs\" : 1.192449, \"req\" : {\"fnr\" : \"07010589518\", \"id\" : \"\", \"saksblokk\" : \"A\", \"saksnr\" : \"04\", \"tknr\" : \"2103\"}, \"result\" : \"IM\"}
]
Exception in thread "Thread-1" java.lang.ClassCastException: class com.beust.klaxon.JsonArray cannot be cast to class com.beust.klaxon.JsonObject (com.beust.klaxon.JsonArray and com.beust.klaxon.JsonObject are in unnamed module of loader 'app')
 */