package com.tgglabs.clickhouse.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * The connection settings of the plugin, kept in the settings file of the IDE
 * (`clickhouse-console.xml`).
 *
 * The MOT DE PASSE IS NOT HERE: it is kept in the PasswordSafe of the IDE
 * (see [ClickHouseCredentialStore]). This file is a plain file on disk, it
 * must never hold a secret.
 */
@Service(Service.Level.APP)
@State(name = "ClickHouseConsoleSettings", storages = [Storage("clickhouse-console.xml")])
class ClickHouseSettings : PersistentStateComponent<ClickHouseSettings.State> {

    /**
     * The stored values. The port is kept as text: an empty or a wrong port
     * must not stop the IDE from loading its settings.
     */
    class State {
        var host: String = "localhost"
        var port: String = "8123"
        var user: String = "default"
        var database: String = "default"
        var secure: Boolean = false
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): ClickHouseSettings =
            ApplicationManager.getApplication().getService(ClickHouseSettings::class.java)
    }
}
