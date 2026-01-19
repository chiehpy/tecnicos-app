package com.enofir.tecnicos_app.utils

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.TextView

enum class ChipState { ERROR, PROCESSING, OK }

object StatusChip {
    fun apply(chip: TextView, state: ChipState, text: String) {
        chip.text = text
        val bg = GradientDrawable()
        bg.cornerRadius = 40f

        val color = when (state) {
            ChipState.ERROR -> Color.parseColor("#D32F2F")
            ChipState.PROCESSING -> Color.parseColor("#F9A825")
            ChipState.OK -> Color.parseColor("#2E7D32")
        }
        bg.setColor(color)
        chip.background = bg
        chip.setTextColor(Color.WHITE)
    }
}
