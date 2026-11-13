package com.tgglabs.clickhouse.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * The password of the ClickHouse server, kept in the PasswordSafe of the IDE
 * and NOWHERE ELSE.
 *
 * The settings file of the IDE is a plain file on disk: it must never hold a
 * secret. The PasswordSafe is the place the IDE itself uses for the
 * passwords of its database tools.
 */
object ClickHouseCredentialStore {

    private const val SERVICE_NAME = "ClickHouse Console"

    /**
     * The key of the password: one password per server and per user. The
     * password of the user `default` on `localhost:8123` is not the password
     * of the user `reader` on the same server.
     */
    private fun key(host: String, port: Int, user: String): String = "$host:$port:$user"

    private fun attributes(host: String, port: Int, user: String): CredentialAttributes =
        CredentialAttributes(SERVICE_NAME, key(host, port, user))

    /** The stored password, or an empty text when there is none. */
    fun getPassword(host: String, port: Int, user: String): String =
        PasswordSafe.instance.getPassword(attributes(host, port, user)).orEmpty()

    /** Stores the password. An empty text removes it. */
    fun setPassword(host: String, port: Int, user: String, password: String) {
        val attributes = attributes(host, port, user)
        if (password.isEmpty()) {
            PasswordSafe.instance.set(attributes, null)
        } else {
            PasswordSafe.instance.setPassword(attributes, password)
        }
    }
}
