package com.enofir.tecnicos_app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.enofir.tecnicos_app.R
import com.enofir.tecnicos_app.core.HistoryStore
import com.enofir.tecnicos_app.core.PrintHistoryStore
import com.enofir.tecnicos_app.model.HistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : BaseActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var container: LinearLayout
    private lateinit var btnViewTerminals: Button
    private lateinit var btnViewPrints: Button

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private var currentView = VIEW_TERMINALS

    companion object {
        private const val VIEW_TERMINALS = 0
        private const val VIEW_PRINTS = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        tvTitle = findViewById(R.id.tvHistoryTitle)
        container = findViewById(R.id.historyContainer)
        btnViewTerminals = findViewById(R.id.btnViewTerminals)
        btnViewPrints = findViewById(R.id.btnViewPrints)

        val btnBack = findViewById<Button>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        btnViewTerminals.setOnClickListener {
            currentView = VIEW_TERMINALS
            updateView()
        }

        btnViewPrints.setOnClickListener {
            currentView = VIEW_PRINTS
            updateView()
        }
    }

    override fun onResume() {
        super.onResume()
        updateView()
    }

    private fun updateView() {
        updateButtonStates()

        when (currentView) {
            VIEW_TERMINALS -> showTerminalsHistory()
            VIEW_PRINTS -> showPrintsHistory()
        }
    }

    private fun updateButtonStates() {
        // Resaltar el botón activo
        btnViewTerminals.alpha = if (currentView == VIEW_TERMINALS) 1.0f else 0.5f
        btnViewPrints.alpha = if (currentView == VIEW_PRINTS) 1.0f else 0.5f
    }

    private fun showTerminalsHistory() {
        val today = HistoryStore.getToday(this)
            .filter { it.action == "COMPLETE" && it.ok }

        tvTitle.text = "Terminales de hoy (${today.size})"
        renderTerminalRows(today)
    }

    private fun showPrintsHistory() {
        val today = PrintHistoryStore.getToday(this)

        tvTitle.text = "Impresiones de hoy (${today.size})"
        renderPrintRows(today)
    }

    private fun renderTerminalRows(items: List<HistoryEntry>) {
        container.removeAllViews()

        if (items.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Todavía no hay terminales finalizadas hoy."
                textSize = 14f
            }
            container.addView(empty)
            return
        }

        // Encabezado
        val header = layoutInflater.inflate(R.layout.row_history, container, false)
        header.findViewById<TextView>(R.id.tvTime).apply {
            text = "Hora"
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        header.findViewById<TextView>(R.id.tvSerial).apply {
            text = "Serial"
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        header.findViewById<TextView>(R.id.tvRole).apply {
            text = "Rol"
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        container.addView(header)

        // Filas
        items.forEach { e ->
            val row = layoutInflater.inflate(R.layout.row_history, container, false)
            row.findViewById<TextView>(R.id.tvTime).text = timeFmt.format(Date(e.ts))
            row.findViewById<TextView>(R.id.tvSerial).text = e.serial
            row.findViewById<TextView>(R.id.tvRole).text = e.role
            container.addView(row)
        }
    }

    private fun renderPrintRows(items: List<PrintHistoryStore.PrintEntry>) {
        container.removeAllViews()

        if (items.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Todavía no hay impresiones de hoy."
                textSize = 14f
            }
            container.addView(empty)
            return
        }

        // Encabezado
        val header = layoutInflater.inflate(R.layout.row_print_history, container, false)
        header.findViewById<TextView>(R.id.tvTime).apply {
            text = "Hora"
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        header.findViewById<TextView>(R.id.tvSerial).apply {
            text = "Serial"
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        header.findViewById<ImageButton>(R.id.btnCopy).visibility = android.view.View.INVISIBLE
        container.addView(header)

        // Filas
        items.forEach { entry ->
            val row = layoutInflater.inflate(R.layout.row_print_history, container, false)
            row.findViewById<TextView>(R.id.tvTime).text = timeFmt.format(Date(entry.ts))
            row.findViewById<TextView>(R.id.tvSerial).text = entry.serial

            row.findViewById<ImageButton>(R.id.btnCopy).setOnClickListener {
                copyToClipboard(entry.serial)
            }

            container.addView(row)
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Serial", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copiado: $text", Toast.LENGTH_SHORT).show()
    }
}
