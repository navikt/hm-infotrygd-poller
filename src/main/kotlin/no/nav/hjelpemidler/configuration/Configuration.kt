package no.nav.hjelpemidler.configuration

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType

object Configuration {
    private val configuration by lazy {
        when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
            "dev-gcp" -> ConfigurationProperties.systemProperties() overriding EnvironmentVariables overriding devProperties
            "prod-gcp" -> ConfigurationProperties.systemProperties() overriding EnvironmentVariables overriding prodProperties
            else -> ConfigurationProperties.systemProperties() overriding EnvironmentVariables overriding localProperties
        }
    }

    private val prodProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "prod",

            "AZURE_TENANT_BASEURL" to "https://login.microsoftonline.com",
            "AZURE_AD_SCOPE" to "api://prod-fss.teamdigihot.hm-infotrygd-proxy/.default",

            "INFOTRYGDPROXY_URL" to "https://hm-infotrygd-proxy.prod-fss-pub.nais.io",
        ),
    )

    private val devProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "dev",

            "AZURE_TENANT_BASEURL" to "https://login.microsoftonline.com",
            "AZURE_AD_SCOPE" to "api://dev-fss.teamdigihot.hm-infotrygd-proxy/.default",

            "INFOTRYGDPROXY_URL" to "https://hm-infotrygd-proxy.dev-fss-pub.nais.io",
        ),
    )

    private val localProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "local",

            "AZURE_TENANT_BASEURL" to "http://azure/token",
            "AZURE_APP_TENANT_ID" to "local",
            "AZURE_APP_CLIENT_ID" to "local",
            "AZURE_APP_CLIENT_SECRET" to "local",
            "AZURE_AD_SCOPE" to "local",

            "INFOTRYGDPROXY_URL" to "http://infotrygd/proxy",

            "DB_HOST" to "localhost",
            "DB_PORT" to "5432",
            "DB_DATABASE" to "infotrygd_poller",
            "DB_USERNAME" to "infotrygd_poller",
            "DB_PASSWORD" to "infotrygd_poller",

            "INFLUX_HOST" to "localhost",
            "INFLUX_PORT" to "8086",
            "INFLUX_DATABASE_NAME" to "infotrygd_poller",
            "INFLUX_USER" to "infotrygd_poller",
            "INFLUX_PASSWORD" to "infotrygd_poller",
        ),
    )

    val azureAD: Map<String, String> = mapOf(
        "AZURE_TENANT_BASEURL" to configuration[Key("AZURE_TENANT_BASEURL", stringType)],
        "AZURE_APP_TENANT_ID" to configuration[Key("AZURE_APP_TENANT_ID", stringType)],
        "AZURE_APP_CLIENT_ID" to configuration[Key("AZURE_APP_CLIENT_ID", stringType)],
        "AZURE_APP_CLIENT_SECRET" to configuration[Key("AZURE_APP_CLIENT_SECRET", stringType)],
        "AZURE_AD_SCOPE" to configuration[Key("AZURE_AD_SCOPE", stringType)],
    )

    val infotrygdProxy: Map<String, String> = mapOf(
        "INFOTRYGDPROXY_URL" to configuration[Key("INFOTRYGDPROXY_URL", stringType)],
    )

    val db: Map<String, String> = mapOf(
        "DB_HOST" to configuration[Key("DB_HOST", stringType)],
        "DB_PORT" to configuration[Key("DB_PORT", stringType)],
        "DB_DATABASE" to configuration[Key("DB_DATABASE", stringType)],
        "DB_USERNAME" to configuration[Key("DB_USERNAME", stringType)],
        "DB_PASSWORD" to configuration[Key("DB_PASSWORD", stringType)],
    )

    val influx: Map<String, String> = mapOf(
        "INFLUX_HOST" to configuration[Key("INFLUX_HOST", stringType)],
        "INFLUX_PORT" to configuration[Key("INFLUX_PORT", stringType)],
        "INFLUX_DATABASE_NAME" to configuration[Key("INFLUX_DATABASE_NAME", stringType)],
        "INFLUX_USER" to configuration[Key("INFLUX_USER", stringType)],
        "INFLUX_PASSWORD" to configuration[Key("INFLUX_PASSWORD", stringType)],
    )

    val application: Map<String, String> = mapOf(
        "APP_PROFILE" to configuration[Key("application.profile", stringType)],
    ) + System.getenv().filter { it.key.startsWith("NAIS_") }
}
