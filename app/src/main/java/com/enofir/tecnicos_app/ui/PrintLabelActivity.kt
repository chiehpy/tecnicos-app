package com.enofir.tecnicos_app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.enofir.tecnicos_app.R
import com.enofir.tecnicos_app.core.ApiClient
import com.enofir.tecnicos_app.core.PrintConfigStore
import com.enofir.tecnicos_app.core.PrintHistoryStore
import com.enofir.tecnicos_app.model.PrintLabelResponse
import com.enofir.tecnicos_app.sdk.ScanActivity
import com.enofir.tecnicos_app.utils.ChipState
import com.enofir.tecnicos_app.utils.StatusChip
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.OutputStream
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Pantalla de impresión directa de etiquetas (solo QA).
 * No requiere Case existente, solo el número de serie.
 */
class PrintLabelActivity : BaseActivity() {

    companion object {
        private const val PRINTER_IP = "192.168.204.10"
        private const val PRINTER_PORT = 9100
    }

    private lateinit var etSerial: EditText
    private lateinit var btnScan: Button
    private lateinit var btnPrint: Button
    private lateinit var btnPrintConfig: Button
    private lateinit var tvResult: TextView
    private lateinit var chip: TextView

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                val code = res.data?.getStringExtra(ScanActivity.EXTRA_RESULT)?.trim()
                if (!code.isNullOrEmpty()) etSerial.setText(code)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_print_label)

        chip = findViewById(R.id.statusChip)
        etSerial = findViewById(R.id.etSerial)
        btnScan = findViewById(R.id.btnScan)
        btnPrint = findViewById(R.id.btnPrint)
        btnPrintConfig = findViewById(R.id.btnPrintConfig)
        tvResult = findViewById(R.id.tvResult)

        StatusChip.apply(chip, ChipState.OK, "LISTO")

        // Escanear
        btnScan.setOnClickListener {
            scanLauncher.launch(Intent(this, ScanActivity::class.java))
        }

        // Imprimir
        btnPrint.setOnClickListener {
            val serial = etSerial.text.toString().trim()

            if (serial.isEmpty()) {
                StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                tvResult.text = "Ingresá un número de serie"
                return@setOnClickListener
            }

            printLabel(serial)
        }

        // Configurar impresión
        btnPrintConfig.setOnClickListener {
            showPrintConfigDialog()
        }
    }

    private fun printLabel(serial: String) {
        btnPrint.isEnabled = false
        StatusChip.apply(chip, ChipState.PROCESSING, "PROCESANDO")
        tvResult.text = "Obteniendo etiqueta..."

        val lsMm = PrintConfigStore.getLsMmForApi(this)

        ApiClient.printLabel(serial, lsMm = lsMm).enqueue(object : Callback<PrintLabelResponse> {

            override fun onResponse(call: Call<PrintLabelResponse>, response: Response<PrintLabelResponse>) {
                val body = response.body()

                if (!response.isSuccessful || body == null) {
                    StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                    val raw = response.errorBody()?.string()?.trim().orEmpty()
                    val msg = if (raw.isNotEmpty()) raw else "Error en respuesta"
                    tvResult.text = "HTTP ${response.code()} - $msg"
                    btnPrint.isEnabled = true
                    return
                }

                if (!body.ok || body.zpl.isNullOrEmpty()) {
                    StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                    tvResult.text = body.message ?: "No se pudo generar etiqueta"
                    btnPrint.isEnabled = true
                    return
                }

                tvResult.text = "Enviando a impresora..."
                sendZplToPrinter(serial, body.zpl)
            }

            override fun onFailure(call: Call<PrintLabelResponse>, t: Throwable) {
                StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                tvResult.text = "Falla de conexión: ${t.message}"
                btnPrint.isEnabled = true
            }
        })
    }

    private fun sendZplToPrinter(serial: String, zpl: String) {
        thread {
            var socket: Socket? = null
            var output: OutputStream? = null
            try {
                socket = Socket(PRINTER_IP, PRINTER_PORT)
                output = socket.getOutputStream()
                output.write(zpl.toByteArray(Charsets.UTF_8))
                output.flush()

                runOnUiThread {
                    // Guardar en historial
                    PrintHistoryStore.add(this, serial)

                    StatusChip.apply(chip, ChipState.OK, "OK")
                    tvResult.text = "Etiqueta enviada a impresora"
                    btnPrint.isEnabled = true
                    etSerial.setText("")
                    etSerial.requestFocus()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                    tvResult.text = "Error impresora: ${e.message}"
                    btnPrint.isEnabled = true
                }
            } finally {
                try { output?.close() } catch (_: Exception) {}
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    private fun showPrintConfigDialog() {
        val currentLsMm = PrintConfigStore.getLsMm(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val tvShiftLabel = TextView(this).apply {
            val suffix = if (currentLsMm == PrintConfigStore.DEFAULT_LS_MM) " (predeterminado)" else ""
            text = "Mover imagen: ${PrintConfigStore.formatLsMmForDisplay(currentLsMm)}$suffix"
            textSize = 16f
        }
        layout.addView(tvShiftLabel)

        val maxShift = PrintConfigStore.MAX_SHIFT_MM
        val seekShift = SeekBar(this).apply {
            max = maxShift * 2
            progress = currentLsMm + maxShift
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress - maxShift
                    val suffix = if (value == PrintConfigStore.DEFAULT_LS_MM) " (predeterminado)" else ""
                    tvShiftLabel.text = "Mover imagen: ${PrintConfigStore.formatLsMmForDisplay(value)}$suffix"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        layout.addView(seekShift)

        val tvNote = TextView(this).apply {
            text = "Izquierda / Derecha: desplaza la etiqueta horizontalmente"
            textSize = 12f
            setPadding(0, 16, 0, 0)
        }
        layout.addView(tvNote)

        AlertDialog.Builder(this)
            .setTitle("Configurar impresión")
            .setView(layout)
            .setPositiveButton("Guardar") { _, _ ->
                val newLsMm = seekShift.progress - maxShift
                PrintConfigStore.setLsMm(this, newLsMm)
            }
            .setNeutralButton("Restablecer") { _, _ ->
                PrintConfigStore.resetToDefaults(this)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
