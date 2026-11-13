package com.tgglabs.clickhouse.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The tests of the parser that reads the answers of the HTTP interface of
 * ClickHouse.
 *
 * The JSON answers used here are the ones the real server sends (measured on
 * play.clickhouse.com on 13/11/2026).
 */
class ClickHouseResponseParserTest {

    private val parser = ClickHouseResponseParser()

    // -----------------------------------------------------------------
    // JSON
    // -----------------------------------------------------------------

    @Test
    @DisplayName("the JSON answer of the server is read: columns, types and rows")
    fun readsTheJsonAnswer() {
        val body = """
            {
            	"meta":
            	[
            		{
            			"name": "n",
            			"type": "UInt64"
            		},
            		{
            			"name": "s",
            			"type": "String"
            		}
            	],

            	"data":
            	[
            		{
            			"n": 0,
            			"s": "zero"
            		},
            		{
            			"n": 1,
            			"s": "one"
            		}
            	],

            	"rows": 2
            }
        """.trimIndent()

        val result = parser.parse(body, ClickHouseFormat.JSON)

        assertEquals(2, result.columnCount)
        assertEquals(2, result.rowCount)
        assertEquals("n", result.columns[0].name)
        assertEquals("UInt64", result.columns[0].type)
        assertEquals("s", result.columns[1].name)
        assertEquals("String", result.columns[1].type)
        assertEquals("0", result.cell(0, 0))
        assertEquals("zero", result.cell(0, 1))
        assertEquals("1", result.cell(1, 0))
        assertEquals("one", result.cell(1, 1))
    }

    @Test
    @DisplayName("a null value is shown as the explicit text NULL")
    fun showsTheNullValues() {
        val body = """{"meta":[{"name":"v","type":"Nullable(String)"}],"data":[{"v":null}]}"""
        val result = parser.parse(body, ClickHouseFormat.JSON)
        assertEquals("NULL", result.cell(0, 0))
    }

    @Test
    @DisplayName("a result without rows keeps its columns")
    fun readsTheEmptyResult() {
        val body = """{"meta":[{"name":"n","type":"UInt8"}],"data":[],"rows":0}"""
        val result = parser.parse(body, ClickHouseFormat.JSON)
        assertEquals(1, result.columnCount)
        assertEquals(0, result.rowCount)
    }

    @Test
    @DisplayName("an empty answer is an empty result")
    fun readsTheEmptyAnswer() {
        val result = parser.parse("", ClickHouseFormat.JSON)
        assertEquals(0, result.columnCount)
        assertEquals(0, result.rowCount)
    }

    @Test
    @DisplayName("a column that is missing in a row is an empty cell")
    fun readsTheMissingColumn() {
        val body = """{"meta":[{"name":"a","type":"UInt8"},{"name":"b","type":"UInt8"}],"data":[{"a":1}]}"""
        val result = parser.parse(body, ClickHouseFormat.JSON)
        assertEquals("1", result.cell(0, 0))
        assertEquals("", result.cell(0, 1))
    }

    @Test
    @DisplayName("the values that are not scalars are shown as compact JSON")
    fun readsTheComplexValues() {
        val body = """{"meta":[{"name":"a","type":"Array(UInt8)"}],"data":[{"a":[1,2]}]}"""
        val result = parser.parse(body, ClickHouseFormat.JSON)
        assertEquals("[1,2]", result.cell(0, 0))
    }

    @Test
    @DisplayName("a malformed JSON is refused")
    fun refusesTheMalformedJson() {
        val error = assertThrows(ClickHouseResponseException::class.java) {
            parser.parse("""{"meta": [""", ClickHouseFormat.JSON)
        }
        assertTrue(error.message!!.contains("valid JSON"), error.message)
    }

    @Test
    @DisplayName("a JSON answer that is not an object is refused")
    fun refusesANonObjectAnswer() {
        assertThrows(ClickHouseResponseException::class.java) {
            parser.parse("[1, 2]", ClickHouseFormat.JSON)
        }
    }

    @Test
    @DisplayName("a JSON answer without data is refused")
    fun refusesAnAnswerWithoutData() {
        val error = assertThrows(ClickHouseResponseException::class.java) {
            parser.parse("""{"meta":[]}""", ClickHouseFormat.JSON)
        }
        assertTrue(error.message!!.contains("data"), error.message)
    }

    // -----------------------------------------------------------------
    // TSV
    // -----------------------------------------------------------------

    @Test
    @DisplayName("the TSV answer with names is read: the first line holds the names")
    fun readsTheTsvWithNamesAnswer() {
        val body = "a\tb\n1\tx\n2\ty\n"
        val result = parser.parse(body, ClickHouseFormat.TSV_WITH_NAMES)
        assertEquals(2, result.columnCount)
        assertEquals("a", result.columns[0].name)
        assertEquals("b", result.columns[1].name)
        assertEquals(2, result.rowCount)
        assertEquals("1", result.cell(0, 0))
        assertEquals("x", result.cell(0, 1))
        assertEquals("2", result.cell(1, 0))
        assertEquals("y", result.cell(1, 1))
    }

    @Test
    @DisplayName("a TSV answer with names and no row keeps its columns")
    fun readsTheTsvWithNamesWithoutRow() {
        val result = parser.parse("a\tb\n", ClickHouseFormat.TSV_WITH_NAMES)
        assertEquals(2, result.columnCount)
        assertEquals(0, result.rowCount)
    }

    @Test
    @DisplayName("the TSV answer without names has generated column names")
    fun readsTheTsvAnswer() {
        val result = parser.parse("1\tx\n2\ty\n", ClickHouseFormat.TSV)
        assertEquals(2, result.columnCount)
        assertEquals("column_1", result.columns[0].name)
        assertEquals("column_2", result.columns[1].name)
        assertEquals(2, result.rowCount)
        assertEquals("x", result.cell(0, 1))
    }

    @Test
    @DisplayName("the tabulations inside the values are unescaped")
    fun unescapesTheTabulations() {
        // Measured on play.clickhouse.com on 13/11/2026: a real tab inside a
        // value is written as the two characters "\" and "t".
        val body = "t\na\\tb\n"
        val result = parser.parse(body, ClickHouseFormat.TSV_WITH_NAMES)
        assertEquals("t", result.columns[0].name)
        assertEquals("a\tb", result.cell(0, 0))
    }

    @Test
    @DisplayName("the other escapes of the TSV format are unescaped")
    fun unescapesTheOtherEscapes() {
        val body = "v\n" + "a\\nb\\tc\\\\d\\re" + "\n"
        val result = parser.parse(body, ClickHouseFormat.TSV_WITH_NAMES)
        assertEquals("a\nb\tc\\d\re", result.cell(0, 0))
    }

    @Test
    @DisplayName("a row that is shorter than the header is completed with empty cells")
    fun completesTheShortRows() {
        val result = parser.parse("a\tb\tc\n1\n", ClickHouseFormat.TSV_WITH_NAMES)
        assertEquals(3, result.columnCount)
        assertEquals("1", result.cell(0, 0))
        assertEquals("", result.cell(0, 1))
        assertEquals("", result.cell(0, 2))
    }

    @Test
    @DisplayName("an empty TSV answer is an empty result")
    fun readsTheEmptyTsvAnswer() {
        val result = parser.parse("", ClickHouseFormat.TSV_WITH_NAMES)
        assertEquals(0, result.rowCount)
    }

    // -----------------------------------------------------------------
    // Errors
    // -----------------------------------------------------------------

    @Test
    @DisplayName("the message of an error of the server is read without its code")
    fun readsTheErrorMessage() {
        val body = "Code: 62. DB::Exception: Syntax error: failed at position 1 ('selct'). (SYNTAX_ERROR) (version 25.8.2.29)"
        val message = parser.extractErrorMessage(body)
        assertTrue(message.startsWith("DB::Exception:"), message)
        assertEquals(-1, message.indexOf("Code:"))
    }

    @Test
    @DisplayName("an error without code is read as it is")
    fun readsTheErrorWithoutCode() {
        assertEquals("Boom.", parser.extractErrorMessage("Boom."))
    }

    @Test
    @DisplayName("an empty error body gives an empty message")
    fun readsTheEmptyErrorBody() {
        assertEquals("", parser.extractErrorMessage("   "))
    }
}
