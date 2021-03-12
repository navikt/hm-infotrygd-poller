package no.nav.hjelpemidler.configuration

import com.natpryce.konfig.*

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

            "application.profile" to "prod",
            "SENSU_URL" to "https://digihot-proxy.prod-fss-pub.nais.io/sensu",
        )
    )

    private val devProperties = ConfigurationMap(
        mapOf(
            "kafka.aiven.topic" to "teamdigihot.hm-soknadsbehandling-v1",
            "kafka.reset.policy" to "latest",
            "kafka.client.id" to "hm-infotrygd-poller-dev",

            "application.profile" to "dev",
            "SENSU_URL" to "https://digihot-proxy.dev-fss-pub.nais.io/sensu",
        )
    )

    private val localProperties = ConfigurationMap(
        mapOf(
            "HTTP_PORT" to "8085",

            "kafka.reset.policy" to "earliest",
            "application.httpPort" to "8082",
            "application.profile" to "LOCAL",
            "KAFKA_TRUSTSTORE_PATH" to "",
            "KAFKA_CREDSTORE_PASSWORD" to "",
            "KAFKA_KEYSTORE_PATH" to "",
            "kafka.brokers" to "host.docker.internal:9092",
            "kafka.client.id" to "hm-infotrygd-poller-local",
            "kafka.aiven.topic" to "teamdigihot.hm-soknadsbehandling-v1",

            "application.profile" to "local",
            "SENSU_URL" to "https://test",

            "HM_INFOTRYGDKPOLLER_SRVUSER" to "abc",
            "HM_INFOTRYGDPOLLER_SRVPWD" to "abc",
        )
    )

    val rapidConfig: Map<String, String> = mapOf(
        "RAPID_KAFKA_CLUSTER" to "gcp",
        "RAPID_APP_NAME" to "hm-infotrygd-poller",
        "KAFKA_BOOTSTRAP_SERVERS" to config()[Key("kafka.brokers", stringType)],
        "KAFKA_CONSUMER_GROUP_ID" to "hm-infotrygd-poller-v1",
        "KAFKA_RAPID_TOPIC" to config()[Key("kafka.aiven.topic", stringType)],
        "KAFKA_RESET_POLICY" to config()[Key("kafka.reset.policy", stringType)],
        "KAFKA_TRUSTSTORE_PATH" to config()[Key("KAFKA_TRUSTSTORE_PATH", stringType)],
        "KAFKA_TRUSTSTORE_PASSWORD" to config()[Key("KAFKA_CREDSTORE_PASSWORD", stringType)],
        "KAFKA_KEYSTORE_PATH" to config()[Key("KAFKA_KEYSTORE_PATH", stringType)],
        "KAFKA_KEYSTORE_PASSWORD" to config()[Key("KAFKA_CREDSTORE_PASSWORD", stringType)],
        "HTTP_PORT" to "8080",
        "KAFKA_CLIENT_ID" to config()[Key("kafka.client.id", stringType)],
    ) + System.getenv().filter { it.key.startsWith("NAIS_") }

    val oracleDatabaseConfig: Map<String, String> = mapOf(
        "HM_INFOTRYGDKPOLLER_SRVUSER" to config()[Key("HM_INFOTRYGDKPOLLER_SRVUSER", stringType)],
        "HM_INFOTRYGDPOLLER_SRVPWD" to config()[Key("HM_INFOTRYGDPOLLER_SRVPWD", stringType)],
        "DATABASE_URL" to "jdbc:oracle:thin:@a01dbfl033.adeo.no:1521/infotrygd_hjq",
    )

    val application: Map<String, String> = mapOf(
        "APP_PROFILE" to config()[Key("application.profile", stringType)],
        "SENSU_URL" to config()[Key("SENSU_URL", stringType)],
    ) + System.getenv().filter { it.key.startsWith("NAIS_") }

}