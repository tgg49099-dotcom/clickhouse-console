package com.tgglabs.clickhouse.core

/**
 * One column of a result: its name and the type the server gave for it.
 */
data class QueryColumn(val name: String, val type: String)

/**
 * The result of a query: the columns, then the rows.
 *
 * A cell is always a `String`: the table shows what the server sent, and the
 * type of the column is displayed next to its name. A SQL NULL is shown as
 * the explicit text `NULL` (an empty cell would be ambiguous with an empty
 * string).
 */
data class QueryResult(
    val columns: List<QueryColumn>,
    val rows: List<List<String>>,
) {

    val rowCount: Int get() = rows.size

    val columnCount: Int get() = columns.size

    /** The value of a cell, or an empty string when the row is shorter. */
    fun cell(row: Int, column: Int): String = rows[row].getOrElse(column) { "" }

    companion object {
        val EMPTY: QueryResult = QueryResult(emptyList(), emptyList())
    }
}
