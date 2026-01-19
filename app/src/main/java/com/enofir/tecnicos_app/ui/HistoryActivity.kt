package com.enofir.tecnicos_app.ui

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.enofir.tecnicos_app.R
import com.enofir.tecnicos_app.core.HistoryStore
import com.enofir.tecnicos_app.model.HistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var container: LinearLayout
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        tvTitle = findViewById(R.id.tvHistoryTitle)
        container = findViewById(R.id.historyContainer)

        val btnBack = findViewById<Button>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()

        val today = HistoryStore.getToday(this)
            .filter { it.action == "COMPLETE" && it.ok } // “terminales hechos en el día”

        tvTitle.text = "Historial de hoy (${today.size})"
        renderRows(today)
    }

    private fun renderRows(items: List<HistoryEntry>) {
        container.removeAllViews()

        if (items.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Todavía no hay terminales finalizadas hoy."
                textSize = 14f
            }
            container.addView(empty)
            return
        }

        // Encabezado tipo tabla
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
}
