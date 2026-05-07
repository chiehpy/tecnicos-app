package com.enofir.tecnicos_app.ui

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.Chronometer
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.enofir.tecnicos_app.R
import android.widget.LinearLayout
import android.widget.SeekBar
import com.enofir.tecnicos_app.core.ApiClient
import com.enofir.tecnicos_app.core.HistoryStore
import com.enofir.tecnicos_app.core.PrintConfigStore
import com.enofir.tecnicos_app.core.SessionManager
import com.enofir.tecnicos_app.core.CatalogsStore
import com.enofir.tecnicos_app.model.CatalogsResponse
import com.enofir.tecnicos_app.model.FailureObservationsCatalog
import com.enofir.tecnicos_app.model.HistoryEntry
import com.enofir.tecnicos_app.model.PrintLabelResponse
import com.enofir.tecnicos_app.model.StatusCatalog
import com.enofir.tecnicos_app.model.TerminalEventResponse
import com.enofir.tecnicos_app.model.TerminalLookupResponse
import com.enofir.tecnicos_app.model.VmCheckResponse
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
        private const val ROLE_VERIFICAR_APPS = "Verificar Apps"
        private const val ROLE_PROGRAMADOR = "Programador (carga de firmwares)"
        private const val ROLE_REPARACION = "Reparación"

        private const val STATUS_IRREPARABLE = "Irreparable"
        private const val STATUS_REPARACION_TECNICA = "Reparación Técnica"
        private const val STATUS_PENDIENTE_FACTURACION = "Pendiente de facturación"

        // Impresora Zebra
        private const val PRINTER_IP = "192.168.0.10"
        private const val PRINTER_PORT = 9100
    }

    // Failure observations (Revisión inicial)
    private val failureOptions: List<String> get() = FailureObservationsCatalog.OPTIONS
    private val failureSelected: MutableSet<String> = mutableSetOf()

    // Catálogo QA — obtenido desde CatalogsStore (servidor o caché)
    private val qaOptions: List<String> get() = CatalogsStore.qaOptions

    // Catálogo de repuestos recuperados (Recovery) — filtrado por modelo según serial
    private var serial: String = ""
    private val recoveredPartsOptions get() = CatalogsStore.getRecoveredParts(serial)
    private val recoveredPartsSelected: BooleanArray by lazy { BooleanArray(recoveredPartsOptions.size) { false } }

    // Catálogo de repuestos utilizados (Reparación) — separado del de Recovery
    private val repairedPartsOptions get() = CatalogsStore.getRepairedParts(serial)
    private val repairedPartsSelected: BooleanArray by lazy { BooleanArray(repairedPartsOptions.size) { false } }
    private var repairedPartsNoneSelected = false

    // Substatus UI -> valor real MDW
    private data class SubOpt(val label: String, val mdwValue: String)

    private val reparacionSubstatusOptions: List<SubOpt> = listOf(
        SubOpt("Reparación", "Reparación"),
        SubOpt("Programación", "Carga de firmware + Inyección")
    )

    private var currentStatus: String? = null
    private var csId: String? = null
    private var currentAccountName: String? = null
    private var currentModelRaw: String = ""
    private var startTimestamp: Long = 0

    // Fallas de Revisión Inicial — cargadas desde el lookup
    private var revisionInicialFailures: List<String> = emptyList()

    // Estado de validacion QA (calculado en el lookup)
    private var qaValidationPassed = false
    private var qaConflictNotified = false

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

    private fun getSelectedFailureValues(): List<String> = failureSelected.toList()

    private fun clearAllFailureSelections() { failureSelected.clear() }

    private fun setOnlyNoneFailureSelected() {
        failureSelected.clear()
        failureSelected.add(FailureObservationsCatalog.NONE)
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
        for (i in recoveredPartsOptions.indices) if (recoveredPartsSelected[i]) out.add(recoveredPartsOptions[i].pn)
        return out
    }

    private fun renderRecoveredPartsText(tvRecoveredParts: TextView) {
        val names = recoveredPartsOptions
            .filterIndexed { i, _ -> recoveredPartsSelected.getOrElse(i) { false } }
            .map { it.name }
        tvRecoveredParts.text = if (names.isEmpty()) {
            "Repuestos: (sin seleccionar)"
        } else {
            "Repuestos: ${names.joinToString(", ")}"
        }
    }

    private fun getSelectedRepairedParts(): List<String> {
        if (repairedPartsNoneSelected) return emptyList()
        return repairedPartsOptions
            .filterIndexed { i, _ -> repairedPartsSelected.getOrElse(i) { false } }
            .map { it.sfId ?: it.pn }
    }

    private fun getPartUsageCount(partId: String): Int =
        getSharedPreferences("part_usage", Context.MODE_PRIVATE).getInt("u_$partId", 0)

    private fun incrementPartUsageCount(partId: String) {
        val prefs = getSharedPreferences("part_usage", Context.MODE_PRIVATE)
        prefs.edit().putInt("u_$partId", prefs.getInt("u_$partId", 0) + 1).apply()
    }

    private fun renderRepairedPartsText(tv: TextView) {
        tv.text = when {
            repairedPartsNoneSelected -> "Repuestos: No se cambiaron repuestos"
            else -> {
                val names = repairedPartsOptions
                    .filterIndexed { i, _ -> repairedPartsSelected.getOrElse(i) { false } }
                    .map { it.name }
                if (names.isEmpty()) "Repuestos: (sin seleccionar)" else "Repuestos: ${names.joinToString(", ")}"
            }
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
        showFailureTreeDialog(
            title = "Seleccionar fallas (obligatorio)",
            positiveLabel = "Continuar",
            showPlacaDanada = true,
            requireSelection = true,
            irreparableOnly = true,
            onConfirm = onConfirm
        )
    }

    private sealed class FallaDialogItem {
        data class Flat(val label: String) : FallaDialogItem()
        data class Cat(val cat: FailureObservationsCatalog.FallaCategory) : FallaDialogItem()
        data class Locked(val label: String) : FallaDialogItem()
    }

    private fun showFailureTreeDialog(
        title: String,
        positiveLabel: String,
        showPlacaDanada: Boolean,
        requireSelection: Boolean,
        irreparableOnly: Boolean = false,
        onConfirm: (List<String>) -> Unit
    ) {
        val noneLabel = FailureObservationsCatalog.NONE
        val cats = FailureObservationsCatalog.CATEGORIES
            .filter { showPlacaDanada || it.name != "Placa dañada" }
        val flatOpts = FailureObservationsCatalog.FLAT_OPTIONS
            .filter { it != noneLabel }
            .filter { !irreparableOnly || it != noneLabel }

        val items: List<FallaDialogItem> = (if (irreparableOnly) emptyList() else listOf(FallaDialogItem.Flat(noneLabel))) +
                cats.map { FallaDialogItem.Cat(it) } +
                flatOpts.map { FallaDialogItem.Flat(it) }

        val listView = ListView(this)

        val adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(pos: Int) = items[pos]
            override fun getItemId(pos: Int) = pos.toLong()
            override fun getViewTypeCount() = 2
            override fun getItemViewType(pos: Int) = if (items[pos] is FallaDialogItem.Cat) 1 else 0

            override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                return when (val item = items[pos]) {
                    is FallaDialogItem.Flat -> {
                        val cb = CheckBox(this@TerminalDetailsActivity)
                        cb.text = item.label
                        cb.textSize = 15f
                        cb.setPadding(32, 24, 32, 24)
                        cb.isChecked = item.label in failureSelected
                        cb.isClickable = false
                        cb.isFocusable = false
                        cb
                    }
                    is FallaDialogItem.Cat -> {
                        val selectedCount = item.cat.subOptions.count { it in failureSelected }
                        val cb = CheckBox(this@TerminalDetailsActivity)
                        cb.text = "${item.cat.name}  ▶"
                        cb.textSize = 15f
                        cb.setPadding(32, 24, 32, 24)
                        cb.isChecked = selectedCount > 0
                        cb.isClickable = false
                        cb.isFocusable = false
                        cb
                    }
                    is FallaDialogItem.Locked -> TextView(this@TerminalDetailsActivity) // nunca ocurre aquí
                }
            }
        }

        listView.adapter = adapter
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            when (val item = items[pos]) {
                is FallaDialogItem.Locked -> { /* no-op */ }
                is FallaDialogItem.Flat -> {
                    val label = item.label
                    if (label == noneLabel) {
                        if (noneLabel in failureSelected) failureSelected.remove(noneLabel)
                        else { failureSelected.clear(); failureSelected.add(noneLabel) }
                    } else {
                        if (label in failureSelected) failureSelected.remove(label)
                        else { failureSelected.add(label); failureSelected.remove(noneLabel) }
                    }
                    adapter.notifyDataSetChanged()
                }
                is FallaDialogItem.Cat -> {
                    val cat = item.cat
                    val subArray = cat.subOptions.toTypedArray()
                    val subChecked = BooleanArray(subArray.size) { subArray[it] in failureSelected }
                    AlertDialog.Builder(this)
                        .setTitle(cat.name)
                        .setMultiChoiceItems(subArray, subChecked) { _, which, checked ->
                            val sub = subArray[which]
                            if (checked) { failureSelected.add(sub); failureSelected.remove(noneLabel) }
                            else failureSelected.remove(sub)
                        }
                        .setPositiveButton("Listo") { d, _ ->
                            d.dismiss()
                            adapter.notifyDataSetChanged()
                        }
                        .show()
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(listView)
            .setPositiveButton(positiveLabel) { d, _ ->
                d.dismiss()
                val vals = failureSelected.toList()
                if (requireSelection && vals.isEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Faltan fallas")
                        .setMessage("Seleccioná al menos una falla.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@setPositiveButton
                }
                if (irreparableOnly && !IrreparableChecker.isIrreparable(vals, currentAccountName)) {
                    AlertDialog.Builder(this)
                        .setTitle("Fallas insuficientes")
                        .setMessage(
                            "Las fallas seleccionadas no califican como irreparable.\n\n" +
                            "Se requiere: placa dañada, carcasa frontal o display roto" +
                            if (currentAccountName?.trim().equals("Mercado Libre SA", ignoreCase = true))
                                "." else " combinado con impresora rota, cámara rota o carcasa posterior rota."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                    return@setPositiveButton
                }
                onConfirm(vals)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Form 1 del flujo de Reparación COMPLETE — árbol de fallas.
     * - "No se encontraron fallas adicionales" al tope (exclusivo con el resto)
     * - Fallas de Revisión Inicial: pre-chequeadas y bloqueadas (grises)
     * - Categorías con sub-opciones + fallas planas, filtrando irreparables para ML N950
     * Devuelve solo las fallas NUEVAS seleccionadas (no las de RI).
     */
    private fun showRepairFallasDialog(onConfirm: (initialDiagnosis: List<String>) -> Unit) {
        val isMercadoLibre = currentAccountName?.trim().equals("Mercado Libre SA", ignoreCase = true)
        val irreparableKeywords = listOf("placa", "carcasa frontal", "display", "tactil", "táctil")
        val noAdditional = "No se encontraron fallas adicionales"

        val lockedFallas = revisionInicialFailures.filter { it != FailureObservationsCatalog.NONE }
        val lockedSet = lockedFallas.toSet()

        val cats = FailureObservationsCatalog.CATEGORIES
            .filter { cat -> !(isMercadoLibre && irreparableKeywords.any { kw -> cat.name.contains(kw, ignoreCase = true) }) }

        val flatOpts = FailureObservationsCatalog.FLAT_OPTIONS
            .filter { it != FailureObservationsCatalog.NONE && it !in lockedSet }
            .filter { f -> !(isMercadoLibre && irreparableKeywords.any { kw -> f.contains(kw, ignoreCase = true) }) }

        val items: List<FallaDialogItem> =
            listOf(FallaDialogItem.Flat(noAdditional)) +
            lockedFallas.map { FallaDialogItem.Locked(it) } +
            cats.map { FallaDialogItem.Cat(it) } +
            flatOpts.map { FallaDialogItem.Flat(it) }

        val newSelected = mutableSetOf<String>()
        var noAdditionalSelected = false

        val listView = ListView(this)

        val adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(pos: Int) = items[pos]
            override fun getItemId(pos: Int) = pos.toLong()
            override fun getViewTypeCount() = 3
            override fun getItemViewType(pos: Int) = when (items[pos]) {
                is FallaDialogItem.Flat -> 0
                is FallaDialogItem.Cat -> 1
                is FallaDialogItem.Locked -> 2
            }

            override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                return when (val item = items[pos]) {
                    is FallaDialogItem.Flat -> {
                        val isNoAdd = item.label == noAdditional
                        val cb = CheckBox(this@TerminalDetailsActivity)
                        cb.text = item.label
                        cb.textSize = 15f
                        cb.setPadding(32, 24, 32, 24)
                        cb.isChecked = if (isNoAdd) noAdditionalSelected else item.label in newSelected
                        cb.isClickable = false
                        cb.isFocusable = false
                        cb
                    }
                    is FallaDialogItem.Cat -> {
                        val selectedCount = item.cat.subOptions.count { it in newSelected }
                        val cb = CheckBox(this@TerminalDetailsActivity)
                        cb.text = "${item.cat.name}  ▶"
                        cb.textSize = 15f
                        cb.setPadding(32, 24, 32, 24)
                        cb.isChecked = selectedCount > 0
                        cb.isClickable = false
                        cb.isFocusable = false
                        cb
                    }
                    is FallaDialogItem.Locked -> {
                        val cb = CheckBox(this@TerminalDetailsActivity)
                        cb.text = item.label
                        cb.textSize = 15f
                        cb.setPadding(32, 24, 32, 24)
                        cb.isChecked = true
                        cb.isClickable = false
                        cb.isFocusable = false
                        cb.alpha = 0.4f
                        cb
                    }
                }
            }
        }

        listView.adapter = adapter
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            when (val item = items[pos]) {
                is FallaDialogItem.Locked -> { /* bloqueado */ }
                is FallaDialogItem.Flat -> {
                    val label = item.label
                    if (label == noAdditional) {
                        noAdditionalSelected = !noAdditionalSelected
                        if (noAdditionalSelected) newSelected.clear()
                    } else {
                        if (label in newSelected) newSelected.remove(label)
                        else { newSelected.add(label); noAdditionalSelected = false }
                    }
                    adapter.notifyDataSetChanged()
                }
                is FallaDialogItem.Cat -> {
                    val cat = item.cat
                    val subArray = cat.subOptions.toTypedArray()
                    val subChecked = BooleanArray(subArray.size) { subArray[it] in newSelected }
                    AlertDialog.Builder(this)
                        .setTitle(cat.name)
                        .setMultiChoiceItems(subArray, subChecked) { _, which, checked ->
                            val sub = subArray[which]
                            if (checked) { newSelected.add(sub); noAdditionalSelected = false }
                            else newSelected.remove(sub)
                        }
                        .setPositiveButton("Listo") { d, _ ->
                            d.dismiss()
                            adapter.notifyDataSetChanged()
                        }
                        .show()
                }
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Fallas encontradas")
            .setView(listView)
            .setPositiveButton("Continuar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!noAdditionalSelected && newSelected.isEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Falta seleccionar")
                    .setMessage("Seleccioná al menos una falla o \"$noAdditional\".")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }
            dialog.dismiss()
            onConfirm(newSelected.toList())
        }
    }

    /** Form 2 del flujo de Reparación COMPLETE — selección de repuestos utilizados. */
    private fun showRepairPartsDialog(onConfirm: (selected: List<String>, noneSelected: Boolean) -> Unit) {
        val noneLabel = "No se cambiaron repuestos"
        val sortedIndices = repairedPartsOptions.indices
            .sortedByDescending { i -> getPartUsageCount(repairedPartsOptions[i].sfId ?: repairedPartsOptions[i].pn) }
        val sortedParts = sortedIndices.map { repairedPartsOptions[it] }

        val allItems = (listOf(noneLabel) + sortedParts.map { part ->
            val usadoSuffix = if (part.pn.endsWith("UND") || part.pn.endsWith("U")) " - USADO" else ""
            val stockSuffix = if ((part.stock ?: 0) > 0) " (${part.stock})" else ""
            "${part.name}$usadoSuffix$stockSuffix"
        }).toTypedArray()

        val dialogSelected = BooleanArray(allItems.size) { false }
        if (repairedPartsNoneSelected) {
            dialogSelected[0] = true
        } else {
            sortedIndices.forEachIndexed { sortPos, origIdx ->
                dialogSelected[sortPos + 1] = repairedPartsSelected.getOrElse(origIdx) { false }
            }
        }

        val partsDialog = AlertDialog.Builder(this)
            .setTitle("Repuestos utilizados")
            .setMultiChoiceItems(allItems, dialogSelected) { _, which, checked ->
                if (which == 0) {
                    dialogSelected.fill(false)
                    dialogSelected[0] = checked
                } else {
                    dialogSelected[0] = false
                    dialogSelected[which] = checked
                }
            }
            .setPositiveButton("Aceptar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        partsDialog.show()

        partsDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val anySelected = dialogSelected.any { it }
            if (!anySelected) {
                AlertDialog.Builder(this)
                    .setTitle("Falta seleccionar")
                    .setMessage("Seleccioná al menos un repuesto o \"$noneLabel\".")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }
            repairedPartsNoneSelected = dialogSelected[0]
            sortedIndices.forEachIndexed { sortPos, origIdx ->
                repairedPartsSelected[origIdx] = dialogSelected[sortPos + 1]
            }
            partsDialog.dismiss()
            val selected = getSelectedRepairedParts()
            onConfirm(selected, repairedPartsNoneSelected)
        }
    }

    private fun showQaRejectDialog(onConfirm: (qaObsStringForMdw: String, qaObsStringForUi: String) -> Unit) {
        val qaSelected = BooleanArray(qaOptions.size) { false }

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
        initialDiagnosis: List<String>? = null,
        recoveredParts: String? = null,
        technicianNameRequired: Boolean = false,
        saveHistory: Boolean = true,
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
            technicianName = techName,
            recoveredParts = recoveredParts,
            failureObservations = failureObservations,
            initialDiagnosis = initialDiagnosis
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
                    if (saveHistory) {
                        HistoryStore.add(
                            this@TerminalDetailsActivity,
                            HistoryEntry(
                                ts = System.currentTimeMillis(),
                                serial = serial,
                                role = intent.getStringExtra(EXTRA_ROLE)?.trim().orEmpty(),
                                action = "MODIFY",
                                ok = true,
                                message = actionDesc,
                                startTs = startTimestamp
                            )
                        )
                    }

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

    private fun executeComplete(
        serial: String,
        role: String,
        chip: TextView,
        tvResult: TextView,
        btnComplete: Button,
        observations: List<String>? = null,
        appOk: Boolean? = null,
        firmwareOk: Boolean? = null,
        llaveOk: Boolean? = null,
        spareParts: List<String>? = null,
        caseId: String? = null,
        repairTime: String? = null,
        initialDiagnosis: List<String>? = null
    ) {
        btnComplete.isEnabled = false
        StatusChip.apply(chip, ChipState.PROCESSING, "PROCESANDO")
        tvResult.text = "Enviando COMPLETE..."

        val isVerificarAppsRole = role == ROLE_VERIFICAR_APPS
        val appOkValue = if (isVerificarAppsRole) true else appOk
        ApiClient.complete(serial, role, observations, appOk = appOkValue, firmwareOk = firmwareOk, llaveOk = llaveOk, spareParts = spareParts, caseId = caseId, repairTime = repairTime, initialDiagnosis = initialDiagnosis).enqueue(object : Callback<TerminalEventResponse> {

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

                    spareParts?.forEach { partId -> incrementPartUsageCount(partId) }

                    val endTs = System.currentTimeMillis()
                    android.util.Log.d("HistoryDebug", "Saving COMPLETE: startTimestamp=$startTimestamp, endTs=$endTs")
                    HistoryStore.add(
                        this@TerminalDetailsActivity,
                        HistoryEntry(
                            ts = endTs,
                            serial = serial,
                            role = role,
                            action = if (isVerificarAppsRole) "APPS_OK" else "COMPLETE",
                            ok = true,
                            message = body.message,
                            startTs = startTimestamp
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal_details)

        startTimestamp = System.currentTimeMillis()
        android.util.Log.d("HistoryDebug", "onCreate: startTimestamp=$startTimestamp")

        // Refrescar catálogos al abrir cada terminal (silencioso si falla)
        ApiClient.fetchCatalogs().enqueue(object : Callback<CatalogsResponse> {
            override fun onResponse(call: Call<CatalogsResponse>, response: Response<CatalogsResponse>) {
                if (response.isSuccessful) {
                    response.body()?.let { CatalogsStore.save(it, this@TerminalDetailsActivity) }
                }
            }
            override fun onFailure(call: Call<CatalogsResponse>, t: Throwable) { /* usa caché o defaults */ }
        })

        val chronometer = findViewById<Chronometer>(R.id.chronometer)
        val chronometerVisible = SessionManager(this).getChronometerVisible()
        if (chronometerVisible) {
            chronometer.visibility = View.VISIBLE
            chronometer.base = SystemClock.elapsedRealtime()
            chronometer.start()
        } else {
            chronometer.visibility = View.GONE
        }

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
        val qaConflictRow = findViewById<View>(R.id.qaConflictRow)
        val tvQaConflictItems = findViewById<TextView>(R.id.tvQaConflictItems)

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

        serial = intent.getStringExtra(EXTRA_SERIAL)?.trim().orEmpty()
        val role = intent.getStringExtra(EXTRA_ROLE)?.trim().orEmpty()
        val isRevisionInicial = role == ROLE_REVISION_INICIAL
        val isQa = role == ROLE_QA
        val isRecovery = role == ROLE_RECOVERY
        val isVerificarApps = role == ROLE_VERIFICAR_APPS
        val isProgramador = role == ROLE_PROGRAMADOR
        val isReparacion = role == ROLE_REPARACION

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
            failureObsContainer.visibility = View.GONE
            btnComplete.text = "Enviar a reparación técnica"
            btnChangeState.text = "Irreparable"
            btnChangeState.isEnabled = true
            btnChangeState.alpha = 1.0f
        } else if (isQa) {
            failureObsContainer.visibility = View.GONE
            recoveryContainer.visibility = View.GONE
            btnComplete.text = "APROBAR TERMINAL"
            btnComplete.isEnabled = false
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
                val items: Array<CharSequence> = recoveredPartsOptions.map { it.name }.toTypedArray()

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
        } else if (isVerificarApps) {
            failureObsContainer.visibility = View.GONE
            recoveryContainer.visibility = View.GONE
            btnComplete.text = "APPS OK"
            btnChangeState.text = "SIN APPS"
        } else if (isProgramador) {
            failureObsContainer.visibility = View.GONE
            recoveryContainer.visibility = View.GONE
            btnComplete.text = "PROGRAMAR TERMINAL"
            btnChangeState.visibility = View.GONE
        } else if (isReparacion) {
            failureObsContainer.visibility = View.GONE
            recoveryContainer.visibility = View.GONE
            btnComplete.text = "REPARAR TERMINAL"
            btnChangeState.text = "CAMBIAR ESTADO"
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
                    currentModelRaw = modelRaw

                    // Guardar accountName para IrreparableChecker
                    currentAccountName = cs?.accountName?.trim()

                    // Mostrar botones de impresión solo para QA si el serial está en la VM
                    if (isQa) {
                        ApiClient.vmCheck(serial).enqueue(object : Callback<VmCheckResponse> {
                            override fun onResponse(call: Call<VmCheckResponse>, response: Response<VmCheckResponse>) {
                                if (response.isSuccessful && response.body()?.exists == true) {
                                    btnPrintLabel.visibility = View.VISIBLE
                                    btnPrintConfig.visibility = View.VISIBLE
                                }
                            }
                            override fun onFailure(call: Call<VmCheckResponse>, t: Throwable) {
                                // Sin conexión o error: dejar botones ocultos
                            }
                        })
                    }

                    val status = cs?.status?.trim().takeIf { present(it) }
                    tvStatusValue.text = status ?: "-"
                    currentStatus = status

                    val observedFailures = cs?.failureObservations?.trim().takeIf { present(it) }
                    tvObservedFailuresValue.text = observedFailures ?: "-"
                    revisionInicialFailures = observedFailures
                        ?.split(",", ";", "+")
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?: emptyList()

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

                    if (isQa) {
                        val missing = mutableListOf<String>()
                        if (cs?.appOk != true) missing.add("Faltan apps")
                        if (cs?.firmwareOk != true) missing.add("Falta firmware")
                        if (cs?.llaveOk != true) missing.add("Falta llave")
                        if (!present(cs?.cleanedBy)) missing.add("Falta limpieza")

                        if (missing.isEmpty()) {
                            qaValidationPassed = true
                            btnComplete.isEnabled = true
                            qaConflictRow.visibility = View.GONE
                        } else {
                            qaValidationPassed = false
                            btnComplete.isEnabled = false
                            StatusChip.apply(chip, ChipState.ERROR, "CONFLICTO")
                            qaConflictRow.visibility = View.VISIBLE
                            tvQaConflictItems.text = missing.joinToString("\n") { "- $it" }
                            tvResult.text = "No se puede aprobar: faltan campos requeridos."

                            if (!qaConflictNotified) {
                                qaConflictNotified = true
                                ApiClient.qaNotify(serial, missing).enqueue(object : Callback<TerminalEventResponse> {
                                    override fun onResponse(call: Call<TerminalEventResponse>, response: Response<TerminalEventResponse>) {}
                                    override fun onFailure(call: Call<TerminalEventResponse>, t: Throwable) {}
                                })
                            }
                        }
                    }

                    csId = cs?.id
                    if (!isQa || qaValidationPassed) tvResult.text = ""
                    if (status != null && !isVerificarApps) btnChangeState.isEnabled = true
                    if (isVerificarApps) btnChangeState.isEnabled = true
                }

                override fun onFailure(call: Call<TerminalLookupResponse>, t: Throwable) {
                    tvResult.text = "No se pudo cargar info: ${t.message}"
                }
            })
        }
        // ===== FIN LOOKUP =====

        // ===== CAMBIAR ESTADO =====
        btnChangeState.setOnClickListener {
            // SIN APPS: envía COMPLETE con role "Verificar Apps"
            if (isVerificarApps) {
                btnChangeState.isEnabled = false
                btnComplete.isEnabled = false
                StatusChip.apply(chip, ChipState.PROCESSING, "PROCESANDO")
                tvResult.text = "Enviando SIN APPS..."

                ApiClient.complete(serial, role, null, appOk = false).enqueue(object : Callback<TerminalEventResponse> {
                    override fun onResponse(call: Call<TerminalEventResponse>, response: Response<TerminalEventResponse>) {
                        val body = response.body()
                        if (!response.isSuccessful || body == null) {
                            StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                            val raw = response.errorBody()?.string()?.trim().orEmpty()
                            tvResult.text = "HTTP ${response.code()} - ${if (raw.isNotEmpty()) raw else "Error en respuesta"}"
                            btnChangeState.isEnabled = true
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
                                    action = "SIN_APPS",
                                    ok = true,
                                    message = body.message,
                                    startTs = startTimestamp
                                )
                            )
                            setResult(Activity.RESULT_OK)
                            finish()
                        } else {
                            StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                            tvResult.text = body.message ?: "Error"
                            btnChangeState.isEnabled = true
                            btnComplete.isEnabled = true
                        }
                    }
                    override fun onFailure(call: Call<TerminalEventResponse>, t: Throwable) {
                        StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                        tvResult.text = "Falla de conexión: ${t.message}"
                        btnChangeState.isEnabled = true
                        btnComplete.isEnabled = true
                    }
                })
                return@setOnClickListener
            }

            val fromStatus = currentStatus
            if (fromStatus == null) {
                tvResult.text = "No se puede cambiar: estado actual desconocido."
                return@setOnClickListener
            }

            if (isRevisionInicial) {
                showFailureTreeDialog(
                    title = "Seleccionar fallas (obligatorio)",
                    positiveLabel = "Continuar",
                    showPlacaDanada = true,
                    requireSelection = true,
                    irreparableOnly = true
                ) { selectedFallas ->
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
                }
            } else {
                val statusOptions = StatusCatalog.OPTIONS
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
                                val isIrreparable = newStatus == STATUS_IRREPARABLE
                                // Revisión Inicial → FailureObservations__c; otros roles → InitialDiagnosis__c
                                val irreparableUsesFailureObs = isIrreparable && role == ROLE_REVISION_INICIAL
                                executeChangeStatus(
                                    serial = serial,
                                    newStatus = newStatus,
                                    chip = chip,
                                    tvResult = tvResult,
                                    tvStatusValue = tvStatusValue,
                                    btnChangeState = btnChangeState,
                                    finishOnSuccess = false,
                                    substatus = resolvedSubstatus,
                                    failureObservations = if (irreparableUsesFailureObs) resolvedFailures else if (isIrreparable) null else resolvedFailures,
                                    initialDiagnosis = if (isIrreparable && !irreparableUsesFailureObs) resolvedFailures else null,
                                    saveHistory = !isQa,
                                    onOk = {
                                        if (isQa && qaObsForMdw != null) {
                                            executeRejectQa(
                                                serial = serial,
                                                qaObsStringForMdw = qaObsForMdw,
                                                chip = chip,
                                                tvResult = tvResult,
                                                btnChangeState = btnChangeState,
                                                onSuccess = {
                                                    val destAbrev = when (newStatus) {
                                                        StatusCatalog.REVISION_INICIAL -> "R.I."
                                                        StatusCatalog.REPARACION_TECNICA -> "Rep."
                                                        StatusCatalog.LIMPIEZA -> "Lim."
                                                        StatusCatalog.TESTEO -> "Testeo"
                                                        StatusCatalog.IRREPARABLE -> "Irrep."
                                                        else -> newStatus
                                                    }
                                                    HistoryStore.add(
                                                        this@TerminalDetailsActivity,
                                                        HistoryEntry(
                                                            ts = System.currentTimeMillis(),
                                                            serial = serial,
                                                            role = role,
                                                            action = "REJECT",
                                                            ok = true,
                                                            message = buildString {
                                                                append("Rechazada: $destAbrev")
                                                                if (resolvedSubstatus != null) append(" | Sub: $resolvedSubstatus")
                                                                if (!resolvedFailures.isNullOrEmpty()) append(" | Fallas: ${resolvedFailures.joinToString(", ")}")
                                                                if (qaObsForUi != null) append(" | ObsQA: $qaObsForUi")
                                                            },
                                                            startTs = startTimestamp
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

            // Reparación: Form 1 (fallas) → Form 2 (repuestos) → COMPLETE
            if (isReparacion) {
                if (csId.isNullOrBlank()) {
                    StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                    tvResult.text = "No se pudo obtener el ID del caso de SF. Reiniciá la pantalla."
                    return@setOnClickListener
                }

                // Form 1: fallas encontradas
                showRepairFallasDialog { nuevasFallas ->
                    // Form 2: repuestos utilizados
                    showRepairPartsDialog { selected, noneSelected ->
                        val nowTs = System.currentTimeMillis()
                        val totalSeconds = ((nowTs - startTimestamp) / 1000).toInt()
                        val repairTime = "%02d:%02d:%02d".format(totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)

                        executeComplete(
                            serial, role, chip, tvResult, btnComplete,
                            spareParts = selected.ifEmpty { null },
                            caseId = csId,
                            repairTime = repairTime,
                            initialDiagnosis = nuevasFallas.ifEmpty { null }
                        )
                    }
                }
                return@setOnClickListener
            }

            // Recovery (Camino A): MODIFY + recoveredParts + technicianName (OBLIGATORIO)
            if (isRecovery) {
                val selectedParts = getSelectedRecoveredParts()
                val recoveredPartsStr = if (selectedParts.isEmpty()) "" else {
                    selectedParts.joinToString("; ").let {
                        if (it.length > 500) it.take(500) else it
                    }
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

            // Programador (carga de firmwares)
            if (isProgramador) {
                val session = SessionManager(this)
                val doFirmware = session.isProgrammerFirmware()
                val doLlaves = session.isProgrammerLlaves()
                if (!doFirmware && !doLlaves) {
                    AlertDialog.Builder(this)
                        .setTitle("Configuración incompleta")
                        .setMessage("No tenés ningún tipo de programación configurado en Ajustes.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@setOnClickListener
                }
                executeComplete(
                    serial, role, chip, tvResult, btnComplete,
                    firmwareOk = if (doFirmware) true else null,
                    llaveOk    = if (doLlaves)   true else null
                )
                return@setOnClickListener
            }

            if (isRevisionInicial) {
                showFailureTreeDialog(
                    title = "Fallas encontradas (obligatorio)",
                    positiveLabel = "Enviar a reparación",
                    showPlacaDanada = false,
                    requireSelection = true
                ) { observations ->
                    if (IrreparableChecker.isIrreparable(observations, currentAccountName)) {
                        AlertDialog.Builder(this)
                            .setTitle("Advertencia: Terminal potencialmente irreparable")
                            .setMessage(
                                "Las fallas seleccionadas indican que este terminal podría ser irreparable:\n\n" +
                                        "${observations.joinToString(", ")}\n\n" +
                                        "¿Estás seguro que querés enviarlo a reparación técnica en lugar de marcarlo como Irreparable?"
                            )
                            .setPositiveButton("Sí, enviar a reparación") { _, _ -> executeComplete(serial, role, chip, tvResult, btnComplete, observations) }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    } else {
                        executeComplete(serial, role, chip, tvResult, btnComplete, observations)
                    }
                }
                return@setOnClickListener
            }

            if (isQa) {
                if (!qaValidationPassed) {
                    StatusChip.apply(chip, ChipState.ERROR, "CONFLICTO")
                    tvResult.text = "No se puede aprobar: faltan campos requeridos."
                    return@setOnClickListener
                }
                executeComplete(serial, role, chip, tvResult, btnComplete)
                return@setOnClickListener
            }

            val observations = null
            executeComplete(serial, role, chip, tvResult, btnComplete, observations)
        }
        // ===== FIN COMPLETE =====
    }
}
