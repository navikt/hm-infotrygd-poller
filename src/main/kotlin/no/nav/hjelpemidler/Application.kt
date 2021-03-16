package no.nav.hjelpemidler

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.helse.rapids_rivers.KafkaConfig
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.hjelpemidler.metrics.SensuMetrics
import no.nav.hjelpemidler.rivers.LoggRiver
import no.nav.hjelpemidler.service.azure.AzureClient
import oracle.jdbc.OracleConnection
import oracle.jdbc.pool.OracleDataSource
import java.net.InetAddress
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
fun main() {
    /*var rapidApp: RapidsConnection? = null
    rapidApp = RapidApplication.Builder(
        RapidApplication.RapidApplicationConfig(
            Configuration.rapidConfig["RAPID_APP_NAME"],
            InetAddress.getLocalHost().hostName,
            Configuration.rapidConfig["KAFKA_RAPID_TOPIC"]!!,
            emptyList(),
            KafkaConfig(
                Configuration.rapidConfig["KAFKA_BOOTSTRAP_SERVERS"]!!,
                Configuration.rapidConfig["KAFKA_CONSUMER_GROUP_ID"]!!,
                Configuration.rapidConfig["KAFKA_CLIENT_ID"]!!,
                null,
                null,
                Configuration.rapidConfig["KAFKA_TRUSTSTORE_PATH"]!!,
                Configuration.rapidConfig["KAFKA_TRUSTSTORE_PASSWORD"]!!,
                "jks",
                "PKCS12",
                Configuration.rapidConfig["KAFKA_KEYSTORE_PATH"]!!,
                Configuration.rapidConfig["KAFKA_KEYSTORE_PASSWORD"]!!,
                Configuration.rapidConfig["KAFKA_RESET_POLICY"]!!,
                false,
                null,
                null,
            ),
            8080,
        )
    ).build().apply {
        LoggRiver(this)
    }
     */

    val elapsed: Duration = measureTime {

        val req = Request(
            "2103",
            "07010589518",
            "A",
            "04",
        )

        val json: String = mapper.writeValueAsString(req)

        if (azTokenTimeout == null || azTokenTimeout?.isBefore(LocalDateTime.now()) == true) {
            val token = azClient.getToken(Configuration.azureAD["AZURE_AD_SCOPE"]!!)
            azToken = token.accessToken
            azTokenTimeout = LocalDateTime.now()
                .plusSeconds(token.expiresIn - 60 /* 60s leeway => renew 60s before token expiration */)
        }

        val authToken = azToken!!
        val url = Configuration.infotrygdProxy["INFOTRYGDPROXY_URL"]!! + "/result"
        logg.info("Making proxy request with url: $url and json: $json. Token: $authToken")

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
            throw Exception("invalid response status code when requesting infotrygd data: req=$req statusCode=$statusCode")
        }

        // Use the jackson object mapper to turn the returned raw json into a response object hierarchy
        val response: Response = mapper.readValue(httpResponse.body().toString())

        /*if ((response.errors != null && response.errors.isNotEmpty()) || response.data?.journalpost == null) {
            SensuMetrics().safHentingFeilet()
            val responseWasEmpty = response.data?.journalpost == null
            val jsonErr = mapper.writeValueAsString(response.errors)
            throw Exception("unable to fetch SAF details about journaling event: responseWasEmpty=$responseWasEmpty errors=$jsonErr")
        }*/

        logg.info("Response received from infotrygd: \"${response.result}\"")

    }

    logg.info("Request time elapsed: ${elapsed.inMilliseconds}")

    Thread.sleep(1000*60*60*24)

    /*
    // Run our rapid and rivers implementation facing hm-rapid
    logg.info("Starting Rapid & Rivers app towards hm-rapid")
    rapidApp.start()
    logg.info("Application ending.")
     */
}


data class Request(
    val tknr: String,
    val fnr: String,
    val saksblokk: String,
    val saksnr: String,
)

data class Response(
    val result: String,
)
