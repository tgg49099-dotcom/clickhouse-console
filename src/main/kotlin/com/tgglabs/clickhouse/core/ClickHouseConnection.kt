package com.tgglabs.clickhouse.core

/**
 * The parameters of a connection to the HTTP interface of a ClickHouse
 * server.
 *
 * The password is NEVER written in the settings file of the IDE: it is kept
 * in the PasswordSafe of the IDE (see
 * `com.tgglabs.clickhouse.settings.ClickHouseCredentialStore`).
 */
data class ClickHouseConnection(
    val host: String = "localhost",
    val port: Int = 8123,
    val user: String = "default",
    val password: String = "",
    val database: String = "default",
    val secure: Boolean = false,
) {

    /** The base URL of the HTTP interface of the server. */
    val url: String get() = "${if (secure) "https" else "http"}://$host:$port/"

    /**
     * The problems that would make the connection fail, as text meant to be
     * shown to the user. An empty list means the connection is usable.
     *
     * An EMPTY USER is not a problem: no `X-ClickHouse-User` header is then
     * sent, and the server applies its own default user. That is what the
     * public playground of ClickHouse expects (measured on
     * play.clickhouse.com on 13/11/2026: sending the user `default` without
     * a password is refused with "Code: 194. DB::Exception: default:
     * Authentication failed", while sending nothing is accepted).
     */
    fun validate(): List<String> {
        val problems = ArrayList<String>()
        if (host.isBlank()) {
            problems.add("The host is empty.")
        }
        if (port < 1 || port > 65535) {
            problems.add("The port must be between 1 and 65535 (it is $port).")
        }
        return problems
    }

    /**
     * The password is masked: this text can end up in a log or in a report.
     */
    override fun toString(): String =
        "ClickHouseConnection(host=$host, port=$port, user=$user, password=***, database=$database, secure=$secure)"
}
