package com.tgglabs.clickhouse.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.tgglabs.clickhouse.core.ClickHouseClient
import com.tgglabs.clickhouse.core.ClickHouseConnection
import com.tgglabs.clickhouse.core.ClickHouseFormat
import com.tgglabs.clickhouse.core.QueryResult
import com.tgglabs.clickhouse.settings.ClickHouseCredentialStore
import com.tgglabs.clickhouse.settings.ClickHouseSettings
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JSplitPane

/**
 * The content of the "ClickHouse Console" tool window: the connection
 * parameters, the SQL editor, the result table and a status line.
 */
class ClickHousePanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val hostField = JBTextField()
    private val portField = JBTextField()
    private val userField = JBTextField()
    private val passwordField = JBPasswordField()
    private val databaseField = JBTextField()
    private val secureBox = JCheckBox("Use TLS (https)")

    private val sqlEditor = JBTextArea()
    private val tableModel = QueryResultTableModel()
    private val resultTable = JBTable(tableModel)
    private val statusLabel = JBLabel(" ")

    private val runButton = JButton("Run")
    private val testButton = JButton("Test connection")

    private val client = ClickHouseClient()

    init {
        border = JBUI.Borders.empty(6)
        add(buildConnectionPanel(), BorderLayout.NORTH)
        add(buildResultPanel(), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        runButton.addActionListener { runQuery() }
        testButton.addActionListener { testConnection() }

        loadSettings()
    }

    // -----------------------------------------------------------------
    // Layout
    // -----------------------------------------------------------------

    private fun buildConnectionPanel(): JPanel {
        val form = FormBuilder.createFormBuilder()
        form.addLabeledComponent(JBLabel("Host:"), hostField, true)
        form.addLabeledComponent(JBLabel("Port:"), portField, false)
        form.addLabeledComponent(JBLabel("User:"), userField, false)
        form.addLabeledComponent(JBLabel("Password:"), passwordField, false)
        form.addLabeledComponent(JBLabel("Database:"), databaseField, false)
        form.addComponent(secureBox)

        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        buttons.add(runButton)
        buttons.add(testButton)
        form.addComponent(buttons)

        return form.panel
    }

    private fun buildResultPanel(): JSplitPane {
        sqlEditor.font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        sqlEditor.text = "SELECT 1"

        resultTable.autoResizeMode = JBTable.AUTO_RESIZE_ALL_COLUMNS
        resultTable.setShowGrid(false)

        val splitter = JSplitPane(JSplitPane.VERTICAL_SPLIT, JBScrollPane(sqlEditor), JBScrollPane(resultTable))
        splitter.resizeWeight = 0.35
        splitter.border = JBUI.Borders.emptyTop(4)
        return splitter
    }

    // -----------------------------------------------------------------
    // Actions
    // -----------------------------------------------------------------

    private fun runQuery() {
        val connection = readConnection()
        val problems = connection.validate()
        if (problems.isNotEmpty()) {
            showError(problems.joinToString(" "))
            return
        }
        val query = sqlEditor.text.trim()
        if (query.isEmpty()) {
            showError("The query is empty.")
            return
        }
        saveSettings(connection)

        setStatus("Running the query…")
        object : Task.Backgroundable(project, "Running the ClickHouse query", true) {
            private var result: QueryResult? = null
            private var failure: String? = null

            override fun run(indicator: ProgressIndicator) {
                try {
                    result = client.execute(connection, query, ClickHouseFormat.JSON)
                } catch (e: Exception) {
                    failure = e.message ?: e.toString()
                }
            }

            override fun onSuccess() {
                val answer = result
                if (answer == null) {
                    showError(failure ?: "The query failed.")
                    return
                }
                tableModel.setResult(answer)
                setStatus("${answer.rowCount} row(s), ${answer.columnCount} column(s).")
            }

            override fun onThrowable(error: Throwable) {
                showError(error.message ?: error.toString())
            }
        }.queue()
    }

    private fun testConnection() {
        val connection = readConnection()
        val problems = connection.validate()
        if (problems.isNotEmpty()) {
            showError(problems.joinToString(" "))
            return
        }
        saveSettings(connection)

        setStatus("Connecting…")
        object : Task.Backgroundable(project, "Connecting to the ClickHouse server", true) {
            private var version: String? = null
            private var failure: String? = null

            override fun run(indicator: ProgressIndicator) {
                try {
                    version = client.testConnection(connection)
                } catch (e: Exception) {
                    failure = e.message ?: e.toString()
                }
            }

            override fun onSuccess() {
                val answer = version
                if (answer == null) {
                    showError(failure ?: "The connection failed.")
                    return
                }
                setStatus("Connected. ClickHouse $answer.")
            }

            override fun onThrowable(error: Throwable) {
                showError(error.message ?: error.toString())
            }
        }.queue()
    }

    // -----------------------------------------------------------------
    // Settings
    // -----------------------------------------------------------------

    private fun readConnection(): ClickHouseConnection {
        val port = portField.text.trim().toIntOrNull() ?: -1
        return ClickHouseConnection(
            host = hostField.text.trim(),
            port = port,
            user = userField.text.trim(),
            password = String(passwordField.password ?: CharArray(0)),
            database = databaseField.text.trim(),
            secure = secureBox.isSelected,
        )
    }

    private fun loadSettings() {
        val settings = ClickHouseSettings.getInstance().state
        hostField.text = settings.host
        portField.text = settings.port
        userField.text = settings.user
        databaseField.text = settings.database
        secureBox.isSelected = settings.secure
        val password = ClickHouseCredentialStore.getPassword(settings.host, settings.port.toIntOrNull() ?: 8123, settings.user)
        if (password.isNotEmpty()) {
            passwordField.text = password
        }
    }

    private fun saveSettings(connection: ClickHouseConnection) {
        val settings = ClickHouseSettings.getInstance()
        settings.state.host = connection.host
        settings.state.port = connection.port.toString()
        settings.state.user = connection.user
        settings.state.database = connection.database
        settings.state.secure = connection.secure
        ClickHouseCredentialStore.setPassword(connection.host, connection.port, connection.user, connection.password)
    }

    // -----------------------------------------------------------------
    // Status line
    // -----------------------------------------------------------------

    private fun setStatus(text: String) {
        statusLabel.foreground = UIUtil.getContextHelpForeground()
        statusLabel.text = text
    }

    private fun showError(message: String) {
        statusLabel.foreground = UIUtil.getErrorForeground()
        statusLabel.text = message
    }

    override fun dispose() {
        // Nothing to release: the client holds no open connection.
    }
}
