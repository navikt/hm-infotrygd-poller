package no.nav.hjelpemidler.service.azure

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

private val logg = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

class AzureClient(private val tenantUrl: String, private val clientId: String, private val clientSecret: String) {

    companion object {
        private val objectMapper = ObjectMapper()
    }

    private val tokenCache: MutableMap<String, Token> = mutableMapOf()

    fun getToken(scope: String) =
        tokenCache[scope]
            ?.takeUnless(Token::isExpired)
            ?: fetchToken(scope)
                .also { token ->
                    tokenCache[scope] = token
                }

    private fun fetchToken(scope: String): Token {
        val (responseCode, responseBody) = with(URL("$tenantUrl/oauth2/v2.0/token").openConnection() as HttpURLConnection) {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 10000
            doOutput = true
            outputStream.use {
                it.bufferedWriter().apply {
                    write("client_id=$clientId&client_secret=$clientSecret&scope=$scope&grant_type=client_credentials")
                    flush()
                }
            }

            val stream: InputStream? = if (responseCode < 300) this.inputStream else this.errorStream
            responseCode to stream?.bufferedReader()?.readText()
        }

        sikkerlogg.info { "Svar fra Azure AD, responseCode: $responseCode, responseBody: $responseBody" }

        if (responseBody == null) {
            throw RuntimeException("Ukjent feil fra Azure AD (responseCode: $responseCode), responseBody er null")
        }

        val jsonNode = objectMapper.readTree(responseBody)

        if (jsonNode.has("error")) {
            logg.error { "${jsonNode["error_description"].textValue()}: $jsonNode" }
            error("error from the azure token endpoint: ${jsonNode["error_description"].textValue()}")
        } else if (responseCode >= 300) {
            error("Unknown error (responseCode: $responseCode) from Azure AD")
        }

        return Token(
            tokenType = jsonNode["token_type"].textValue(),
            expiresIn = jsonNode["expires_in"].longValue(),
            accessToken = jsonNode["access_token"].textValue(),
        )
    }

    data class Token(val tokenType: String, val expiresIn: Long, val accessToken: String) {
        companion object {
            private const val LEEWAY_SECONDS = 60
        }

        private val expiresOn = Instant.now().plusSeconds(expiresIn - LEEWAY_SECONDS)

        fun isExpired(): Boolean = expiresOn.isBefore(Instant.now())
    }
}
