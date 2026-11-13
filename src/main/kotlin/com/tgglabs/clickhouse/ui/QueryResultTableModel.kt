package com.tgglabs.clickhouse.ui

import com.tgglabs.clickhouse.core.QueryResult
import javax.swing.table.AbstractTableModel

/**
 * The table model of the result: the columns the server announced, then the
 * rows it sent.
 *
 * The name of a column is shown followed by its type, the way the ClickHouse
 * command line client shows it (`name (type)`).
 */
class QueryResultTableModel(result: QueryResult = QueryResult.EMPTY) : AbstractTableModel() {

    private var columns = result.columns
    private var rows = result.rows

    fun setResult(result: QueryResult) {
        columns = result.columns
        rows = result.rows
        fireTableStructureChanged()
    }

    fun result(): QueryResult = QueryResult(columns, rows)

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String {
        val columnInfo = columns[column]
        return if (columnInfo.type.isEmpty()) columnInfo.name else "${columnInfo.name} (${columnInfo.type})"
    }

    override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows.getOrNull(rowIndex) ?: return ""
        return row.getOrElse(columnIndex) { "" }
    }
}
