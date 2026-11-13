package com.tgglabs.clickhouse.core.json

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The tests of the JSON parser that reads the answers of ClickHouse.
 */
class JsonParserTest {

    @Test
    @DisplayName("an object is read as a map of values")
    fun readsAnObject() {
        val value = JsonParser.parse("""{"name": "n", "type": "UInt64"}""")
        val entries = value.asObject
        assertTrue(entries != null)
        assertEquals(2, entries!!.size)
        assertEquals("n", entries["name"]?.asString)
        assertEquals("UInt64", entries["type"]?.asString)
    }

    @Test
    @DisplayName("the numbers keep the exact text sent by the server")
    fun keepsTheRawTextOfTheNumbers() {
        val value = JsonParser.parse("""{"v": 18446744073709551615}""")
        assertEquals("18446744073709551615", value.asObject?.get("v")?.asDisplayText())
    }

    @Test
    @DisplayName("the negative numbers, the exponents and the fractions are read")
    fun readsEveryNumber() {
        assertEquals("-12", JsonParser.parse("-12").asDisplayText())
        assertEquals("1.5", JsonParser.parse("1.5").asDisplayText())
        assertEquals("1e10", JsonParser.parse("1e10").asDisplayText())
        assertEquals("-2.5E-3", JsonParser.parse("-2.5E-3").asDisplayText())
    }

    @Test
    @DisplayName("the arrays are read, empty or not")
    fun readsTheArrays() {
        assertEquals(0, JsonParser.parse("[]").asArray?.size)
        val items = JsonParser.parse("""[1, "two", null, {"a": 1}]""").asArray
        assertEquals(4, items?.size)
        assertEquals("two", items?.get(1)?.asString)
        assertTrue(items?.get(2) is JsonValue.Null)
        assertEquals(1, items?.get(3)?.asObject?.get("a")?.asDisplayText()?.toInt())
    }

    @Test
    @DisplayName("the escapes of the strings are read")
    fun readsTheEscapes() {
        assertEquals("a\"b", JsonParser.parse(""""a\"b"""").asString)
        assertEquals("a\\b", JsonParser.parse(""""a\\b"""").asString)
        assertEquals("a\nb", JsonParser.parse(""""a\nb"""").asString)
        assertEquals("a\tb", JsonParser.parse(""""a\tb"""").asString)
        assertEquals("a/b", JsonParser.parse(""""a\/b"""").asString)
        assertEquals("é", JsonParser.parse(""""é"""").asString)
    }

    @Test
    @DisplayName("the null and the booleans are read")
    fun readsTheLiterals() {
        assertTrue(JsonParser.parse("null") is JsonValue.Null)
        assertEquals("true", JsonParser.parse("true").asDisplayText())
        assertEquals("false", JsonParser.parse("false").asDisplayText())
    }

    @Test
    @DisplayName("a null is shown as the explicit text NULL")
    fun showsNull() {
        assertEquals("NULL", JsonValue.Null.asDisplayText())
    }

    @Test
    @DisplayName("the empty text is not JSON")
    fun refusesTheEmptyText() {
        assertThrows(JsonParseException::class.java) { JsonParser.parse("") }
    }

    @Test
    @DisplayName("a malformed JSON is refused, and the error says where")
    fun refusesTheMalformedJson() {
        val error = assertThrows(JsonParseException::class.java) { JsonParser.parse("""{"a": }""") }
        assertTrue(error.message!!.contains("position"), error.message)
    }

    @Test
    @DisplayName("a trailing comma is refused")
    fun refusesTheTrailingComma() {
        assertThrows(JsonParseException::class.java) { JsonParser.parse("""{"a": 1,}""") }
        assertThrows(JsonParseException::class.java) { JsonParser.parse("""[1, 2,]""") }
    }

    @Test
    @DisplayName("a key that is not a string is refused")
    fun refusesAnUnquotedKey() {
        assertThrows(JsonParseException::class.java) { JsonParser.parse("""{a: 1}""") }
    }

    @Test
    @DisplayName("something after the top-level value is refused")
    fun refusesTheTrailingContent() {
        assertThrows(JsonParseException::class.java) { JsonParser.parse("""{"a": 1} garbage""") }
    }

    @Test
    @DisplayName("a string that is not closed is refused")
    fun refusesAnUnclosedString() {
        assertThrows(JsonParseException::class.java) { JsonParser.parse(""""abc""") }
    }

    @Test
    @DisplayName("a raw control character inside a string is refused")
    fun refusesTheRawControlCharacter() {
        assertThrows(JsonParseException::class.java) { JsonParser.parse("\"a\nb\"") }
    }

    @Test
    @DisplayName("an unknown escape is refused")
    fun refusesAnUnknownEscape() {
        assertThrows(JsonParseException::class.java) { JsonParser.parse(""""a\qb"""") }
    }

    @Test
    @DisplayName("the values are written back as compact JSON")
    fun writesCompactJson() {
        val value = JsonParser.parse("""{ "a" : [ 1 , "b" ] , "c" : null }""")
        assertEquals("""{"a":[1,"b"],"c":null}""", value.compactText())
    }

    @Test
    @DisplayName("an object that is too deep is refused instead of blowing the stack")
    fun refusesTheTooDeepJson() {
        val deep = "[".repeat(500) + "]".repeat(500)
        assertThrows(JsonParseException::class.java) { JsonParser.parse(deep) }
    }

    @Test
    @DisplayName("a value that is not an object has no object view")
    fun hasNoObjectView() {
        assertNull(JsonParser.parse("[1]").asObject)
    }
}
