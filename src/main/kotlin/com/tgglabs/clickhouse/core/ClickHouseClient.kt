package com.tgglabs.clickhouse.core

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Thrown when a query could not be run: the server is unreachable, the
 * server answered an error, or the answer could not be read.
 */
class ClickHouseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Talks to a ClickHouse server through its OFFICIAL HTTP interface.
 *
 * The interface is the one of the `clickhouse-client` over HTTP:
 *
 *  * the query is sent in the body of a POST request
 *    (`http://host:port/?default_format=JSON`);
 *  * the credentials are sent with the official headers
 *    `X-ClickHouse-User` and `X-ClickHouse-Key`;
 *  * the default database is sent with the header `X-ClickHouse-Database`;
 *  * the format of the answer is asked for with the parameter
 *    `default_format` (official setting `default_format`).
 *
 * Measured on play.clickhouse.com on 13/11/2026:
 *  * `GET /?default_format=JSON&query=SELECT 1 AS a` answers the JSON
 *    object `{"meta":[{"name":"a","type":"UInt8"}],"data":[{"a":1}],...}`;
 *  * `POST /?default_format=TSVWithNames` with the query in the body
 *    answers the header line `a\tb` then the rows.
 *
 * The client uses `java.net.http.HttpClient` (JDK 11+): the IntelliJ
 * Platform runs on a JBR 21, so it is always there, and no HTTP dependency
 * has to be shipped with the plugin.
 */
class ClickHouseClient(
    private val parser: ClickHouseResponseParser = ClickHouseResponseParser(),
    private val httpClient: HttpClient = defaultHttpClient(),
    private val requestTimeout: Duration = Duration.ofSeconds(30),
) {

    /**
     * Runs a query and returns its result.
     *
     * @throws ClickHouseException when the server cannot be reached or
     *         answers an error.
     */
    fun execute(
        connection: ClickHouseConnection,
        query: String,
        format: ClickHouseFormat = ClickHouseFormat.JSON,
    ): QueryResult {
        val problems = connection.validate()
        if (problems.isNotEmpty()) {
            throw ClickHouseException(problems.joinToString(" "))
        }

        val request = buildRequest(connection, query, format)
        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ClickHouseException("The query was interrupted.", e)
        } catch (e: IOException) {
            throw ClickHouseException(
                "Cannot reach ${connection.host}:${connection.port} (${e.message ?: e.javaClass.simpleName}).",
                e,
            )
        }

        if (response.statusCode() !in 200..299) {
            val message = parser.extractErrorMessage(response.body())
            throw ClickHouseException(message.ifBlank { "The server answered HTTP ${response.statusCode()}." })
        }

        return parser.parse(response.body(), format)
    }

    /**
     * Checks that the server answers, and returns the version it announces.
     */
    fun testConnection(connection: ClickHouseConnection): String {
        val result = execute(connection, "SELECT version()", ClickHouseFormat.TSV)
        return result.cell(0, 0).ifBlank { "the server answered" }
    }

    // -----------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------

    private fun buildRequest(
        connection: ClickHouseConnection,
        query: String,
        format: ClickHouseFormat,
    ): HttpRequest {
        val uri = URI.create("${connection.url}?default_format=${format.formatName}")
        val builder = HttpRequest.newBuilder(uri)
            .timeout(requestTimeout)
            .header("Content-Type", "text/plain; charset=UTF-8")
            .header("Accept", "*/*")
            .header("User-Agent", USER_AGENT)
            .POST(HttpRequest.BodyPublishers.ofString(query, StandardCharsets.UTF_8))

        if (connection.user.isNotBlank()) {
            builder.header("X-ClickHouse-User", connection.user)
        }
        if (connection.password.isNotEmpty()) {
            builder.header("X-ClickHouse-Key", connection.password)
        }
        if (connection.database.isNotBlank()) {
            builder.header("X-ClickHouse-Database", connection.database)
        }
        return builder.build()
    }

    companion object {
        const val USER_AGENT: String = "ClickHouseConsole/0.0.1 (JetBrains plugin)"

        fun defaultHttpClient(): HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }
}
