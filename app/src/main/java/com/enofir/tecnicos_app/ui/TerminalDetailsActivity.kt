package com.enofir.tecnicos_app.ui

import android.app.Activity
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.enofir.tecnicos_app.R
import com.enofir.tecnicos_app.core.ApiClient
import com.enofir.tecnicos_app.core.HistoryStore
import com.enofir.tecnicos_app.model.FailureObservationsCatalog
import com.enofir.tecnicos_app.model.HistoryEntry
import com.enofir.tecnicos_app.model.StatusCatalog
import com.enofir.tecnicos_app.model.TerminalEventResponse
import com.enofir.tecnicos_app.model.TerminalLookupResponse
import com.enofir.tecnicos_app.utils.ChipState
import com.enofir.tecnicos_app.utils.IrreparableChecker
import com.enofir.tecnicos_app.utils.StatusChip
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TerminalDetailsActivity : BaseActivity() {

    companion object {
        const val EXTRA_SERIAL = "extra_serial"
        const val EXTRA_ROLE = "extra_role"
        private const val ROLE_REVISION_INICIAL = "Revisión inicial"
        private const val ROLE_QA = "QA"

        private const val STATUS_IRREPARABLE = "Irreparable"
        private const val STATUS_REPARACION_TECNICA = "Reparación Técnica"
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

    // IMPORTANTE: una sola declaración
    private val qaSelected: BooleanArray = BooleanArray(qaOptions.size) { false }

    // Substatus UI -> valor real MDW
    private data class SubOpt(val label: String, val mdwValue: String)

    private val reparacionSubstatusOptions: List<SubOpt> = listOf(
        SubOpt("Reparación", "Reparación"),
        SubOpt("Programación", "Carga de firmware + Inyección")
    )

    private var currentStatus: String? = null

    private fun present(s: String?): Boolean =
        !s.isNullOrBlank() && s.trim() != "-" && s.trim().lowercase() != "null"

    private fun computeModelLabel(modelRaw: String?, imei2Raw: String?): String {
        val m = modelRaw?.trim().orEmpty()
        if (m == "N910") {
            return if (present(imei2Raw)) "N910 Plus" else "N910 A5"
        }
        return if (m.isNotEmpty()) m else "-"
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
            val canBeIrreparable = IrreparableChecker.isIrreparable(vals)
            btn.isEnabled = canBeIrreparable
            btn.alpha = if (canBeIrreparable) 1.0f else 0.5f
        }
    }

    /**
     * Diálogo substatus obligatorio cuando targetStatus="Reparación Técnica"
     */
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

    /**
     * Diálogo de fallas para Irreparable (OBLIGATORIO por MDW)
     */
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

    /**
     * REJECT QA:
     * - UI muestra con comas
     * - MDW suele querer ';' -> enviamos ';' al backend para no romper contrato.
     */
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

    /**
     * Ejecuta REJECT (QA) después de un MODIFY OK.
     */
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
     * MODIFY wrapper
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
        onOk: (() -> Unit)? = null
    ) {
        btnChangeState.isEnabled = false
        StatusChip.apply(chip, ChipState.PROCESSING, "PROCESANDO")
        tvResult.text = "Cambiando estado..."

        ApiClient.modify(serial, newStatus, substatus, failureObservations)
            .enqueue(object : Callback<TerminalEventResponse> {

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

        val btnComplete = findViewById<Button>(R.id.btnComplete)
        val btnChangeState = findViewById<Button>(R.id.btnChangeState)

        val failureObsContainer = findViewById<View>(R.id.failureObsContainer)
        val btnFailureObs = findViewById<Button>(R.id.btnFailureObs)
        val tvFailureObs = findViewById<TextView>(R.id.tvFailureObs)

        val serial = intent.getStringExtra(EXTRA_SERIAL)?.trim().orEmpty()
        val role = intent.getStringExtra(EXTRA_ROLE)?.trim().orEmpty()
        val isRevisionInicial = role == ROLE_REVISION_INICIAL
        val isQa = role == ROLE_QA

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
            // QA: aprobar terminal o rebotar (MODIFY → REJECT con observaciones QA)
            failureObsContainer.visibility = View.GONE
            btnComplete.text = "APROBAR TERMINAL"
            btnChangeState.text = "RECHAZAR TERMINAL"
        } else {
            // Limpieza u otros roles: wording estándar
            failureObsContainer.visibility = View.GONE
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

                    val status = cs?.status?.trim().takeIf { present(it) }
                    tvStatusValue.text = status ?: "-"
                    currentStatus = status

                    val observedFailures = cs?.failureObservations?.trim().takeIf { present(it) }
                    tvObservedFailuresValue.text = observedFailures ?: "-"

                    // QA Observations (mostrar si viene)
                    val qaObsRaw = cs?.qaObservations?.trim()
                    if (present(qaObsRaw)) {
                        qaObsRow.visibility = View.VISIBLE
                        tvQaObservationsValue.text = qaObsRaw!!.replace(";", ", ")
                    } else {
                        qaObsRow.visibility = View.GONE
                        tvQaObservationsValue.text = "-"
                    }

                    // Cantidad de rechazos QA (mostrar si viene y es > 0)
                    val qaRejectCount = cs?.qaRejectCount
                    if (qaRejectCount != null && qaRejectCount > 0) {
                        qaRejectCountRow.visibility = View.VISIBLE
                        tvQaRejectCountValue.text = qaRejectCount.toString()
                    } else {
                        qaRejectCountRow.visibility = View.GONE
                        tvQaRejectCountValue.text = "-"
                    }

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

            if (isRevisionInicial && IrreparableChecker.isIrreparable(observations)) {
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
