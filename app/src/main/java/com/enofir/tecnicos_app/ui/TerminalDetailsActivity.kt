package com.enofir.tecnicos_app.ui

import android.app.Activity
import android.content.DialogInterface
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.enofir.tecnicos_app.R
import android.widget.LinearLayout
import android.widget.SeekBar
import com.enofir.tecnicos_app.core.ApiClient
import com.enofir.tecnicos_app.core.HistoryStore
import com.enofir.tecnicos_app.core.PrintConfigStore
import com.enofir.tecnicos_app.core.SessionManager
import com.enofir.tecnicos_app.model.FailureObservationsCatalog
import com.enofir.tecnicos_app.model.HistoryEntry
import com.enofir.tecnicos_app.model.PrintLabelResponse
import com.enofir.tecnicos_app.model.StatusCatalog
import com.enofir.tecnicos_app.model.TerminalEventResponse
import com.enofir.tecnicos_app.model.TerminalLookupResponse
import java.io.OutputStream
import java.net.Socket
import kotlin.concurrent.thread
import com.enofir.tecnicos_app.utils.ChipState
import com.enofir.tecnicos_app.utils.IrreparableChecker
import com.enofir.tecnicos_app.utils.StatusChip
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TerminalDetailsActivity : BaseActivity() {

    companion object {
        const val EXTRA_SERIAL = "extra_serial"
        const val EXTRA_ROLE = "extra_role"
        private const val ROLE_REVISION_INICIAL = "Revisión inicial"
        private const val ROLE_QA = "QA"
        private const val ROLE_RECOVERY = "Recovery"

        private const val STATUS_IRREPARABLE = "Irreparable"
        private const val STATUS_REPARACION_TECNICA = "Reparación Técnica"
        private const val STATUS_PENDIENTE_FACTURACION = "Pendiente de facturación"

        // Impresora Zebra
        private const val PRINTER_IP = "192.168.0.10"
        private const val PRINTER_PORT = 9100
    }

    // Failure observations (Revisión inicial)
    private val failureOptions: List<String> = FailureObservationsCatalog.OPTIONS
    private val failureSelected: BooleanArray = BooleanArray(failureOptions.size) { false }

    // Catálogo QA (definitivo)
    private val qaOptions: List<String> = listOf(
        "Falta de limpieza: Carcasa posterior",
        "Falta de limpieza: Carcasa frontal",
        "Falta de limpieza: Tapa de bateria",
        "Falta de limpieza: Tapa de impresora",
        "Daño estetico: Carcasa posterior",
        "Daño estetico: Carcasa frontal",
        "Daño estetico: Dientes Impresora",
        "Daño estetico: Tapa de bateria",
        "Daño estetico: Tapa de impresora",
        "Daño estetico: Carcasa frontal gastada (amarilla)",
        "Daño estetico: Carcasa posterior gastada (amarilla)",
        "Daño estetico: Tapa de bateria gastada (amarilla)",
        "Daño estetico: Tapa de impresora (amarilla)",
        "Faltan tornillos",
        "Tamper",
        "Camara trasera",
        "Camara frontal",
        "Sin audio"
    )

    private val qaSelected: BooleanArray = BooleanArray(qaOptions.size) { false }

    // Catálogo de repuestos recuperados (Recovery)
    private val recoveredPartsOptions: List<String> = listOf(
        "Carcasa frontal",
        "Carcasa posterior",
        "Bateria",
        "Tapa bateria",
        "Tapa impresora",
        "Rodillo",
        "Display",
        "Impresora",
        "Pila",
        "Pila IO",
        "Placa IO",
        "Camara delantera",
        "Camara trasera",
        "Lectora magnetica"
    )
    private val recoveredPartsSelected: BooleanArray = BooleanArray(recoveredPartsOptions.size) { false }

    // Substatus UI -> valor real MDW
    private data class SubOpt(val label: String, val mdwValue: String)

    private val reparacionSubstatusOptions: List<SubOpt> = listOf(
        SubOpt("Reparación", "Reparación"),
        SubOpt("Programación", "Carga de firmware + Inyección")
    )

    private var currentStatus: String? = null
    private var csId: String? = null
    private var currentAccountName: String? = null

    private fun present(s: String?): Boolean =
        !s.isNullOrBlank() && s.trim() != "-" && s.trim().lowercase() != "null"

    private fun computeModelLabel(modelRaw: String?, imei2Raw: String?): String {
        val m = modelRaw?.trim().orEmpty()
        if (m == "N910") {
            return if (present(imei2Raw)) "N910 Plus" else "N910 A5"
        }
        return if (m.isNotEmpty()) m else "-"
    }

    // ===== technicianName desde JWT (OBLIGATORIO para Recovery/MODIFY) =====

    private fun base64UrlDecodeToString(input: String): String {
        var s = input.replace('-', '+').replace('_', '/')
        val mod = s.length % 4
        if (mod != 0) s += "=".repeat(4 - mod)
        val decoded = Base64.decode(s, Base64.DEFAULT)
        return String(decoded, Charsets.UTF_8)
    }

    private fun getTechnicianNameFromJwt(): String? {
        return try {
            val token = SessionManager(applicationContext).getToken()?.trim().orEmpty()
            if (token.isEmpty()) return null

            val parts = token.split(".")
            if (parts.size < 2) return null

            val payloadJson = base64UrlDecodeToString(parts[1])
            val obj = JSONObject(payloadJson)
            obj.optString("technician_name", null)?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun getSelectedFailureValues(): List<String> {
        val out = mutableListOf<String>()
        for (i in failureOptions.indices) if (failureSelected[i]) out.add(failureOptions[i])
        return out
    }

    private fun clearAllFailureSelections() {
        for (i in failureSelected.indices) failureSelected[i] = false
    }

    private fun setOnlyNoneFailureSelected() {
        clearAllFailureSelections()
        val noneIndex = failureOptions.indexOf(FailureObservationsCatalog.NONE)
        if (noneIndex >= 0) failureSelected[noneIndex] = true
    }

    private fun setDialogChecksFromModel(dialog: AlertDialog, model: BooleanArray) {
        val lv = dialog.listView ?: return
        for (i in model.indices) lv.setItemChecked(i, model[i])
    }

    private fun renderFailureObsText(tvFailureObs: TextView, btnIrreparable: Button? = null) {
        val vals = getSelectedFailureValues()
        tvFailureObs.text = if (vals.isEmpty()) {
            "Observaciones: (sin seleccionar)"
        } else {
            "Observaciones: ${vals.joinToString(", ")}"
        }

        btnIrreparable?.let { btn ->
            val canBeIrreparable = IrreparableChecker.isIrreparable(vals, currentAccountName)
            btn.isEnabled = canBeIrreparable
            btn.alpha = if (canBeIrreparable) 1.0f else 0.5f
        }
    }

    private fun getSelectedRecoveredParts(): List<String> {
        val out = mutableListOf<String>()
        for (i in recoveredPartsOptions.indices) if (recoveredPartsSelected[i]) out.add(recoveredPartsOptions[i])
        return out
    }

    private fun renderRecoveredPartsText(tvRecoveredParts: TextView) {
        val vals = getSelectedRecoveredParts()
        tvRecoveredParts.text = if (vals.isEmpty()) {
            "Repuestos: (sin seleccionar)"
        } else {
            "Repuestos: ${vals.joinToString(", ")}"
        }
    }

    /**
     * Envía el ZPL a la impresora Zebra por TCP socket.
     */
    private fun sendZplToPrinter(
        zpl: String,
        chip: TextView,
        tvResult: TextView,
        btnPrintLabel: Button
    ) {
        thread {
            var socket: Socket? = null
            var output: OutputStream? = null
            try {
                socket = Socket(PRINTER_IP, PRINTER_PORT)
                output = socket.getOutputStream()
                output.write(zpl.toByteArray(Charsets.UTF_8))
                output.flush()

                runOnUiThread {
                    StatusChip.apply(chip, ChipState.OK, "OK")
                    tvResult.text = "Etiqueta enviada a impresora"
                    btnPrintLabel.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                    tvResult.text = "Error impresora: ${e.message}"
                    btnPrintLabel.isEnabled = true
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

    private fun showSubstatusDialogForReparacionTecnica(onSelected: (mdwSubstatus: String, uiLabel: String) -> Unit) {
        val labels = reparacionSubstatusOptions.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Seleccionar subestado")
            .setItems(labels) { _, which ->
                val opt = reparacionSubstatusOptions[which]
                onSelected(opt.mdwValue, opt.label)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showFailureObsDialogForIrreparable(onConfirm: (failureObs: List<String>) -> Unit) {
        val noneIndex = failureOptions.indexOf(FailureObservationsCatalog.NONE)
        val items: Array<CharSequence> = failureOptions.toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Seleccionar fallas (obligatorio)")
            .setMultiChoiceItems(items, failureSelected) { dialogInterface: DialogInterface, which: Int, checked: Boolean ->
                failureSelected[which] = checked

                if (noneIndex >= 0) {
                    if (which == noneIndex && checked) {
                        setOnlyNoneFailureSelected()
                    } else if (which != noneIndex && checked) {
                        failureSelected[noneIndex] = false
                    }
                }

                val alert = dialogInterface as? AlertDialog
                if (alert != null) setDialogChecksFromModel(alert, failureSelected)
            }
            .setPositiveButton("Continuar") { d, _ ->
                d.dismiss()
                val vals = getSelectedFailureValues()
                if (vals.isEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Faltan fallas")
                        .setMessage("Seleccioná al menos una falla (o 'Sin falla').")
                        .setPositiveButton("OK", null)
                        .show()
                    return@setPositiveButton
                }
                onConfirm(vals)
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()
        setDialogChecksFromModel(dialog, failureSelected)
    }

    private fun showQaRejectDialog(onConfirm: (qaObsStringForMdw: String, qaObsStringForUi: String) -> Unit) {
        for (i in qaSelected.indices) qaSelected[i] = false

        val items: Array<CharSequence> = qaOptions.toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Observaciones QA (obligatorio)")
            .setMultiChoiceItems(items, qaSelected) { dialogInterface: DialogInterface, which: Int, checked: Boolean ->
                qaSelected[which] = checked
                val alert = dialogInterface as? AlertDialog
                if (alert != null) setDialogChecksFromModel(alert, qaSelected)
            }
            .setPositiveButton("Continuar") { d, _ ->
                d.dismiss()

                val selectedVals = mutableListOf<String>()
                for (i in qaSelected.indices) {
                    if (qaSelected[i]) selectedVals.add(qaOptions[i])
                }

                if (selectedVals.isEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Faltan observaciones")
                        .setMessage("Seleccioná al menos una observación QA.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@setPositiveButton
                }

                val ui = selectedVals.joinToString(", ")
                val mdw = selectedVals.joinToString(";")
                onConfirm(mdw, ui)
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()
        setDialogChecksFromModel(dialog, qaSelected)
    }

    private fun executeRejectQa(
        serial: String,
        qaObsStringForMdw: String,
        chip: TextView,
        tvResult: TextView,
        btnChangeState: Button,
        onSuccess: () -> Unit
    ) {
        btnChangeState.isEnabled = false
        StatusChip.apply(chip, ChipState.PROCESSING, "PROCESANDO")
        tvResult.text = "Enviando REJECT (QA)..."

        ApiClient.reject(serial, ROLE_QA, qaObsStringForMdw).enqueue(object : Callback<TerminalEventResponse> {

            override fun onResponse(call: Call<TerminalEventResponse>, response: Response<TerminalEventResponse>) {
                val body = response.body()

                if (!response.isSuccessful || body == null) {
                    StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                    val raw = response.errorBody()?.string()?.trim().orEmpty()
                    val msg = if (raw.isNotEmpty()) raw else "Error en respuesta"
                    tvResult.text = "REJECT falló: HTTP ${response.code()} - $msg"
                    btnChangeState.isEnabled = true
                    return
                }

                if (body.ok) {
                    StatusChip.apply(chip, ChipState.OK, "OK")
                    tvResult.text = body.message ?: "REJECT OK"
                    onSuccess()
                } else {
                    StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                    tvResult.text = body.message ?: "REJECT falló"
                    btnChangeState.isEnabled = true
                }
            }

            override fun onFailure(call: Call<TerminalEventResponse>, t: Throwable) {
                StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                tvResult.text = "REJECT falló: ${t.message}"
                btnChangeState.isEnabled = true
            }
        })
    }

    /**
     * MODIFY wrapper (named args para evitar mismatch)
     * technicianNameRequired=true => si no se puede obtener del JWT, no envía.
     */
    private fun executeChangeStatus(
        serial: String,
        newStatus: String,
        chip: TextView,
        tvResult: TextView,
        tvStatusValue: TextView,
        btnChangeState: Button,
        finishOnSuccess: Boolean = false,
        substatus: String? = null,
        failureObservations: List<String>? = null,
        recoveredParts: String? = null,
        technicianNameRequired: Boolean = false,
        onOk: (() -> Unit)? = null
    ) {
        btnChangeState.isEnabled = false
        StatusChip.apply(chip, ChipState.PROCESSING, "PROCESANDO")
        tvResult.text = "Cambiando estado..."

        val techName = getTechnicianNameFromJwt()

        if (technicianNameRequired && techName.isNullOrBlank()) {
            StatusChip.apply(chip, ChipState.ERROR, "ERROR")
            tvResult.text = "No se pudo obtener technicianName. Re-logueá e intentá de nuevo."
            btnChangeState.isEnabled = true
            return
        }

        ApiClient.modify(
            serial = serial,
            targetStatus = newStatus,
            targetSubstatus = substatus,
            technicianName = techName, // lo mandamos siempre si existe
            recoveredParts = recoveredParts,
            failureObservations = failureObservations
        ).enqueue(object : Callback<TerminalEventResponse> {

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

                    // Guardar en historial
                    val actionDesc = buildString {
                        append("→ $newStatus")
                        if (!failureObservations.isNullOrEmpty()) {
                            append(" (${failureObservations.joinToString(", ")})")
                        }
                        if (!recoveredParts.isNullOrBlank()) {
                            append(" [${recoveredParts}]")
                        }
                    }
                    HistoryStore.add(
                        this@TerminalDetailsActivity,
                        HistoryEntry(
                            ts = System.currentTimeMillis(),
                            serial = serial,
                            role = intent.getStringExtra(EXTRA_ROLE)?.trim().orEmpty(),
                            action = "MODIFY",
                            ok = true,
                            message = actionDesc
                        )
                    )

                    if (finishOnSuccess) {
                        setResult(Activity.RESULT_OK)
                        finish()
                    } else {
                        if (onOk == null) btnChangeState.isEnabled = true
                        onOk?.invoke()
                    }
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

        val qaObsRow = findViewById<View>(R.id.qaObsRow)
        val tvQaObservationsValue = findViewById<TextView>(R.id.tvQaObservationsValue)

        val qaRejectCountRow = findViewById<View>(R.id.qaRejectCountRow)
        val tvQaRejectCountValue = findViewById<TextView>(R.id.tvQaRejectCountValue)

        // Nuevos campos de técnicos
        val finalDiagnosisRow = findViewById<View>(R.id.finalDiagnosisRow)
        val tvFinalDiagnosisValue = findViewById<TextView>(R.id.tvFinalDiagnosisValue)
        val receivedByRow = findViewById<View>(R.id.receivedByRow)
        val tvReceivedByValue = findViewById<TextView>(R.id.tvReceivedByValue)
        val pretestByRow = findViewById<View>(R.id.pretestByRow)
        val tvPretestByValue = findViewById<TextView>(R.id.tvPretestByValue)
        val cleanedByRow = findViewById<View>(R.id.cleanedByRow)
        val tvCleanedByValue = findViewById<TextView>(R.id.tvCleanedByValue)
        val repairTechnicianRow = findViewById<View>(R.id.repairTechnicianRow)
        val tvRepairTechnicianValue = findViewById<TextView>(R.id.tvRepairTechnicianValue)
        val programmedByRow = findViewById<View>(R.id.programmedByRow)
        val tvProgrammedByValue = findViewById<TextView>(R.id.tvProgrammedByValue)
        val batteryTestedByRow = findViewById<View>(R.id.batteryTestedByRow)
        val tvBatteryTestedByValue = findViewById<TextView>(R.id.tvBatteryTestedByValue)
        val qaPerformedByRow = findViewById<View>(R.id.qaPerformedByRow)
        val tvQaPerformedByValue = findViewById<TextView>(R.id.tvQaPerformedByValue)
        val dismantledByRow = findViewById<View>(R.id.dismantledByRow)
        val tvDismantledByValue = findViewById<TextView>(R.id.tvDismantledByValue)

        val btnComplete = findViewById<Button>(R.id.btnComplete)
        val btnChangeState = findViewById<Button>(R.id.btnChangeState)
        val btnPrintLabel = findViewById<Button>(R.id.btnPrintLabel)
        val btnPrintConfig = findViewById<Button>(R.id.btnPrintConfig)

        val failureObsContainer = findViewById<View>(R.id.failureObsContainer)
        val btnFailureObs = findViewById<Button>(R.id.btnFailureObs)
        val tvFailureObs = findViewById<TextView>(R.id.tvFailureObs)

        val serial = intent.getStringExtra(EXTRA_SERIAL)?.trim().orEmpty()
        val role = intent.getStringExtra(EXTRA_ROLE)?.trim().orEmpty()
        val isRevisionInicial = role == ROLE_REVISION_INICIAL
        val isQa = role == ROLE_QA
        val isRecovery = role == ROLE_RECOVERY

        // Recovery views
        val recoveryContainer = findViewById<View>(R.id.recoveryContainer)
        val btnSelectRecoveredParts = findViewById<Button>(R.id.btnSelectRecoveredParts)
        val tvRecoveredParts = findViewById<TextView>(R.id.tvRecoveredParts)

        tvSerial.text = serial

        // Defaults UI
        tvImei.text = "-"
        tvImei2.text = "-"
        tvModel.text = "-"
        imei2Row.visibility = View.GONE
        tvStatusValue.text = "-"
        tvObservedFailuresValue.text = "-"

        qaObsRow.visibility = View.GONE
        tvQaObservationsValue.text = "-"

        qaRejectCountRow.visibility = View.GONE
        tvQaRejectCountValue.text = "-"

        // Ocultar nuevos campos inicialmente
        finalDiagnosisRow.visibility = View.GONE
        receivedByRow.visibility = View.GONE
        pretestByRow.visibility = View.GONE
        cleanedByRow.visibility = View.GONE
        repairTechnicianRow.visibility = View.GONE
        programmedByRow.visibility = View.GONE
        batteryTestedByRow.visibility = View.GONE
        qaPerformedByRow.visibility = View.GONE
        dismantledByRow.visibility = View.GONE

        btnChangeState.isEnabled = false
        btnChangeState.text = "Cambiar estado"

        StatusChip.apply(chip, ChipState.OK, "LISTO")
        tvResult.text = ""

        if (isRevisionInicial) {
            failureObsContainer.visibility = View.VISIBLE
            btnComplete.text = "Enviar a reparación técnica"
            btnChangeState.text = "Irreparable"

            btnChangeState.isEnabled = false
            btnChangeState.alpha = 0.5f

            btnFailureObs.isEnabled = true
            renderFailureObsText(tvFailureObs, btnChangeState)

            btnFailureObs.setOnClickListener {
                val noneIndex = failureOptions.indexOf(FailureObservationsCatalog.NONE)
                val items: Array<CharSequence> = failureOptions.toTypedArray()

                val dialog = AlertDialog.Builder(this)
                    .setTitle("Fallas encontradas")
                    .setMultiChoiceItems(items, failureSelected) { dialogInterface: DialogInterface, which: Int, checked: Boolean ->
                        failureSelected[which] = checked

                        if (noneIndex >= 0) {
                            if (which == noneIndex && checked) {
                                setOnlyNoneFailureSelected()
                            } else if (which != noneIndex && checked) {
                                failureSelected[noneIndex] = false
                            }
                        }

                        val alert = dialogInterface as? AlertDialog
                        if (alert != null) setDialogChecksFromModel(alert, failureSelected)
                    }
                    .setPositiveButton("Aceptar") { d, _ ->
                        renderFailureObsText(tvFailureObs, btnChangeState)
                        d.dismiss()
                    }
                    .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
                    .create()

                dialog.show()
                setDialogChecksFromModel(dialog, failureSelected)
            }
        } else if (isQa) {
            failureObsContainer.visibility = View.GONE
            recoveryContainer.visibility = View.GONE
            btnComplete.text = "APROBAR TERMINAL"
            btnChangeState.text = "RECHAZAR TERMINAL"
            // Botones de impresión se muestran solo para N910 (ver lookup)

            // Imprimir etiqueta ZPL
            btnPrintLabel.setOnClickListener {
                btnPrintLabel.isEnabled = false
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
                            btnPrintLabel.isEnabled = true
                            return
                        }

                        if (!body.ok || body.zpl.isNullOrEmpty()) {
                            StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                            tvResult.text = body.message ?: "No se pudo generar etiqueta"
                            btnPrintLabel.isEnabled = true
                            return
                        }

                        tvResult.text = "Enviando a impresora..."
                        sendZplToPrinter(body.zpl, chip, tvResult, btnPrintLabel)
                    }

                    override fun onFailure(call: Call<PrintLabelResponse>, t: Throwable) {
                        StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                        tvResult.text = "Falla de conexión: ${t.message}"
                        btnPrintLabel.isEnabled = true
                    }
                })
            }

            // Configurar impresión
            btnPrintConfig.setOnClickListener {
                showPrintConfigDialog()
            }
        } else if (isRecovery) {
            failureObsContainer.visibility = View.GONE
            recoveryContainer.visibility = View.VISIBLE
            btnComplete.text = "PROCESAR TERMINAL"
            btnChangeState.text = "REVERTIR ESTADO"

            // Solo selecciona y muestra. (Camino A: se envía en MODIFY al completar)
            btnSelectRecoveredParts.setOnClickListener {
                val items: Array<CharSequence> = recoveredPartsOptions.toTypedArray()

                val dialog = AlertDialog.Builder(this)
                    .setTitle("Seleccionar repuestos recuperados")
                    .setMultiChoiceItems(items, recoveredPartsSelected) { dialogInterface: DialogInterface, which: Int, checked: Boolean ->
                        recoveredPartsSelected[which] = checked
                        val alert = dialogInterface as? AlertDialog
                        if (alert != null) setDialogChecksFromModel(alert, recoveredPartsSelected)
                    }
                    .setPositiveButton("Aceptar") { d, _ ->
                        d.dismiss()
                        renderRecoveredPartsText(tvRecoveredParts)
                    }
                    .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
                    .create()

                dialog.show()
                setDialogChecksFromModel(dialog, recoveredPartsSelected)
            }
        } else {
            failureObsContainer.visibility = View.GONE
            recoveryContainer.visibility = View.GONE
            btnComplete.text = "FINALIZAR PROCESO"
            btnChangeState.text = "CAMBIAR ESTADO"
        }

        // ===== LOOKUP =====
        if (serial.isNotEmpty()) {
            tvResult.text = "Cargando info..."
            ApiClient.lookup(serial).enqueue(object : Callback<TerminalLookupResponse> {

                override fun onResponse(call: Call<TerminalLookupResponse>, response: Response<TerminalLookupResponse>) {
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

                    // Guardar accountName para IrreparableChecker
                    currentAccountName = cs?.accountName?.trim()

                    // Mostrar botones de impresión solo para N910 en rol QA
                    if (isQa && modelRaw.contains("N910", ignoreCase = true)) {
                        btnPrintLabel.visibility = View.VISIBLE
                        btnPrintConfig.visibility = View.VISIBLE
                    }

                    val status = cs?.status?.trim().takeIf { present(it) }
                    tvStatusValue.text = status ?: "-"
                    currentStatus = status

                    val observedFailures = cs?.failureObservations?.trim().takeIf { present(it) }
                    tvObservedFailuresValue.text = observedFailures ?: "-"

                    val qaObsRaw = cs?.qaObservations?.trim()
                    if (present(qaObsRaw)) {
                        qaObsRow.visibility = View.VISIBLE
                        tvQaObservationsValue.text = qaObsRaw!!.replace(";", ", ")
                    } else {
                        qaObsRow.visibility = View.GONE
                        tvQaObservationsValue.text = "-"
                    }

                    val qaRejectCount = cs?.qaRejectCount
                    if (qaRejectCount != null && qaRejectCount > 0) {
                        qaRejectCountRow.visibility = View.VISIBLE
                        tvQaRejectCountValue.text = qaRejectCount.toString()
                    } else {
                        qaRejectCountRow.visibility = View.GONE
                        tvQaRejectCountValue.text = "-"
                    }

                    // Mostrar campos de técnicos si tienen valor
                    cs?.finalDiagnosis?.trim()?.takeIf { present(it) }?.let {
                        finalDiagnosisRow.visibility = View.VISIBLE
                        tvFinalDiagnosisValue.text = it
                    }
                    cs?.receivedBy?.trim()?.takeIf { present(it) }?.let {
                        receivedByRow.visibility = View.VISIBLE
                        tvReceivedByValue.text = it
                    }
                    cs?.pretestReviewedBy?.trim()?.takeIf { present(it) }?.let {
                        pretestByRow.visibility = View.VISIBLE
                        tvPretestByValue.text = it
                    }
                    cs?.cleanedBy?.trim()?.takeIf { present(it) }?.let {
                        cleanedByRow.visibility = View.VISIBLE
                        tvCleanedByValue.text = it
                    }
                    cs?.repairTechnician?.trim()?.takeIf { present(it) }?.let {
                        repairTechnicianRow.visibility = View.VISIBLE
                        tvRepairTechnicianValue.text = it
                    }
                    cs?.programmedBy?.trim()?.takeIf { present(it) }?.let {
                        programmedByRow.visibility = View.VISIBLE
                        tvProgrammedByValue.text = it
                    }
                    cs?.batteryTestedBy?.trim()?.takeIf { present(it) }?.let {
                        batteryTestedByRow.visibility = View.VISIBLE
                        tvBatteryTestedByValue.text = it
                    }
                    cs?.qaPerformedBy?.trim()?.takeIf { present(it) }?.let {
                        qaPerformedByRow.visibility = View.VISIBLE
                        tvQaPerformedByValue.text = it
                    }
                    cs?.dismantledBy?.trim()?.takeIf { present(it) }?.let {
                        dismantledByRow.visibility = View.VISIBLE
                        tvDismantledByValue.text = it
                    }

                    csId = cs?.id
                    tvResult.text = ""
                    if (status != null) btnChangeState.isEnabled = true
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

            if (isRevisionInicial) {
                val selectedFallas = getSelectedFailureValues()
                if (selectedFallas.isEmpty()) {
                    StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                    tvResult.text = "Seleccioná al menos una falla (o 'Sin falla')."
                    return@setOnClickListener
                }

                AlertDialog.Builder(this)
                    .setTitle("Confirmar cambio a Irreparable")
                    .setMessage(
                        "Razón: ${selectedFallas.joinToString(", ")}\n\n" +
                                "¿Estás seguro que querés marcar este terminal como Irreparable?"
                    )
                    .setPositiveButton("Aceptar") { _, _ ->
                        executeChangeStatus(
                            serial = serial,
                            newStatus = STATUS_IRREPARABLE,
                            chip = chip,
                            tvResult = tvResult,
                            tvStatusValue = tvStatusValue,
                            btnChangeState = btnChangeState,
                            finishOnSuccess = true,
                            substatus = null,
                            failureObservations = selectedFallas
                        )
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            } else {
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

                        fun runModifyThenMaybeReject(resolvedSubstatus: String?, resolvedFailures: List<String>?) {
                            val confirmMsg = buildString {
                                append("¿Estás seguro que vas a cambiar este terminal de estado \"$fromStatus\" a \"$newStatus\"?")
                                if (resolvedSubstatus != null) append("\n\nSubestado: $resolvedSubstatus")
                                if (!resolvedFailures.isNullOrEmpty()) append("\n\nFallas: ${resolvedFailures.joinToString(", ")}")
                            }

                            fun doModifyThenMaybeReject(qaObsForMdw: String?, qaObsForUi: String?) {
                                executeChangeStatus(
                                    serial = serial,
                                    newStatus = newStatus,
                                    chip = chip,
                                    tvResult = tvResult,
                                    tvStatusValue = tvStatusValue,
                                    btnChangeState = btnChangeState,
                                    finishOnSuccess = false,
                                    substatus = resolvedSubstatus,
                                    failureObservations = resolvedFailures,
                                    onOk = {
                                        if (isQa && qaObsForMdw != null) {
                                            executeRejectQa(
                                                serial = serial,
                                                qaObsStringForMdw = qaObsForMdw,
                                                chip = chip,
                                                tvResult = tvResult,
                                                btnChangeState = btnChangeState,
                                                onSuccess = {
                                                    HistoryStore.add(
                                                        this@TerminalDetailsActivity,
                                                        HistoryEntry(
                                                            ts = System.currentTimeMillis(),
                                                            serial = serial,
                                                            role = role,
                                                            action = "REJECT",
                                                            ok = true,
                                                            message = buildString {
                                                                append("Estado: $newStatus")
                                                                if (resolvedSubstatus != null) append(" | Sub: $resolvedSubstatus")
                                                                if (!resolvedFailures.isNullOrEmpty()) append(" | Fallas: ${resolvedFailures.joinToString(", ")}")
                                                                if (qaObsForUi != null) append(" | ObsQA: $qaObsForUi")
                                                            }
                                                        )
                                                    )
                                                    setResult(Activity.RESULT_OK)
                                                    finish()
                                                }
                                            )
                                        } else {
                                            setResult(Activity.RESULT_OK)
                                            finish()
                                        }
                                    }
                                )
                            }

                            if (isQa) {
                                showQaRejectDialog { qaObsForMdw, qaObsForUi ->
                                    AlertDialog.Builder(this)
                                        .setTitle("Confirmar cambio de estado")
                                        .setMessage(confirmMsg)
                                        .setPositiveButton("Aceptar") { _, _ -> doModifyThenMaybeReject(qaObsForMdw, qaObsForUi) }
                                        .setNegativeButton("Cancelar", null)
                                        .show()
                                }
                            } else {
                                AlertDialog.Builder(this)
                                    .setTitle("Confirmar cambio de estado")
                                    .setMessage(confirmMsg)
                                    .setPositiveButton("Aceptar") { _, _ -> doModifyThenMaybeReject(null, null) }
                                    .setNegativeButton("Cancelar", null)
                                    .show()
                            }
                        }

                        if (newStatus == STATUS_REPARACION_TECNICA) {
                            showSubstatusDialogForReparacionTecnica { mdwSubstatus, _ ->
                                runModifyThenMaybeReject(resolvedSubstatus = mdwSubstatus, resolvedFailures = null)
                            }
                            return@setItems
                        }

                        if (newStatus == STATUS_IRREPARABLE) {
                            showFailureObsDialogForIrreparable { failures ->
                                runModifyThenMaybeReject(resolvedSubstatus = null, resolvedFailures = failures)
                            }
                            return@setItems
                        }

                        runModifyThenMaybeReject(resolvedSubstatus = null, resolvedFailures = null)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
        // ===== FIN CAMBIAR ESTADO =====

        // ===== COMPLETE =====
        btnComplete.setOnClickListener {
            if (serial.isEmpty() || role.isEmpty()) {
                StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                tvResult.text = "Faltan datos (serial/role)."
                return@setOnClickListener
            }

            // Recovery (Camino A): MODIFY + recoveredParts + technicianName (OBLIGATORIO)
            if (isRecovery) {
                val selectedParts = getSelectedRecoveredParts()
                if (selectedParts.isEmpty()) {
                    StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                    tvResult.text = "Seleccioná al menos un repuesto recuperado."
                    return@setOnClickListener
                }

                val recoveredPartsStr = selectedParts.joinToString("; ").let {
                    if (it.length > 500) it.take(500) else it
                }

                executeChangeStatus(
                    serial = serial,
                    newStatus = STATUS_PENDIENTE_FACTURACION,
                    chip = chip,
                    tvResult = tvResult,
                    tvStatusValue = tvStatusValue,
                    btnChangeState = btnChangeState,
                    finishOnSuccess = true,
                    substatus = null,
                    failureObservations = null,
                    recoveredParts = recoveredPartsStr,
                    technicianNameRequired = true
                )
                return@setOnClickListener
            }

            val observations = if (isRevisionInicial) getSelectedFailureValues() else null
            if (isRevisionInicial && observations.isNullOrEmpty()) {
                StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                tvResult.text = "Seleccioná al menos una observación (o 'Sin falla')."
                return@setOnClickListener
            }

            fun executeComplete() {
                btnComplete.isEnabled = false
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

            if (isRevisionInicial && IrreparableChecker.isIrreparable(observations, currentAccountName)) {
                AlertDialog.Builder(this)
                    .setTitle("Advertencia: Terminal potencialmente irreparable")
                    .setMessage(
                        "Las fallas seleccionadas indican que este terminal podría ser irreparable:\n\n" +
                                "${observations?.joinToString(", ")}\n\n" +
                                "¿Estás seguro que querés enviarlo a reparación técnica en lugar de marcarlo como Irreparable?"
                    )
                    .setPositiveButton("Sí, enviar a reparación") { _, _ -> executeComplete() }
                    .setNegativeButton("Cancelar", null)
                    .show()
            } else {
                executeComplete()
            }
        }
        // ===== FIN COMPLETE =====
    }
}
