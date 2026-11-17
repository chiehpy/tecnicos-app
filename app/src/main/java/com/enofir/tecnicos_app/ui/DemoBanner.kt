package com.enofir.tecnicos_app.ui

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

/** Cartel fijo de la build DEMO, para que nadie la confunda con producción. */
object DemoBanner {
    fun attach(activity: Activity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val banner = TextView(activity).apply {
            text = "DEMO - datos de ejemplo"
            setBackgroundColor(Color.parseColor("#C62828"))
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
            isClickable = false
        }
        root.addView(
            banner,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )
    }
}
