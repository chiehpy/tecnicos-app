package com.enofir.tecnicos_app.ui

import android.app.Activity
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.enofir.tecnicos_app.R
import com.enofir.tecnicos_app.core.ApiClient
import com.enofir.tecnicos_app.core.HistoryStore
import com.enofir.tecnicos_app.model.FailureObservationsCatalog
import com.enofir.tecnicos_app.model.HistoryEntry
import com.enofir.tecnicos_app.model.StatusCatalog
import com.enofir.tecnicos_app.model.TerminalEventResponse
import com.enofir.tecnicos_app.model.TerminalLookupResponse
import com.enofir.tecnicos_app.utils.ChipState
import com.enofir.tecnicos_app.utils.StatusChip
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TerminalDetailsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SERIAL = "extra_serial"
        const val EXTRA_ROLE = "extra_role"
        private const val ROLE_REVISION_INICIAL = "Revisión inicial"
    }

    private val options: List<String> = FailureObservationsCatalog.OPTIONS
    private val selected = BooleanArray(options.size) { false }

    private var currentStatus: String? = null

    private fun getSelectedValues(): List<String> {
        val out = mutableListOf<String>()
        for (i in options.indices) if (selected[i]) out.add(options[i])
        return out
    }

    private fun clearAllSelections() {
        for (i in selected.indices) selected[i] = false
    }

    private fun setOnlyNoneSelected() {
        clearAllSelections()
        val noneIndex = options.indexOf(FailureObservationsCatalog.NONE)
        if (noneIndex >= 0) selected[noneIndex] = true
    }

    private fun setDialogChecksFromModel(dialog: AlertDialog) {
        val lv = dialog.listView ?: return
        for (i in selected.indices) {
            lv.setItemChecked(i, selected[i])
        }
    }

    private fun renderFailureObsText(tvFailureObs: TextView) {
        val vals = getSelectedValues()
        tvFailureObs.text = if (vals.isEmpty()) {
            "Observaciones: (sin seleccionar)"
        } else {
            "Observaciones: ${vals.joinToString(", ")}"
        }
    }

    private fun present(s: String?): Boolean = !s.isNullOrBlank() && s.trim() != "-"

    private fun showChangeStatusConfirmation(
        serial: String,
        fromStatus: String,
        newStatus: String,
        chip: TextView,
        tvResult: TextView,
        tvStatusValue: TextView,
        btnChangeState: Button
    ) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar cambio de estado")
            .setMessage("¿Estás seguro que vas a cambiar este terminal de estado \"$fromStatus\" a \"$newStatus\"?")
            .setPositiveButton("Aceptar") { _, _ ->
                executeChangeStatus(serial, newStatus, chip, tvResult, tvStatusValue, btnChangeState)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun executeChangeStatus(
        serial: String,
        newStatus: String,
        chip: TextView,
        tvResult: TextView,
        tvStatusValue: TextView,
        btnChangeState: Button
    ) {
        btnChangeState.isEnabled = false
        StatusChip.apply(chip, ChipState.PROCESSING, "PROCESANDO")
        tvResult.text = "Cambiando estado..."

        ApiClient.modify(serial, newStatus).enqueue(object : Callback<TerminalEventResponse> {

            override fun onResponse(call: Call<TerminalEventResponse>, response: Response<TerminalEventResponse>) {
                val body = response.body()

                if (!response.isSuccessful || body == null) {
                    StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                    val raw = response.errorBody()?.string()?.trim().orEmpty()
                    val msg = if (raw.isNotEmpty()) raw else "Error en respuesta"
                    tvResult.text = "HTTP ${response.code()} - $msg"
                    btnChangeState.isEnabled = true
                    return
                }

                if (body.ok) {
                    StatusChip.apply(chip, ChipState.OK, "OK")
                    tvResult.text = body.message ?: "Estado cambiado correctamente."
                    tvStatusValue.text = newStatus
                    currentStatus = newStatus
                    btnChangeState.isEnabled = true
                } else {
                    StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                    tvResult.text = body.message ?: "Error al cambiar estado."
                    btnChangeState.isEnabled = true
                }
            }

            override fun onFailure(call: Call<TerminalEventResponse>, t: Throwable) {
                StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                tvResult.text = "Falla de conexión: ${t.message}"
                btnChangeState.isEnabled = true
            }
        })
    }

    private fun computeModelLabel(modelRaw: String?, imei2Raw: String?): String {
        val m = modelRaw?.trim().orEmpty()
        if (m == "N910") {
            return if (present(imei2Raw)) "N910 Plus" else "N910 A5"
        }
        return if (m.isNotEmpty()) m else "-"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal_details)

        val chip = findViewById<TextView>(R.id.statusChip)
        val tvSerial = findViewById<TextView>(R.id.tvSerialValue)
        val tvImei = findViewById<TextView>(R.id.tvImeiValue)
        val tvImei2 = findViewById<TextView>(R.id.tvImei2Value)
        val tvModel = findViewById<TextView>(R.id.tvModelValue)
        val tvResult = findViewById<TextView>(R.id.tvResult)

        val imei2Row = findViewById<View>(R.id.imei2Row)
        val tvStatusValue = findViewById<TextView>(R.id.tvStatusValue)
        val tvObservedFailuresValue = findViewById<TextView>(R.id.tvObservedFailuresValue)

        val btnComplete = findViewById<Button>(R.id.btnComplete)
        val btnChangeState = findViewById<Button>(R.id.btnChangeState)

        // Selector (solo Revisión inicial)
        val failureObsContainer = findViewById<View>(R.id.failureObsContainer)
        val btnFailureObs = findViewById<Button>(R.id.btnFailureObs)
        val tvFailureObs = findViewById<TextView>(R.id.tvFailureObs)

        val serial = intent.getStringExtra(EXTRA_SERIAL)?.trim().orEmpty()
        val role = intent.getStringExtra(EXTRA_ROLE)?.trim().orEmpty()
        val isRevisionInicial = role == ROLE_REVISION_INICIAL

        tvSerial.text = serial

        // Defaults UI
        tvImei.text = "-"
        tvImei2.text = "-"
        tvModel.text = "-"
        imei2Row.visibility = View.GONE
        tvStatusValue.text = "-"
        tvObservedFailuresValue.text = "-"

        btnChangeState.isEnabled = false
        btnChangeState.text = "Cambiar estado"

        StatusChip.apply(chip, ChipState.OK, "LISTO")
        tvResult.text = ""

        // Selector multi-choice SOLO para Revisión inicial (input para COMPLETE)
        if (isRevisionInicial) {
            failureObsContainer.visibility = View.VISIBLE
            btnComplete.text = "Finalizar revisión"

            btnFailureObs.isEnabled = true
            renderFailureObsText(tvFailureObs)

            btnFailureObs.setOnClickListener {
                val noneIndex = options.indexOf(FailureObservationsCatalog.NONE)
                val items: Array<CharSequence> = options.toTypedArray()

                val dialog = AlertDialog.Builder(this)
                    .setTitle("Observaciones de falla")
                    .setMultiChoiceItems(items, selected) { dialogInterface: DialogInterface, which: Int, checked: Boolean ->
                        selected[which] = checked

                        if (noneIndex >= 0) {
                            if (which == noneIndex && checked) {
                                setOnlyNoneSelected()
                            } else if (which != noneIndex && checked) {
                                selected[noneIndex] = false
                            }
                        }

                        val alert = dialogInterface as? AlertDialog
                        if (alert != null) setDialogChecksFromModel(alert)
                    }
                    .setPositiveButton("Aceptar") { d, _ ->
                        renderFailureObsText(tvFailureObs)
                        d.dismiss()
                    }
                    .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
                    .create()

                dialog.show()
                setDialogChecksFromModel(dialog)
            }
        } else {
            failureObsContainer.visibility = View.GONE
        }

        // ===== LOOKUP =====
        if (serial.isNotEmpty()) {
            tvResult.text = "Cargando info..."
            ApiClient.lookup(serial).enqueue(object : Callback<TerminalLookupResponse> {

                override fun onResponse(
                    call: Call<TerminalLookupResponse>,
                    response: Response<TerminalLookupResponse>
                ) {
                    val body = response.body()

                    if (!response.isSuccessful || body == null) {
                        tvResult.text = "No se pudo cargar info (HTTP ${response.code()})"
                        return
                    }

                    if (!body.ok || body.data == null) {
                        val msg = body.message?.trim().orEmpty()
                        val code = body.errorCode?.trim().orEmpty()
                        tvResult.text = when {
                            msg.isNotEmpty() && code.isNotEmpty() -> "$msg ($code)"
                            msg.isNotEmpty() -> msg
                            code.isNotEmpty() -> "Lookup error ($code)"
                            else -> "Lookup error"
                        }
                        return
                    }

                    val data = body.data
                    val asset = data.asset
                    val cs = data.cs

                    // Fuente preferida: asset.*, fallback: data.*
                    val imei = asset?.imei?.trim().takeIf { present(it) }
                        ?: data.imei?.trim().takeIf { present(it) }
                    tvImei.text = imei ?: "-"

                    val imei2 = asset?.imei2?.trim().takeIf { present(it) }
                        ?: data.imei2?.trim().takeIf { present(it) }

                    if (imei2 != null) {
                        imei2Row.visibility = View.VISIBLE
                        tvImei2.text = imei2
                    } else {
                        imei2Row.visibility = View.GONE
                    }

                    val modelRaw = asset?.productName?.trim().takeIf { present(it) } ?: "-"
                    tvModel.text = computeModelLabel(modelRaw, imei2)

                    val status = cs?.status?.trim().takeIf { present(it) }
                    tvStatusValue.text = status ?: "-"
                    currentStatus = status

                    val observedFailures = cs?.failureObservations?.trim().takeIf { present(it) }
                    tvObservedFailuresValue.text = observedFailures ?: "-"

                    tvResult.text = ""

                    if (status != null) {
                        btnChangeState.isEnabled = true
                    }
                }

                override fun onFailure(call: Call<TerminalLookupResponse>, t: Throwable) {
                    tvResult.text = "No se pudo cargar info: ${t.message}"
                }
            })
        }
        // ===== FIN LOOKUP =====

        // ===== CAMBIAR ESTADO =====
        btnChangeState.setOnClickListener {
            val fromStatus = currentStatus
            if (fromStatus == null) {
                tvResult.text = "No se puede cambiar: estado actual desconocido."
                return@setOnClickListener
            }

            val statusOptions = StatusCatalog.OPTIONS.filter { it != fromStatus }
            if (statusOptions.isEmpty()) {
                tvResult.text = "No hay estados disponibles para cambiar."
                return@setOnClickListener
            }

            val items: Array<CharSequence> = statusOptions.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("Seleccionar nuevo estado")
                .setItems(items) { _, which ->
                    val newStatus = statusOptions[which]
                    showChangeStatusConfirmation(serial, fromStatus, newStatus, chip, tvResult, tvStatusValue, btnChangeState)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
        // ===== FIN CAMBIAR ESTADO =====

        btnComplete.setOnClickListener {
            if (serial.isEmpty() || role.isEmpty()) {
                StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                tvResult.text = "Faltan datos (serial/role)."
                return@setOnClickListener
            }

            btnComplete.isEnabled = false

            val observations = if (isRevisionInicial) getSelectedValues() else null
            if (isRevisionInicial && observations.isNullOrEmpty()) {
                StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                tvResult.text = "Seleccioná al menos una observación (o 'Sin falla')."
                btnComplete.isEnabled = true
                return@setOnClickListener
            }

            StatusChip.apply(chip, ChipState.PROCESSING, "PROCESANDO")
            tvResult.text = "Enviando COMPLETE..."

            ApiClient.complete(serial, role, observations).enqueue(object : Callback<TerminalEventResponse> {

                override fun onResponse(call: Call<TerminalEventResponse>, response: Response<TerminalEventResponse>) {
                    val body = response.body()

                    if (!response.isSuccessful || body == null) {
                        StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                        val raw = response.errorBody()?.string()?.trim().orEmpty()
                        val msg = if (raw.isNotEmpty()) raw else "Error en respuesta"
                        tvResult.text = "HTTP ${response.code()} - $msg"
                        btnComplete.isEnabled = true
                        return
                    }

                    if (body.ok) {
                        StatusChip.apply(chip, ChipState.OK, "OK")
                        tvResult.text = "Datos enviados correctamente. ${body.message}"

                        HistoryStore.add(
                            this@TerminalDetailsActivity,
                            HistoryEntry(
                                ts = System.currentTimeMillis(),
                                serial = serial,
                                role = role,
                                action = "COMPLETE",
                                ok = true,
                                message = body.message
                            )
                        )

                        setResult(Activity.RESULT_OK)
                        finish()
                    } else {
                        StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                        tvResult.text = body.message ?: "Error"
                        btnComplete.isEnabled = true
                    }
                }

                override fun onFailure(call: Call<TerminalEventResponse>, t: Throwable) {
                    StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                    tvResult.text = "Falla de conexión: ${t.message}"
                    btnComplete.isEnabled = true
                }
            })
        }
    }
}
