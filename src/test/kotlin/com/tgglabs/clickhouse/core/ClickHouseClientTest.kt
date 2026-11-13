package com.tgglabs.clickhouse.core

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/**
 * The tests of the client, run against a local server that plays the part of
 * a ClickHouse server: they check what the client sends and how it reads the
 * answers, without any network.
 */
class ClickHouseClientTest {

    private lateinit var server: HttpServer
    private var status: Int = 200
    private var answer: String = ""
    private var receivedMethod: String = ""
    private var receivedUri: String = ""
    private var receivedBody: String = ""
    private var receivedHeaders: Map<String, List<String>> = emptyMap()

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange: HttpExchange ->
            receivedMethod = exchange.requestMethod
            receivedUri = exchange.requestURI.toString()
            receivedBody = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
            receivedHeaders = exchange.requestHeaders.mapValues { it.value.toList() }
            val bytes = answer.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/plain; charset=UTF-8")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    private fun connection(user: String = "default", password: String = "", database: String = "") =
        ClickHouseConnection(
            host = "127.0.0.1",
            port = server.address.port,
            user = user,
            password = password,
            database = database,
        )

    @Test
    @DisplayName("the query is sent in the body of a POST, and the answer is read")
    fun sendsTheQueryAndReadsTheAnswer() {
        answer = """{"meta":[{"name":"n","type":"UInt8"}],"data":[{"n":1}]}"""

        val result = ClickHouseClient().execute(connection(), "SELECT 1 AS n", ClickHouseFormat.JSON)

        assertEquals("POST", receivedMethod)
        assertEquals("SELECT 1 AS n", receivedBody)
        assertTrue(receivedUri.contains("default_format=JSON"), receivedUri)
        assertEquals(1, result.rowCount)
        assertEquals("1", result.cell(0, 0))
    }

    /**
     * The header names are read without taking the case into account: the
     * JDK server normalizes them ("X-ClickHouse-User" becomes
     * "X-Clickhouse-user").
     */
    private fun header(name: String): List<String>? =
        receivedHeaders.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    @Test
    @DisplayName("the credentials are sent with the official headers of ClickHouse")
    fun sendsTheOfficialHeaders() {
        answer = """{"meta":[],"data":[]}"""

        ClickHouseClient().execute(
            connection(user = "reader", password = "s3cret", database = "shop"),
            "SELECT 1",
            ClickHouseFormat.JSON,
        )

        assertEquals(listOf("reader"), header("X-ClickHouse-User"))
        assertEquals(listOf("s3cret"), header("X-ClickHouse-Key"))
        assertEquals(listOf("shop"), header("X-ClickHouse-Database"))
    }

    @Test
    @DisplayName("no header is sent when there is no user, no password and no database")
    fun sendsNoHeaderWhenThereIsNothing() {
        answer = """{"meta":[],"data":[]}"""

        ClickHouseClient().execute(
            connection(user = "", password = "", database = ""),
            "SELECT 1",
            ClickHouseFormat.JSON,
        )

        assertTrue(header("X-ClickHouse-User") == null)
        assertTrue(header("X-ClickHouse-Key") == null)
        assertTrue(header("X-ClickHouse-Database") == null)
    }

    @Test
    @DisplayName("the format asked for is the one that is written in the URL")
    fun asksForTheFormat() {
        answer = "a\tb\n1\tx\n"

        val result = ClickHouseClient().execute(connection(), "SELECT 1", ClickHouseFormat.TSV_WITH_NAMES)

        assertTrue(receivedUri.contains("default_format=TSVWithNames"), receivedUri)
        assertEquals("a", result.columns[0].name)
    }

    @Test
    @DisplayName("an error of the server is raised with the message of the server")
    fun raisesTheErrorOfTheServer() {
        status = 500
        answer = "Code: 62. DB::Exception: Syntax error: failed at position 1 ('selct'). (SYNTAX_ERROR)"

        val error = assertThrows(ClickHouseException::class.java) {
            ClickHouseClient().execute(connection(), "selct 1", ClickHouseFormat.JSON)
        }
        assertTrue(error.message!!.startsWith("DB::Exception:"), error.message)
        assertTrue(error.message!!.contains("Syntax error"), error.message)
    }

    @Test
    @DisplayName("an error without a body is still an error")
    fun raisesTheErrorWithoutBody() {
        status = 403
        answer = ""

        val error = assertThrows(ClickHouseException::class.java) {
            ClickHouseClient().execute(connection(), "SELECT 1", ClickHouseFormat.JSON)
        }
        assertTrue(error.message!!.contains("403"), error.message)
    }

    @Test
    @DisplayName("a server that cannot be reached is an error, not a crash")
    fun raisesTheUnreachableServer() {
        val unreachable = ClickHouseConnection(host = "127.0.0.1", port = 1, user = "default")

        val error = assertThrows(ClickHouseException::class.java) {
            ClickHouseClient().execute(unreachable, "SELECT 1", ClickHouseFormat.JSON)
        }
        assertTrue(error.message!!.contains("Cannot reach"), error.message)
    }

    @Test
    @DisplayName("a connection that is not usable is refused before any request")
    fun refusesTheUnusableConnection() {
        val error = assertThrows(ClickHouseException::class.java) {
            ClickHouseClient().execute(ClickHouseConnection(host = "", port = 8123), "SELECT 1", ClickHouseFormat.JSON)
        }
        assertTrue(error.message!!.contains("host"), error.message)
    }

    @Test
    @DisplayName("the password never appears in the text of a connection")
    fun masksThePassword() {
        val text = ClickHouseConnection(host = "h", port = 1, user = "u", password = "s3cret").toString()
        assertTrue(!text.contains("s3cret"), text)
        assertTrue(text.contains("***"), text)
    }
}
