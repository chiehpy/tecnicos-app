package com.enofir.tecnicos_app.core

import android.content.Context
import com.enofir.tecnicos_app.model.HistoryEntry
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object HistoryStore {

    private const val PREFS = "tecnicos_app_history"
    private const val KEY_ITEMS = "items"

    fun add(context: Context, entry: HistoryEntry) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val array = JSONArray(prefs.getString(KEY_ITEMS, "[]"))

        val obj = JSONObject().apply {
            put("ts", entry.ts)
            put("serial", entry.serial)
            put("role", entry.role)
            put("action", entry.action)
            put("ok", entry.ok)
            put("message", entry.message)
        }

        array.put(obj)

        prefs.edit()
            .putString(KEY_ITEMS, array.toString())
            .apply()
    }

    fun getAll(context: Context): List<HistoryEntry> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString(KEY_ITEMS, "[]"))

        val list = mutableListOf<HistoryEntry>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                HistoryEntry(
                    ts = o.getLong("ts"),
                    serial = o.getString("serial"),
                    role = o.getString("role"),
                    action = o.getString("action"),
                    ok = o.getBoolean("ok"),
                    message = o.optString("message")
                )
            )
        }
        return list
    }

    fun getToday(context: Context): List<HistoryEntry> {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        return getAll(context).filter {
            SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(it.ts)) == today
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
