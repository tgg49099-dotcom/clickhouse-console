package com.tgglabs.clickhouse.core

/**
 * The answer formats of the official ClickHouse HTTP interface that this
 * plugin understands.
 *
 * The format is asked for with the `default_format` parameter of the HTTP
 * interface (official documentation: "default_format — the format of the
 * data returned by the server, if the query does not specify FORMAT").
 */
enum class ClickHouseFormat(val formatName: String) {

    /** `FORMAT JSON` — the richest: column names, column types and rows. */
    JSON("JSON"),

    /** `FORMAT TSVWithNames` — the first line holds the column names. */
    TSV_WITH_NAMES("TSVWithNames"),

    /** `FORMAT TSV` — no column names. */
    TSV("TSV")
}
