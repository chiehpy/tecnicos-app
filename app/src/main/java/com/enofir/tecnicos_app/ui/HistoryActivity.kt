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
import com.enofir.tecnicos_app.core.ApiClient
import com.enofir.tecnicos_app.core.HistoryStore
import com.enofir.tecnicos_app.core.PrintHistoryStore
import com.enofir.tecnicos_app.model.HistoryEntry
import com.enofir.tecnicos_app.model.HistoryResponse
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HistoryActivity : BaseActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var container: LinearLayout
    private lateinit var btnViewTerminals: Button
    private lateinit var btnViewPrints: Button

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    private var currentView = VIEW_TERMINALS
    private val groupCollapsed = mutableMapOf<String, Boolean>()

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
        btnViewTerminals.alpha = if (currentView == VIEW_TERMINALS) 1.0f else 0.5f
        btnViewPrints.alpha = if (currentView == VIEW_PRINTS) 1.0f else 0.5f
    }

    private fun showTerminalsHistory() {
        tvTitle.text = "Cargando historial..."

        ApiClient.getHistory().enqueue(object : Callback<HistoryResponse> {
            override fun onResponse(call: Call<HistoryResponse>, response: Response<HistoryResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.ok) {
                    val entries = (body.data ?: emptyList())
                        .map { r ->
                            HistoryEntry(
                                ts       = r.ts,
                                serial   = r.serial,
                                role     = r.role,
                                action   = r.action,
                                ok       = r.ok,
                                message  = r.message,
                                startTs  = r.startTs
                            )
                        }
                        .sortedByDescending { it.ts }
                    tvTitle.text = "Historial de terminales (${entries.size})"
                    renderTerminalRows(entries)
                } else {
                    loadLocalHistory()
                }
            }

            override fun onFailure(call: Call<HistoryResponse>, t: Throwable) {
                // Sin conexión: fallback al local
                loadLocalHistory()
            }
        })
    }

    private fun loadLocalHistory() {
        val all = HistoryStore.getAll(this)
            .filter { it.ok }
            .sortedByDescending { it.ts }
        tvTitle.text = "Historial local (${all.size})"
        renderTerminalRows(all)
    }

    private fun showPrintsHistory() {
        val today = PrintHistoryStore.getToday(this)

        tvTitle.text = "Impresiones de hoy (${today.size})"
        renderPrintRows(today)
    }

    private fun getActionDisplay(entry: HistoryEntry): String {
        Log.d("HistoryDebug", "Entry: action=${entry.action}, role=${entry.role}, message=${entry.message}, serial=${entry.serial}")

        val result = when (entry.action) {
            "APPS_OK" -> "Apps OK"
            "SIN_APPS" -> "Sin Apps"
            "COMPLETE" -> {
                when {
                    entry.role.contains("QA", ignoreCase = true) -> "QA Aprobado"
                    entry.role.contains("Recovery", ignoreCase = true) -> "Recovery OK"
                    entry.role.contains("Revisión", ignoreCase = true) ||
                    entry.role.contains("Revision", ignoreCase = true) -> "Rev. Inicial OK"
                    else -> "Completado"
                }
            }
            "REJECT" -> entry.message?.substringBefore(" |") ?: "Rechazada"
            "MODIFY" -> {
                val status = entry.message?.replace("→ ", "")?.substringBefore(" (")?.substringBefore(" [") ?: "Modificado"
                when {
                    status.contains("Pendiente de facturación", ignoreCase = true) -> "Pend. Fact."
                    status.contains("Reparación Técnica", ignoreCase = true) -> "Rep. Técnica"
                    status.contains("Irreparable", ignoreCase = true) -> "Irreparable"
                    else -> status
                }
            }
            else -> entry.action
        }

        Log.d("HistoryDebug", "Result for ${entry.serial}: $result")
        return result
    }

    private fun formatDuration(startTs: Long, endTs: Long): String {
        Log.d("HistoryDebug", "formatDuration: startTs=$startTs, endTs=$endTs")
        if (startTs <= 0) return "-"
        val diffMs = endTs - startTs
        if (diffMs < 0) return "-"

        val totalSeconds = diffMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60

        return if (minutes > 0) {
            "${minutes}m ${seconds}s"
        } else {
            "${seconds}s"
        }
    }

    private fun renderTerminalRows(items: List<HistoryEntry>) {
        container.removeAllViews()

        if (items.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No hay terminales procesadas."
                textSize = 14f
                setPadding(8, 16, 8, 16)
            }
            container.addView(empty)
            return
        }

        // Encabezado de columnas
        val header = layoutInflater.inflate(R.layout.row_history, container, false)
        header.findViewById<TextView>(R.id.tvSerial).apply {
            text = "SN"; setTypeface(typeface, android.graphics.Typeface.BOLD); background = null
        }
        header.findViewById<TextView>(R.id.tvAction).apply {
            text = "Accion"; setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        header.findViewById<TextView>(R.id.tvDuration).apply {
            text = "Dur."; setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        header.findViewById<TextView>(R.id.tvTime).apply {
            text = "Hora"; setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        container.addView(header)

        val monthFmt = SimpleDateFormat("MMMM yyyy", Locale("es"))
        val dayKeyFmt  = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val dayLabelFmt = SimpleDateFormat("dd/MM", Locale.getDefault())

        // Agrupar por mes → día → rol
        val byMonth = items.groupBy {
            monthFmt.format(Date(it.ts)).replaceFirstChar { c -> c.uppercase() }
        }
        val multipleMonths = byMonth.size > 1

        byMonth.forEach { (month, monthEntries) ->
            val monthKey = "m_$month"

            // ── Nivel mes (solo si hay más de uno) ──────────────────────────
            val targetDayContainer: LinearLayout
            if (multipleMonths) {
                if (!groupCollapsed.containsKey(monthKey)) groupCollapsed[monthKey] = false
                val isMonthCollapsed = groupCollapsed[monthKey] == true

                val monthBody = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = if (isMonthCollapsed) android.view.View.GONE else android.view.View.VISIBLE
                }

                val tvMonth = TextView(this).apply {
                    text = "${if (isMonthCollapsed) "▶" else "▼"}  $month"
                    textSize = 15f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(8, 20, 8, 6)
                    setTextColor(android.graphics.Color.parseColor("#1565C0"))
                    setOnClickListener {
                        val col = groupCollapsed[monthKey] == true
                        groupCollapsed[monthKey] = !col
                        text = "${if (!col) "▶" else "▼"}  $month"
                        monthBody.visibility = if (!col) android.view.View.GONE else android.view.View.VISIBLE
                    }
                }
                container.addView(tvMonth)
                container.addView(monthBody)
                targetDayContainer = monthBody
            } else {
                targetDayContainer = container
            }

            // ── Nivel día ────────────────────────────────────────────────────
            val byDay = monthEntries.groupBy { dayKeyFmt.format(Date(it.ts)) }

            byDay.forEach { (dayKey, dayEntries) ->
                val dKey = "d_$dayKey"
                if (!groupCollapsed.containsKey(dKey)) groupCollapsed[dKey] = false
                val isDayCollapsed = groupCollapsed[dKey] == true

                val dayLabel = dayLabelFmt.format(Date(dayEntries.first().ts))

                val dayBody = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = if (isDayCollapsed) android.view.View.GONE else android.view.View.VISIBLE
                }

                val tvDay = TextView(this).apply {
                    val indent = if (multipleMonths) "    " else ""
                    text = "$indent${if (isDayCollapsed) "▶" else "▼"}  $dayLabel"
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(if (multipleMonths) 32 else 8, 12, 8, 4)
                    setTextColor(android.graphics.Color.parseColor("#424242"))
                    setOnClickListener {
                        val col = groupCollapsed[dKey] == true
                        groupCollapsed[dKey] = !col
                        val ind = if (multipleMonths) "    " else ""
                        text = "$ind${if (!col) "▶" else "▼"}  $dayLabel"
                        dayBody.visibility = if (!col) android.view.View.GONE else android.view.View.VISIBLE
                    }
                }
                targetDayContainer.addView(tvDay)
                targetDayContainer.addView(dayBody)

                // ── Nivel rol ────────────────────────────────────────────────
                val byRole = dayEntries.groupBy { it.role }

                byRole.forEach { (role, roleEntries) ->
                    val rKey = "r_${dayKey}_$role"
                    if (!groupCollapsed.containsKey(rKey)) groupCollapsed[rKey] = false
                    val isRoleCollapsed = groupCollapsed[rKey] == true

                    val roleHeader = layoutInflater.inflate(R.layout.row_history_group_header, dayBody, false)
                    val tvArrow = roleHeader.findViewById<TextView>(R.id.tvGroupArrow)
                    val tvRole  = roleHeader.findViewById<TextView>(R.id.tvGroupRole)
                    val tvCount = roleHeader.findViewById<TextView>(R.id.tvGroupCount)
                    tvRole.text  = role
                    tvCount.text = "(${roleEntries.size})"
                    tvArrow.text = if (isRoleCollapsed) "▶" else "▼"

                    val rowsContainer = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        visibility = if (isRoleCollapsed) android.view.View.GONE else android.view.View.VISIBLE
                    }

                    roleEntries.forEach { e ->
                        val row = layoutInflater.inflate(R.layout.row_history, rowsContainer, false)
                        row.findViewById<TextView>(R.id.tvSerial).apply {
                            text = e.serial
                            setOnClickListener { copyToClipboard(e.serial) }
                        }
                        row.findViewById<TextView>(R.id.tvAction).text   = getActionDisplay(e)
                        row.findViewById<TextView>(R.id.tvDuration).text = formatDuration(e.startTs, e.ts)
                        row.findViewById<TextView>(R.id.tvTime).text     = timeFmt.format(Date(e.ts))
                        rowsContainer.addView(row)
                    }

                    roleHeader.setOnClickListener {
                        val col = groupCollapsed[rKey] == true
                        groupCollapsed[rKey] = !col
                        tvArrow.text = if (!col) "▶" else "▼"
                        rowsContainer.visibility = if (!col) android.view.View.GONE else android.view.View.VISIBLE
                    }

                    dayBody.addView(roleHeader)
                    dayBody.addView(rowsContainer)
                }
            }
        }
    }

    private fun renderPrintRows(items: List<PrintHistoryStore.PrintEntry>) {
        container.removeAllViews()

        if (items.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Todavia no hay impresiones de hoy."
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
