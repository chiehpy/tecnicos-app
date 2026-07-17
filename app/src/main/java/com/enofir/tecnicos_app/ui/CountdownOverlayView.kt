package com.enofir.tecnicos_app.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import com.enofir.tecnicos_app.core.ServerTimeSync
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Cuenta regresiva discreta (chica, en la esquina superior izquierda, color naranja)
 * que se actualiza sola cada segundo. Se auto-gestiona con el ciclo de vida de la
 * ventana: arranca al adjuntarse y se detiene al desadjuntarse, así que cualquier
 * Activity la puede agregar con [attachCountdownOverlay] sin manejar Handlers.
 *
 * Usa [ServerTimeSync] (hora corregida contra el header Date del MDW) en lugar de
 * System.currentTimeMillis() directo, para no depender de que el reloj del
 * dispositivo del técnico esté bien configurado.
 *
 * A partir del lunes 20/07/2026 00:00 hs (Argentina) el timer se desactiva solo:
 * se oculta (GONE) y deja de re-programar el tick, así no queda "pegado" en
 * 00d 00h 00m 00s para siempre.
 */
class CountdownOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    private val tick = object : Runnable {
        override fun run() {
            if (updateText()) {
                postDelayed(this, TICK_MS)
            }
        }
    }

    init {
        setTextColor(Color.parseColor("#FF6600"))
        textSize = 10f
        setPadding(12, 12, 12, 12)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (updateText()) {
            postDelayed(tick, TICK_MS)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    /** Actualiza el texto (o se oculta si ya pasó DISABLE_AFTER_MILLIS). Devuelve si hay que seguir tickeando. */
    private fun updateText(): Boolean {
        val now = ServerTimeSync.nowMillis()
        if (now >= DISABLE_AFTER_MILLIS) {
            visibility = View.GONE
            return false
        }
        visibility = View.VISIBLE
        val remainingMs = (DEADLINE_MILLIS - now).coerceAtLeast(0)
        val days = TimeUnit.MILLISECONDS.toDays(remainingMs)
        val hours = TimeUnit.MILLISECONDS.toHours(remainingMs) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMs) % 60
        text = String.format(Locale.getDefault(), "%02dd %02dh %02dm %02ds", days, hours, minutes, seconds)
        return true
    }

    companion object {
        private const val TICK_MS = 1_000L

        // Domingo 19/07/2026 16:00 hs (Argentina, UTC-3) = 2026-07-19T19:00:00Z
        private const val DEADLINE_MILLIS = 1784487600000L

        // Lunes 20/07/2026 00:00 hs (Argentina, UTC-3) = 2026-07-20T03:00:00Z
        private const val DISABLE_AFTER_MILLIS = 1784516400000L
    }
}

/**
 * Agrega el [CountdownOverlayView] al root de la Activity (arriba a la izquierda,
 * flotando sobre el contenido). Pensada para llamarse una vez, después de
 * setContentView.
 */
fun Activity.attachCountdownOverlay() {
    val root = findViewById<ViewGroup>(android.R.id.content) ?: return
    val overlay = CountdownOverlayView(this)
    val params = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        Gravity.TOP or Gravity.START
    )
    root.addView(overlay, params)
}
