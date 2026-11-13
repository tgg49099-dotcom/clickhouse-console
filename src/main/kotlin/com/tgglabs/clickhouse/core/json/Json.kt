package com.tgglabs.clickhouse.core.json

/**
 * Minimal JSON model.
 *
 * The IntelliJ Platform ships no JSON library (measured on the jars of
 * IntelliJ IDEA Community 2025.3: no Gson, no Jackson, no
 * kotlinx-serialization), so the plugin parses the JSON answer of ClickHouse
 * itself. The model is deliberately small: only what the `JSON` format of
 * ClickHouse can produce is represented.
 *
 * Numbers keep their RAW TEXT: ClickHouse sends `UInt64` values that do not
 * fit in a `Double`, and reformatting them would lose precision.
 */
sealed class JsonValue {

    /** The JSON `null`. ClickHouse uses it for SQL NULL. */
    object Null : JsonValue()

    data class Bool(val value: Boolean) : JsonValue()

    /** A number, kept as the exact text sent by the server. */
    data class Num(val text: String) : JsonValue()

    data class Str(val value: String) : JsonValue()

    data class Arr(val items: List<JsonValue>) : JsonValue()

    data class Obj(val entries: Map<String, JsonValue>) : JsonValue()

    /** The object entries, or `null` if this value is not an object. */
    val asObject: Map<String, JsonValue>?
        get() = (this as? Obj)?.entries

    /** The array items, or `null` if this value is not an array. */
    val asArray: List<JsonValue>?
        get() = (this as? Arr)?.items

    /** The string value, or `null` if this value is not a string. */
    val asString: String?
        get() = (this as? Str)?.value

    /**
     * The value as plain text, for display in a table cell:
     * a string is its text, a number keeps its raw text, a boolean is
     * `true`/`false`, and a null becomes the explicit marker `NULL`
     * (an empty cell would be ambiguous with an empty string).
     */
    fun asDisplayText(): String = when (this) {
        is Null -> "NULL"
        is Bool -> if (value) "true" else "false"
        is Num -> text
        is Str -> value
        is Arr -> compactText()
        is Obj -> compactText()
    }

    /** The value written back as compact JSON (no extra whitespace). */
    fun compactText(): String = StringBuilder().also { write(it) }.toString()

    private fun write(out: StringBuilder) {
        when (this) {
            is Null -> out.append("null")
            is Bool -> out.append(if (value) "true" else "false")
            is Num -> out.append(text)
            is Str -> writeString(out, value)
            is Arr -> {
                out.append('[')
                items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    item.write(out)
                }
                out.append(']')
            }
            is Obj -> {
                out.append('{')
                var first = true
                for ((key, item) in entries) {
                    if (!first) out.append(',')
                    first = false
                    writeString(out, key)
                    out.append(':')
                    item.write(out)
                }
                out.append('}')
            }
        }
    }

    private fun writeString(out: StringBuilder, value: String) {
        out.append('"')
        for (char in value) {
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else ->
                    if (char < ' ') {
                        out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        out.append(char)
                    }
            }
        }
        out.append('"')
    }
}

/** Thrown when the text is not valid JSON. */
class JsonParseException(message: String) : Exception(message)

/**
 * A strict recursive-descent JSON parser (RFC 8259).
 *
 * Strict means: no trailing comma, no comment, no single quote, no unquoted
 * key, no trailing content after the top-level value, no raw control
 * character inside a string. A malformed answer therefore raises an
 * exception instead of producing a half-built table.
 */
object JsonParser {

    /** Maximum nesting depth: a deeper answer is refused instead of blowing the stack. */
    private const val MAX_DEPTH = 200

    fun parse(text: String): JsonValue {
        val reader = Reader(text)
        reader.skipWhitespace()
        val value = reader.readValue(0)
        reader.skipWhitespace()
        if (!reader.atEnd()) {
            throw JsonParseException("Unexpected character '${reader.peek()}' at position ${reader.position}")
        }
        return value
    }

    private class Reader(private val text: String) {
        var position: Int = 0
            private set

        fun atEnd(): Boolean = position >= text.length

        fun peek(): Char =
            if (atEnd()) throw JsonParseException("Unexpected end of input") else text[position]

        fun skipWhitespace() {
            while (position < text.length) {
                when (text[position]) {
                    ' ', '\t', '\n', '\r' -> position++
                    else -> return
                }
            }
        }

        fun readValue(depth: Int): JsonValue {
            if (depth > MAX_DEPTH) {
                throw JsonParseException("The JSON answer is nested deeper than $MAX_DEPTH levels")
            }
            skipWhitespace()
            return when (peek()) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> JsonValue.Str(readString())
                't' -> readLiteral("true", JsonValue.Bool(true))
                'f' -> readLiteral("false", JsonValue.Bool(false))
                'n' -> readLiteral("null", JsonValue.Null)
                else -> readNumber()
            }
        }

        private fun readLiteral(literal: String, value: JsonValue): JsonValue {
            if (!text.startsWith(literal, position)) {
                throw JsonParseException("Invalid literal at position $position")
            }
            position += literal.length
            return value
        }

        private fun readObject(depth: Int): JsonValue {
            position++ // '{'
            val entries = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (!atEnd() && peek() == '}') {
                position++
                return JsonValue.Obj(entries)
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') {
                    throw JsonParseException("A JSON key must be a string, at position $position")
                }
                val key = readString()
                skipWhitespace()
                if (peek() != ':') {
                    throw JsonParseException("Expected ':' after the key '$key', at position $position")
                }
                position++
                entries[key] = readValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    '}' -> {
                        position++
                        return JsonValue.Obj(entries)
                    }
                    else -> throw JsonParseException("Expected ',' or '}' in the JSON object, at position $position")
                }
            }
        }

        private fun readArray(depth: Int): JsonValue {
            position++ // '['
            val items = ArrayList<JsonValue>()
            skipWhitespace()
            if (!atEnd() && peek() == ']') {
                position++
                return JsonValue.Arr(items)
            }
            while (true) {
                items.add(readValue(depth + 1))
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    ']' -> {
                        position++
                        return JsonValue.Arr(items)
                    }
                    else -> throw JsonParseException("Expected ',' or ']' in the JSON array, at position $position")
                }
            }
        }

        private fun readString(): String {
            position++ // opening quote
            val out = StringBuilder()
            while (true) {
                if (atEnd()) {
                    throw JsonParseException("The JSON string is not closed")
                }
                val char = text[position]
                when {
                    char == '"' -> {
                        position++
                        return out.toString()
                    }
                    char == '\\' -> {
                        position++
                        if (atEnd()) {
                            throw JsonParseException("The JSON string is not closed")
                        }
                        val escaped = text[position]
                        position++
                        when (escaped) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (position + 4 > text.length) {
                                    throw JsonParseException("Truncated \\u escape at position $position")
                                }
                                val hex = text.substring(position, position + 4)
                                val code = hex.toIntOrNull(16)
                                    ?: throw JsonParseException("Invalid \\u escape '$hex' at position $position")
                                out.append(code.toChar())
                                position += 4
                            }
                            else -> throw JsonParseException("Invalid escape '\\$escaped' at position ${position - 1}")
                        }
                    }
                    char < ' ' -> throw JsonParseException("Raw control character in a JSON string, at position $position")
                    else -> {
                        out.append(char)
                        position++
                    }
                }
            }
        }

        private fun readNumber(): JsonValue {
            val start = position
            if (!atEnd() && peek() == '-') position++
            if (atEnd()) throw JsonParseException("Truncated number at position $start")
            if (peek() == '0') {
                position++
            } else if (peek() in '1'..'9') {
                while (!atEnd() && peek() in '0'..'9') position++
            } else {
                throw JsonParseException("Invalid number at position $start")
            }
            if (!atEnd() && peek() == '.') {
                position++
                if (atEnd() || peek() !in '0'..'9') {
                    throw JsonParseException("Invalid fraction in the number at position $start")
                }
                while (!atEnd() && peek() in '0'..'9') position++
            }
            if (!atEnd() && (peek() == 'e' || peek() == 'E')) {
                position++
                if (!atEnd() && (peek() == '+' || peek() == '-')) position++
                if (atEnd() || peek() !in '0'..'9') {
                    throw JsonParseException("Invalid exponent in the number at position $start")
                }
                while (!atEnd() && peek() in '0'..'9') position++
            }
            return JsonValue.Num(text.substring(start, position))
        }
    }
}
