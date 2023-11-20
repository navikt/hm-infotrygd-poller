package no.nav.hjelpemidler.configuration

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType

internal object Configuration {

    private fun config() = when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
        "dev-gcp" -> ConfigurationProperties.systemProperties() overriding EnvironmentVariables overriding devProperties
        "prod-gcp" -> ConfigurationProperties.systemProperties() overriding EnvironmentVariables overriding prodProperties
        else -> {
            ConfigurationProperties.systemProperties() overriding EnvironmentVariables overriding localProperties
        }
    }

    private val prodProperties = ConfigurationMap(
        mapOf(
            "kafka.aiven.topic" to "teamdigihot.hm-soknadsbehandling-v1",
            "kafka.reset.policy" to "latest",
            "kafka.client.id" to "hm-infotrygd-poller-prod",
            "kafka.consumer.id" to "hm-infotrygd-poller-v1",

            "application.profile" to "prod",

            "AZURE_TENANT_BASEURL" to "https://login.microsoftonline.com",
            "AZURE_AD_SCOPE" to "api://prod-fss.teamdigihot.hm-infotrygd-proxy/.default",

            "INFOTRYGDPROXY_URL" to "https://hm-infotrygd-proxy.prod-fss-pub.nais.io",
        )
    )

    private val devProperties = ConfigurationMap(
        mapOf(
            "kafka.aiven.topic" to "teamdigihot.hm-soknadsbehandling-v1",
            "kafka.reset.policy" to "latest",
            "kafka.client.id" to "hm-infotrygd-poller-dev",
            "kafka.consumer.id" to "hm-infotrygd-poller-v2",

            "application.profile" to "dev",

            "AZURE_TENANT_BASEURL" to "https://login.microsoftonline.com",
            "AZURE_AD_SCOPE" to "api://dev-fss.teamdigihot.hm-infotrygd-proxy/.default",

            "INFOTRYGDPROXY_URL" to "https://hm-infotrygd-proxy.dev-fss-pub.nais.io",
        )
    )

    private val localProperties = ConfigurationMap(
        mapOf(
            "HTTP_PORT" to "8085",

            "kafka.reset.policy" to "earliest",
            "application.httpPort" to "8082",
            "KAFKA_TRUSTSTORE_PATH" to "",
            "KAFKA_CREDSTORE_PASSWORD" to "",
            "KAFKA_KEYSTORE_PATH" to "",
            "kafka.brokers" to "host.docker.internal:9092",
            "kafka.client.id" to "hm-infotrygd-poller-local",
            "kafka.aiven.topic" to "teamdigihot.hm-soknadsbehandling-v1",
            "kafka.consumer.id" to "hm-infotrygd-poller-v1",

            "application.profile" to "local",

            "AZURE_TENANT_BASEURL" to "http://localhost:9099",
            "AZURE_APP_TENANT_ID" to "123",
            "AZURE_APP_CLIENT_ID" to "123",
            "AZURE_APP_CLIENT_SECRET" to "dummy",
            "AZURE_AD_SCOPE" to "123",

            "INFOTRYGDPROXY_URL" to "http://localhost:9092",

            "DB_HOST" to "abc",
            "DB_PORT" to "abc",
            "DB_DATABASE" to "abc",
            "DB_USERNAME" to "abc",
            "DB_PASSWORD" to "abc",

            "INFLUX_HOST" to "abc",
            "INFLUX_PORT" to "123",
            "INFLUX_DATABASE_NAME" to "abc",
            "INFLUX_USER" to "abc",
            "INFLUX_PASSWORD" to "abc",
        )
    )

    val rapidConfig: Map<String, String> = mapOf(
        "RAPID_KAFKA_CLUSTER" to "gcp",
        "RAPID_APP_NAME" to "hm-infotrygd-poller",
        "KAFKA_BROKERS" to config()[Key("kafka.brokers", stringType)],
        "KAFKA_CONSUMER_GROUP_ID" to config()[Key("kafka.consumer.id", stringType)],
        "KAFKA_RAPID_TOPIC" to config()[Key("kafka.aiven.topic", stringType)],
        "KAFKA_RESET_POLICY" to config()[Key("kafka.reset.policy", stringType)],
        "KAFKA_TRUSTSTORE_PATH" to config()[Key("KAFKA_TRUSTSTORE_PATH", stringType)],
        "KAFKA_KEYSTORE_PATH" to config()[Key("KAFKA_KEYSTORE_PATH", stringType)],
        "KAFKA_CREDSTORE_PASSWORD" to config()[Key("KAFKA_CREDSTORE_PASSWORD", stringType)],
        "HTTP_PORT" to "8080",
        "KAFKA_CLIENT_ID" to config()[Key("kafka.client.id", stringType)],
    ) + System.getenv().filter { it.key.startsWith("NAIS_") }

    val azureAD: Map<String, String> = mapOf(
        "AZURE_TENANT_BASEURL" to config()[Key("AZURE_TENANT_BASEURL", stringType)],
        "AZURE_APP_TENANT_ID" to config()[Key("AZURE_APP_TENANT_ID", stringType)],
        "AZURE_APP_CLIENT_ID" to config()[Key("AZURE_APP_CLIENT_ID", stringType)],
        "AZURE_APP_CLIENT_SECRET" to config()[Key("AZURE_APP_CLIENT_SECRET", stringType)],
        "AZURE_AD_SCOPE" to config()[Key("AZURE_AD_SCOPE", stringType)],
    )

    val infotrygdProxy: Map<String, String> = mapOf(
        "INFOTRYGDPROXY_URL" to config()[Key("INFOTRYGDPROXY_URL", stringType)],
    )

    val db: Map<String, String> = mapOf(
        "DB_HOST" to config()[Key("DB_HOST", stringType)],
        "DB_PORT" to config()[Key("DB_PORT", stringType)],
        "DB_DATABASE" to config()[Key("DB_DATABASE", stringType)],
        "DB_USERNAME" to config()[Key("DB_USERNAME", stringType)],
        "DB_PASSWORD" to config()[Key("DB_PASSWORD", stringType)],
    )

    val influx: Map<String, String> = mapOf(
        "INFLUX_HOST" to config()[Key("INFLUX_HOST", stringType)],
        "INFLUX_PORT" to config()[Key("INFLUX_PORT", stringType)],
        "INFLUX_DATABASE_NAME" to config()[Key("INFLUX_DATABASE_NAME", stringType)],
        "INFLUX_USER" to config()[Key("INFLUX_USER", stringType)],
        "INFLUX_PASSWORD" to config()[Key("INFLUX_PASSWORD", stringType)],
    )

    val application: Map<String, String> = mapOf(
        "APP_PROFILE" to config()[Key("application.profile", stringType)],
    ) + System.getenv().filter { it.key.startsWith("NAIS_") }
}
