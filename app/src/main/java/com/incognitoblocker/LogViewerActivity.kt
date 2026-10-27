package com.incognitoblocker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors

class LogViewerActivity : AppCompatActivity() {

    private lateinit var tvLogs: TextView
    private lateinit var spinnerFilter: Spinner
    private lateinit var btnRefresh: Button
    private lateinit var btnClear: Button
    private lateinit var btnCopy: Button
    private lateinit var progressBar: ProgressBar

    private val logBuffer = StringBuilder()
    private val maxLogLines = 2000
    private var currentFilter = "All"

    private val filters = arrayOf(
        "All",
        "IncognitoAccessibility",
        "MainActivity",
        "MonitoringService",
        "BootReceiver",
        "IncognitoBlockerApp",
        "Errors Only"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        initViews()
        setupToolbar()
        setupSpinner()
        setupButtons()
        loadLogs()
    }

    private fun initViews() {
        tvLogs = findViewById(R.id.tvLogs)
        spinnerFilter = findViewById(R.id.spinnerFilter)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnClear = findViewById(R.id.btnClear)
        btnCopy = findViewById(R.id.btnCopy)
        progressBar = findViewById(R.id.progressBar)

        tvLogs.movementMethod = ScrollingMovementMethod()
    }

    private fun setupToolbar() {
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filters)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilter.adapter = adapter
        spinnerFilter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentFilter = filters[position]
                applyFilter()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        btnRefresh.setOnClickListener { loadLogs() }
        btnClear.setOnClickListener {
            logBuffer.setLength(0)
            tvLogs.text = ""
            Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
        }
        btnCopy.setOnClickListener {
            val text = tvLogs.text.toString()
            if (text.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("IncognitoBlocker Logs", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Copied ${text.length} chars to clipboard", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No logs to copy", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadLogs() {
        progressBar.visibility = View.VISIBLE
        btnRefresh.isEnabled = false

        Executors.newSingleThreadExecutor().execute {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val newLogs = StringBuilder()
                var lineCount = 0

                while (true) {
                    val line = reader.readLine()
                    if (line == null || lineCount >= maxLogLines) break
                    // Filter for our app tags
                    if (line.contains("IncognitoAccessibility") ||
                        line.contains("MainActivity") ||
                        line.contains("MonitoringService") ||
                        line.contains("BootReceiver") ||
                        line.contains("IncognitoBlockerApp") ||
                        line.contains("IncognitoBlocker") ||
                        line.contains("incognito") ||
                        (currentFilter != "All" && currentFilter != "Errors Only" && line.contains(currentFilter)) ||
                        (currentFilter == "Errors Only" && (line.contains(" E ") || line.contains(" W ")))) {

                        newLogs.append(line).append("\n")
                        lineCount++
                    }
                }
                reader.close()

                runOnUiThread {
                    // Prepend new logs (newest first)
                    logBuffer.insert(0, newLogs)
                    // Trim if too long
                    val lines = logBuffer.toString().split("\n")
                    if (lines.size > maxLogLines) {
                        logBuffer.setLength(0)
                        lines.take(maxLogLines).forEach { logBuffer.append(it).append("\n") }
                    }
                    applyFilter()
                    progressBar.visibility = View.GONE
                    btnRefresh.isEnabled = true
                    // Auto-scroll to top (newest)
                    tvLogs.post { tvLogs.scrollTo(0, 0) }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnRefresh.isEnabled = true
                    tvLogs.text = "Error reading logs: ${e.message}"
                    Log.e("LogViewer", "Failed to read logcat", e)
                }
            }
        }
    }

    private fun applyFilter() {
        val sourceLogs = logBuffer.toString()
        var filteredLogs: String

        when (currentFilter) {
            "All" -> {
                filteredLogs = sourceLogs
            }
            "Errors Only" -> {
                filteredLogs = sourceLogs.lines()
                    .filter { it.contains(" E ") || it.contains(" W ") }
                    .joinToString("\n")
            }
            else -> {
                // Filter by tag name
                filteredLogs = sourceLogs.lines()
                    .filter { it.contains(currentFilter) }
                    .joinToString("\n")
            }
        }

        tvLogs.text = filteredLogs
        // Scroll to top (newest logs)
        tvLogs.post { tvLogs.scrollTo(0, 0) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        loadLogs()
    }
}