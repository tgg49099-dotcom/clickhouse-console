package com.tgglabs.clickhouse.core

import com.tgglabs.clickhouse.core.json.JsonParseException
import com.tgglabs.clickhouse.core.json.JsonParser
import com.tgglabs.clickhouse.core.json.JsonValue

/**
 * Turns the answer of the ClickHouse HTTP interface into a [QueryResult].
 *
 * Two families are supported, and both are official ClickHouse formats:
 *
 *  * `JSON` — an object with a `meta` array (the columns: `name`, `type`) and
 *    a `data` array (the rows, one object per row);
 *  * `TSV` / `TSVWithNames` — one row per line, values separated by tabs.
 *    In that format a real tab inside a value is escaped as `\t` (verified
 *    against play.clickhouse.com on 13/11/2026), so splitting a line on the
 *    real tab characters is exact.
 *
 * The class has NO dependency on the IDE: it is plain Kotlin, and it is
 * covered by its own unit tests.
 */
class ClickHouseResponseParser {

    /**
     * Parses an answer.
     *
     * @throws ClickHouseResponseException when the answer does not have the
     *         expected shape (malformed JSON, unexpected structure).
     */
    fun parse(body: String, format: ClickHouseFormat): QueryResult = when (format) {
        ClickHouseFormat.JSON -> parseJson(body)
        ClickHouseFormat.TSV_WITH_NAMES -> parseTsvWithNames(body)
        ClickHouseFormat.TSV -> parseTsv(body)
    }

    /**
     * Parses a `FORMAT JSON` answer.
     */
    fun parseJson(body: String): QueryResult {
        if (body.isBlank()) return QueryResult.EMPTY

        val root = try {
            JsonParser.parse(body)
        } catch (e: JsonParseException) {
            throw ClickHouseResponseException("The server did not answer valid JSON: ${e.message}", e)
        }

        val entries = root.asObject
            ?: throw ClickHouseResponseException("The server did not answer a JSON object")

        val columns = readColumns(entries["meta"])
        val data = entries["data"]?.asArray
            ?: throw ClickHouseResponseException("The JSON answer has no 'data' array")

        val rows = data.map { row ->
            val cells = row.asObject
                ?: throw ClickHouseResponseException("A row of the JSON answer is not an object")
            columns.map { column -> cells[column.name]?.asDisplayText() ?: "" }
        }

        return QueryResult(columns, rows)
    }

    /**
     * Parses a `FORMAT TSVWithNames` answer: the first line holds the names
     * of the columns, the next ones the rows.
     */
    fun parseTsvWithNames(body: String): QueryResult {
        val lines = splitLines(body)
        if (lines.isEmpty()) return QueryResult.EMPTY

        val names = splitTsvLine(lines[0])
        val columns = names.map { QueryColumn(it, "") }
        val rows = lines.drop(1).map { line ->
            val cells = splitTsvLine(line)
            columns.indices.map { index -> cells.getOrElse(index) { "" } }
        }
        return QueryResult(columns, rows)
    }

    /**
     * Parses a `FORMAT TSV` answer: there is no header line, so the columns
     * are named `column_1`, `column_2`...
     */
    fun parseTsv(body: String): QueryResult {
        val lines = splitLines(body)
        if (lines.isEmpty()) return QueryResult.EMPTY

        val rows = lines.map { line -> splitTsvLine(line) }
        val width = rows.maxOf { it.size }
        val columns = (1..width).map { QueryColumn("column_$it", "") }
        val padded = rows.map { cells -> columns.indices.map { index -> cells.getOrElse(index) { "" } } }
        return QueryResult(columns, padded)
    }

    /**
     * Extracts the error message of a failed query.
     *
     * ClickHouse answers an error with a plain text body that starts with the
     * code, for example:
     * `Code: 62. DB::Exception: Syntax error: failed at position 1 ('selct').`
     * The prefix is removed: what is shown to the user is the message itself.
     */
    fun extractErrorMessage(body: String): String {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return ""
        val withoutCode = trimmed.replace(Regex("^Code:\\s*\\d+\\.\\s*"), "")
        return withoutCode.trim()
    }

    // -----------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------

    private fun readColumns(meta: JsonValue?): List<QueryColumn> {
        val items = meta?.asArray ?: return emptyList()
        return items.map { item ->
            val entries = item.asObject
                ?: throw ClickHouseResponseException("A column of the JSON answer is not an object")
            val name = entries["name"]?.asString ?: ""
            val type = entries["type"]?.asString ?: ""
            QueryColumn(name, type)
        }
    }

    /** Splits the body into lines, without the trailing empty line. */
    private fun splitLines(body: String): List<String> {
        if (body.isEmpty()) return emptyList()
        val lines = body.split('\n').map { line ->
            if (line.endsWith('\r')) line.dropLast(1) else line
        }
        // ClickHouse ends its answers with a newline: the last (empty) element
        // is not a row.
        return if (lines.isNotEmpty() && lines.last().isEmpty()) lines.dropLast(1) else lines
    }

    /**
     * Splits one TSV line on the REAL tabs and unescapes the values.
     *
     * In the TSV format of ClickHouse, a tab inside a value is written as the
     * two characters `\` and `t`, so a real tab is always a separator.
     */
    private fun splitTsvLine(line: String): List<String> {
        if (line.isEmpty()) return emptyList()
        val cells = ArrayList<String>()
        val current = StringBuilder()
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '\\' && index + 1 < line.length -> {
                    val escaped = line[index + 1]
                    current.append(
                        when (escaped) {
                            't' -> '\t'
                            'n' -> '\n'
                            'r' -> '\r'
                            'b' -> '\b'
                            'f' -> '\u000C'
                            '0' -> '\u0000'
                            '\\' -> '\\'
                            '\'' -> '\''
                            else -> escaped
                        }
                    )
                    index += 2
                }
                char == '\t' -> {
                    cells.add(current.toString())
                    current.setLength(0)
                    index++
                }
                else -> {
                    current.append(char)
                    index++
                }
            }
        }
        cells.add(current.toString())
        return cells
    }
}

/** Thrown when the answer of the server cannot be read as a result. */
class ClickHouseResponseException(message: String, cause: Throwable? = null) : Exception(message, cause)
