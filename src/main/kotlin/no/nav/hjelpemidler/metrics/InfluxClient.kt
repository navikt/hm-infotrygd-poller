package no.nav.hjelpemidler.metrics

import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.write.Point
import no.nav.hjelpemidler.configuration.Configuration
import java.time.Instant

class InfluxClient(
    host: String = Configuration.influx["INFLUX_HOST"]!!,
    port: String = Configuration.influx["INFLUX_PORT"]!!,
    user: String = Configuration.influx["INFLUX_USER"]!!,
    password: String = Configuration.influx["INFLUX_PASSWORD"]!!,
    dbName: String = Configuration.influx["INFLUX_DATABASE_NAME"]!!,
) {

    private val writeApi = InfluxDBClientFactory.createV1(
        "$host:$port",
        user,
        password.toCharArray(),
        dbName,
        null
    ).makeWriteApi()

    fun writeEvent(measurement: String, fields: Map<String, Any>, tags: Map<String, String>) {
        val point = Point(measurement)
            .addTags(DEFAULT_TAGS)
            .addTags(tags)
            .addFields(fields)
            .time(Instant.now().toEpochMilli(), WritePrecision.MS)
        writeApi.writePoint(point)
    }

    private val DEFAULT_TAGS: Map<String, String> = mapOf(
        "application" to (Configuration.application["NAIS_APP_NAME"] ?: "hm-infotrygd-poller"),
        "cluster" to (Configuration.application["NAIS_CLUSTER_NAME"] ?: "dev-gcp"),
        "namespace" to (Configuration.application["NAIS_NAMESPACE"] ?: "teamdigihot")
    )
}
