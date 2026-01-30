package com.enofir.tecnicos_app.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Almacena el historial de impresiones de etiquetas.
 */
object PrintHistoryStore {

    private const val PREFS = "print_history"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 100

    data class PrintEntry(
        val ts: Long,
        val serial: String
    )

    fun add(context: Context, serial: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString(KEY_ITEMS, "[]"))

        val obj = JSONObject().apply {
            put("ts", System.currentTimeMillis())
            put("serial", serial)
        }

        array.put(obj)

        // Limitar cantidad de items
        while (array.length() > MAX_ITEMS) {
            array.remove(0)
        }

        prefs.edit()
            .putString(KEY_ITEMS, array.toString())
            .apply()
    }

    fun getAll(context: Context): List<PrintEntry> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString(KEY_ITEMS, "[]"))

        val list = mutableListOf<PrintEntry>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                PrintEntry(
                    ts = o.getLong("ts"),
                    serial = o.getString("serial")
                )
            )
        }
        return list.reversed() // Más recientes primero
    }

    fun getToday(context: Context): List<PrintEntry> {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        return getAll(context).filter {
            SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(it.ts)) == today
        }
    }

    fun getTodaySerials(context: Context): List<String> {
        return getToday(context).map { it.serial }
    }

    fun getTodaySerialsAsText(context: Context): String {
        return getTodaySerials(context).joinToString("\n")
    }

    fun getTodayCount(context: Context): Int {
        return getToday(context).size
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
