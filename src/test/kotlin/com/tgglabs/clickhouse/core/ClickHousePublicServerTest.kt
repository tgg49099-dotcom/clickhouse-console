package com.tgglabs.clickhouse.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The test of the client against the PUBLIC playground of ClickHouse
 * (https://play.clickhouse.com), the official public instance.
 *
 * It is NOT run by the normal build: it needs the network. It is run when
 * the variable CLICKHOUSE_PUBLIC is set to 1:
 *
 *     CLICKHOUSE_PUBLIC=1 ./gradlew test --tests '*ClickHousePublicServerTest*'
 *
 * The playground is run by ClickHouse Inc. and answers without any password
 * when no user is sent.
 */
class ClickHousePublicServerTest {

    private fun playground(): ClickHouseConnection =
        ClickHouseConnection(host = "play.clickhouse.com", port = 443, user = "", database = "default", secure = true)

    @Test
    @DisplayName("a query runs on the public playground of ClickHouse")
    fun runsAQueryOnThePublicPlayground() {
        Assumptions.assumeTrue(
            System.getenv("CLICKHOUSE_PUBLIC") == "1",
            "the test needs the network: run it with CLICKHOUSE_PUBLIC=1",
        )

        val result = ClickHouseClient().execute(
            playground(),
            "SELECT number AS n, 'ligne' AS s FROM numbers(3)",
            ClickHouseFormat.JSON,
        )

        assertEquals(2, result.columnCount)
        assertEquals("n", result.columns[0].name)
        assertEquals("UInt64", result.columns[0].type)
        assertEquals(3, result.rowCount)
        assertEquals("0", result.cell(0, 0))
        assertEquals("ligne", result.cell(0, 1))
        assertEquals("2", result.cell(2, 0))
    }

    @Test
    @DisplayName("the TSV answer of the public playground is read too")
    fun readsTheTsvAnswerOfThePublicPlayground() {
        Assumptions.assumeTrue(
            System.getenv("CLICKHOUSE_PUBLIC") == "1",
            "the test needs the network: run it with CLICKHOUSE_PUBLIC=1",
        )

        val result = ClickHouseClient().execute(
            playground(),
            "SELECT 1 AS a, 'x' AS b",
            ClickHouseFormat.TSV_WITH_NAMES,
        )

        assertEquals("a", result.columns[0].name)
        assertEquals("b", result.columns[1].name)
        assertEquals("1", result.cell(0, 0))
        assertEquals("x", result.cell(0, 1))
    }

    @Test
    @DisplayName("the public playground answers the version of the server")
    fun readsTheVersionOfThePublicPlayground() {
        Assumptions.assumeTrue(
            System.getenv("CLICKHOUSE_PUBLIC") == "1",
            "the test needs the network: run it with CLICKHOUSE_PUBLIC=1",
        )

        val version = ClickHouseClient().testConnection(playground())
        assertTrue(version.isNotEmpty())
        assertTrue(version.contains('.'), version)
    }

    @Test
    @DisplayName("a wrong query on the public playground gives the message of the server")
    fun readsTheErrorOfThePublicPlayground() {
        Assumptions.assumeTrue(
            System.getenv("CLICKHOUSE_PUBLIC") == "1",
            "the test needs the network: run it with CLICKHOUSE_PUBLIC=1",
        )

        val error = runCatching {
            ClickHouseClient().execute(playground(), "selct 1", ClickHouseFormat.JSON)
        }.exceptionOrNull()

        assertTrue(error is ClickHouseException, "the answer was not an error: $error")
        assertTrue(error!!.message!!.contains("Exception") || error!!.message!!.contains("error"), error.message)
    }
}
