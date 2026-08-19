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
import com.enofir.tecnicos_app.core.EventVerifier
import com.enofir.tecnicos_app.core.HistoryStore
import com.enofir.tecnicos_app.core.PendingGate
import com.enofir.tecnicos_app.core.PrintConfigStore
import com.enofir.tecnicos_app.core.SessionManager
import com.enofir.tecnicos_app.core.CatalogsStore
import com.enofir.tecnicos_app.core.FailureMatrix
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
        const val EXTRA_PREVIOUS_TECH = "extra_previous_tech"
        private const val ROLE_REVISION_INICIAL = "Revisión inicial"
        private const val ROLE_QA = "QA"
        private const val ROLE_RECOVERY = "Recovery"
        private const val ROLE_VERIFICAR_APPS = "Verificar Apps"
        private const val ROLE_PROGRAMADOR = "Programador (carga de firmwares)"
        private const val ROLE_LIMPIEZA    = "Limpieza"
        private const val ROLE_REPARACION  = "Reparación"

        private const val STATUS_IRREPARABLE = "Irreparable"
        private const val STATUS_REPARACION_TECNICA = "Reparación Técnica"
        private const val STATUS_PENDIENTE_FACTURACION = "Pendiente de facturación"

        // Impresora Zebra
        private const val PRINTER_IP = "192.168.0.10"
        private const val PRINTER_PORT = 9100
        private const val REQ_PHOTO = 9001
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
        // Criterio de irreparabilidad: lo define el MDW por rol (pre-test solo puede
        // declarar lo medible sin abrir; Reparacion, todo el criterio del canon).
        val rol = SessionManager(this).getRole().orEmpty()
        showFailureTreeDialog(
            title = "Seleccionar fallas (obligatorio)",
            positiveLabel = "Continuar",
            showPlacaDanada = true,
            requireSelection = true,
            irreparableOnly = true,
            corresponden = FailureMatrix.irreparable(rol),
            onConfirm = onConfirm
        )
    }

    /**
     * Form 2 del flujo de Revisión inicial COMPLETE — versión de firmware.
     * Diálogo de una opción; el resultado se envía como firmwareBelow230 en el payload
     * del COMPLETE y el Apex lo traduce a un string que anexa a Comentarios__c.
     * - "2.3.0 o mayor" => onAnswered(false) => "Firmware 2.3.0 o superior" (incluye 2.3.0)
     * - "Menor a 2.3.0" => onAnswered(true)  => "Firmware menor a 2.3.0"
     * - "Cancelar"      => NO invoca el callback => se aborta el COMPLETE (no se envía nada al MDW)
     */
    private fun showFirmwareVersionDialog(onAnswered: (firmwareBelow230: Boolean) -> Unit) {
        // índice 0 = "2.3.0 o mayor" (firmwareBelow230 = false), índice 1 = "Menor a 2.3.0" (true)
        val opciones = arrayOf("2.3.0 o mayor", "Menor a 2.3.0")
        AlertDialog.Builder(this)
            .setTitle("¿Qué versión de firmware es?")
            .setItems(opciones) { _, which -> onAnswered(which == 1) }
            .setNegativeButton("Cancelar", null)
            .show()
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
        /** Fallas que corresponden a este par (rol, destino) segun la matriz del MDW.
         *  Vacio = sin matriz (MDW viejo o celda sin definir) -> se muestra todo, que es
         *  el comportamiento anterior. */
        corresponden: List<String> = emptyList(),
        /** "Sin falla" no aplica cuando el dialogo pide un MOTIVO (rebote, irreparable). */
        incluirSinFalla: Boolean = true,
        onConfirm: (List<String>) -> Unit
    ) {
        val noneLabel = FailureObservationsCatalog.NONE

        // El universo del dialogo: el catalogo completo menos "Sin falla" cuando no aplica.
        val universo = FailureObservationsCatalog.OPTIONS.filter { it != noneLabel }

        // La matriz manda. Los flags showPlacaDanada/irreparableOnly quedan solo como
        // respaldo para cuando el MDW no la sirve: son la version hardcodeada de la
        // misma regla ("el diagnostico de placa solo camino a Irreparable").
        val enCelda = when {
            corresponden.isNotEmpty() -> corresponden
            irreparableOnly -> universo.filter { it.startsWith("Placa dañada") || it.startsWith("Display") || it.startsWith("Carcasa frontal") }
            !showPlacaDanada -> universo.filter { !it.startsWith("Placa dañada") }
            else -> emptyList()
        }

        val arbol = FailureMatrix.construirArbol(enCelda, universo)
        val items: List<FallaDialogItem> =
            (if (irreparableOnly || !incluirSinFalla) emptyList()
             else listOf(FallaDialogItem.Flat(noneLabel))) +
            arbol.map {
                when (it) {
                    is FailureMatrix.Item.Grupo ->
                        FallaDialogItem.Cat(FailureObservationsCatalog.FallaCategory(it.nombre, it.miembros))
                    is FailureMatrix.Item.Falla -> FallaDialogItem.Flat(it.nombre)
                }
            }

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
        val noAdditional = "No se encontraron fallas adicionales"

        val lockedFallas = revisionInicialFailures.filter { it != FailureObservationsCatalog.NONE }
        val lockedSet = lockedFallas.toSet()

        val cats = FailureObservationsCatalog.CATEGORIES

        val flatOpts = FailureObservationsCatalog.FLAT_OPTIONS
            .filter { it != FailureObservationsCatalog.NONE && it !in lockedSet }

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
            .sortedByDescending { i ->
                val part = repairedPartsOptions[i]
                // Prioridad: usage_count global del MDW; fallback al conteo local del dispositivo
                part.usageCount ?: getPartUsageCount(part.sfId ?: part.pn)
            }
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
        // Opciones base + extras por modelo
        val isN950 = currentModelRaw.trim().startsWith("N950", ignoreCase = true)
        val effectiveOptions = if (isN950)
            qaOptions + listOf("Botón de inicio defectuoso")
        else
            qaOptions

        val qaSelected = BooleanArray(effectiveOptions.size) { false }

        val items: Array<CharSequence> = effectiveOptions.toTypedArray()

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
                    if (qaSelected[i]) selectedVals.add(effectiveOptions[i])
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
     * Rebote: **primero el destino, después el motivo**.
     *
     * El orden está invertido respecto de como era antes, y no es un capricho: las
     * razones válidas dependen del PAR (rol origen, destino) —a Limpieza no se le manda
     * "carcasa rota" ni al Programador "film dañado"— así que no se pueden filtrar antes
     * de saber a dónde va la terminal.
     *
     * Si el MDW no sirve la matriz, la lista de motivos sale sin filtrar y el flujo queda
     * equivalente al anterior.
     */
    private fun showRejectWithFailuresDialog(
        onConfirm: (failures: List<String>, destStatus: String, destSubstatus: String?) -> Unit
    ) {
        val session = SessionManager(this)
        val rol = session.getRole().orEmpty()
        val safeStatuses = StatusCatalog.OPTIONS.filter { it != STATUS_IRREPARABLE }

        AlertDialog.Builder(this)
            .setTitle("Enviar terminal a...")
            .setItems(safeStatuses.toTypedArray()) { _, which ->
                val destStatus = safeStatuses[which]
                if (destStatus == STATUS_REPARACION_TECNICA) {
                    // El subestado define a QUÉ ROL va: "Reparación" al técnico de
                    // Reparación, "Carga de firmware + Inyección" al Programador.
                    showSubstatusDialogForReparacionTecnica { mdwSubstatus, _ ->
                        pedirMotivoDelRebote(rol, destStatus, mdwSubstatus, onConfirm)
                    }
                } else {
                    pedirMotivoDelRebote(rol, destStatus, null, onConfirm)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Segundo paso del rebote: el motivo, ya filtrado por el destino elegido. */
    private fun pedirMotivoDelRebote(
        rol: String,
        destStatus: String,
        destSubstatus: String?,
        onConfirm: (failures: List<String>, destStatus: String, destSubstatus: String?) -> Unit,
    ) {
        val session = SessionManager(this)
        val corresponden = FailureMatrix.rebote(
            rol, destStatus, destSubstatus,
            progLlaves = session.isProgrammerLlaves(),
            progFirmware = session.isProgrammerFirmware(),
        )
        val rolDestino = FailureMatrix.rolDestino(destStatus, destSubstatus)
        val titulo = if (rolDestino != null) "Motivo — enviar a $rolDestino" else "Motivo del rechazo"

        // Se reusa el mismo arbol que el resto de los dialogos: agrupa por los grupos del
        // MDW y manda el resto a "Otras fallas".
        showFailureTreeDialog(
            title = titulo,
            positiveLabel = "Confirmar",
            showPlacaDanada = true,          // lo decide la matriz, no este flag
            requireSelection = true,
            corresponden = corresponden,
            incluirSinFalla = false,         // un rebote siempre lleva motivo
        ) { seleccionadas ->
            onConfirm(seleccionadas, destStatus, destSubstatus)
        }
    }

    // --- Verificación E2E (Feature 3): reintento bloqueante ---
    private var pendingRetryAction: (() -> Unit)? = null
    private var retryPrevEnabled: Map<Int, Boolean>? = null
    private val retryBlockedButtonIds = intArrayOf(R.id.btnComplete, R.id.btnChangeState, R.id.btnFaltaRepuesto)

    private fun showRetry(chip: TextView, tvResult: TextView, message: String, retry: () -> Unit) {
        StatusChip.apply(chip, ChipState.ERROR, "ERROR")
        tvResult.text = message
        pendingRetryAction = retry
        // Bloqueante: hasta reintentar con éxito, solo "Reintentar" queda disponible.
        // Guardamos el estado previo real (solo la 1ra vez) para restaurarlo al resolver.
        if (retryPrevEnabled == null) {
            retryPrevEnabled = retryBlockedButtonIds.associateWith { findViewById<Button>(it)?.isEnabled ?: false }
        }
        retryBlockedButtonIds.forEach { findViewById<Button>(it)?.isEnabled = false }
        findViewById<Button>(R.id.btnRetry)?.apply {
            visibility = View.VISIBLE
            isEnabled = true
        }
        // Pantalla bloqueante con la salida del gate: reintentar, o declarar que no se
        // pudo resolver (queda registrado en el MDW y recién ahí se desbloquea).
        PendingGate.showAfterFailure(
            activity = this,
            serial = serial,
            message = message,
            onRetry = { findViewById<Button>(R.id.btnRetry)?.performClick() },
            onDespachada = {
                hideRetry()
                tvResult.text = "Sin confirmar — registrado. Podés continuar."
            },
        )
    }

    private fun hideRetry() {
        pendingRetryAction = null
        findViewById<Button>(R.id.btnRetry)?.visibility = View.GONE
        // Restaurar el estado previo de los botones bloqueados
        retryPrevEnabled?.forEach { (id, wasEnabled) -> findViewById<Button>(id)?.isEnabled = wasEnabled }
        retryPrevEnabled = null
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

        EventVerifier.run(
            serial = serial,
            expectedStatus = null,   // REJECT → el estado resultante lo reporta el MDW (updates.Status)
            callFactory = { ApiClient.reject(serial, ROLE_QA, qaObsStringForMdw) },
        ) { result ->
            when (result) {
                is EventVerifier.Result.Success -> {
                    hideRetry()
                    StatusChip.apply(chip, ChipState.OK, "OK")
                    tvResult.text = result.response.message ?: "REJECT OK"
                    onSuccess()
                }
                is EventVerifier.Result.Failed -> {
                    showRetry(
                        chip, tvResult,
                        "REJECT no confirmado tras ${result.attempts} intento(s): ${result.lastMessage}",
                    ) { executeRejectQa(serial, qaObsStringForMdw, chip, tvResult, btnChangeState, onSuccess) }
                }
            }
        }
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
        comments: String? = null,
        onOk: (() -> Unit)? = null
    ) {
        btnChangeState.isEnabled = false
        StatusChip.apply(chip, ChipState.PROCESSING, "PROCESANDO")
        tvResult.text = "Cambiando estado..."

        val techName = getTechnicianNameFromJwt()
        val activeRole = intent.getStringExtra(EXTRA_ROLE)?.trim().orEmpty().takeIf { it.isNotEmpty() }

        if (technicianNameRequired && techName.isNullOrBlank()) {
            StatusChip.apply(chip, ChipState.ERROR, "ERROR")
            tvResult.text = "No se pudo obtener technicianName. Re-logueá e intentá de nuevo."
            btnChangeState.isEnabled = true
            return
        }

        EventVerifier.run(
            serial = serial,
            expectedStatus = newStatus,   // MODIFY: la app conoce el destino
            callFactory = {
                ApiClient.modify(
                    serial = serial,
                    targetStatus = newStatus,
                    targetSubstatus = substatus,
                    technicianName = techName,
                    recoveredParts = recoveredParts,
                    failureObservations = failureObservations,
                    initialDiagnosis = initialDiagnosis,
                    role = activeRole,
                    comments = comments
                )
            },
        ) { result ->
            when (result) {
                is EventVerifier.Result.Success -> {
                    hideRetry()
                    val body = result.response
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
                }
                is EventVerifier.Result.Failed -> {
                    showRetry(
                        chip, tvResult,
                        "No se pudo confirmar el cambio tras ${result.attempts} intento(s): ${result.lastMessage}",
                    ) {
                        executeChangeStatus(
                            serial, newStatus, chip, tvResult, tvStatusValue, btnChangeState,
                            finishOnSuccess, substatus, failureObservations, initialDiagnosis,
                            recoveredParts, technicianNameRequired, saveHistory, comments, onOk
                        )
                    }
                }
            }
        }
    }

    private var loadingDialog: android.app.ProgressDialog? = null
    private var completeDone = false
    private var previousTechnicianValue: String? = null

    private fun showLoadingOverlay() {
        @Suppress("DEPRECATION")
        loadingDialog = android.app.ProgressDialog(this).apply {
            setMessage("Validando en Salesforce...")
            isIndeterminate = true
            setCancelable(false)
            show()
        }
    }

    private fun hideLoadingOverlay() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun showSuccessAndFinish(caseId: String?) {
        completeDone = true
        val msg = if (!caseId.isNullOrBlank()) "Cargado en SF · $caseId" else "Cargado correctamente en SF"
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            setResult(Activity.RESULT_OK)
            finish()
        }, 1500)
    }

    private fun showErrorAlert(msg: String, onRetry: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Error de conexión")
            .setMessage("No se pudo validar con Salesforce.\n\n$msg")
            .setPositiveButton("Reintentar") { _, _ -> onRetry() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (completeDone) {
            super.onBackPressed()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Abandonar terminal")
            .setMessage("¿Querés salir sin completar? Se liberará la asignación.")
            .setPositiveButton("Abandonar") { _, _ ->
                sendRelease()
            }
            .setNegativeButton("Continuar trabajando", null)
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PHOTO && resultCode == android.app.Activity.RESULT_OK) {
            android.widget.Toast.makeText(this, "Foto subida al caso", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendRelease() {
        val role = intent.getStringExtra(EXTRA_ROLE)?.trim().orEmpty()
        val prevValue = previousTechnicianValue
        ApiClient.release(serial, role, prevValue).enqueue(object : Callback<TerminalEventResponse> {
            override fun onResponse(call: Call<TerminalEventResponse>, response: Response<TerminalEventResponse>) {
                finish()
            }
            override fun onFailure(call: Call<TerminalEventResponse>, t: Throwable) {
                finish()
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
        initialDiagnosis: List<String>? = null,
        firmwareBelow230: Boolean? = null
    ) {
        btnComplete.isEnabled = false
        StatusChip.apply(chip, ChipState.PROCESSING, "PROCESANDO")
        tvResult.text = "Enviando COMPLETE..."

        val isVerificarAppsRole = role == ROLE_VERIFICAR_APPS
        val appOkValue = if (isVerificarAppsRole) true else appOk
        val techName = getTechnicianNameFromJwt()

        fun doComplete() {
            showLoadingOverlay()
            EventVerifier.run(
                serial = serial,
                expectedStatus = null,   // COMPLETE: el estado resultante lo decide el Apex (lo reporta el MDW)
                callFactory = {
                    ApiClient.complete(serial, role, observations, appOk = appOkValue, firmwareOk = firmwareOk, llaveOk = llaveOk, spareParts = spareParts, caseId = caseId, repairTime = repairTime, initialDiagnosis = initialDiagnosis, technicianName = techName, firmwareBelow230 = firmwareBelow230)
                },
            ) { result ->
                hideLoadingOverlay()
                when (result) {
                    is EventVerifier.Result.Success -> {
                        hideRetry()
                        val body = result.response
                        StatusChip.apply(chip, ChipState.OK, "OK")
                        spareParts?.forEach { partId -> incrementPartUsageCount(partId) }
                        val endTs = System.currentTimeMillis()
                        HistoryStore.add(
                            this@TerminalDetailsActivity,
                            HistoryEntry(
                                ts = endTs, serial = serial, role = role,
                                action = if (isVerificarAppsRole) "APPS_OK" else "COMPLETE",
                                ok = true, message = body.message, startTs = startTimestamp
                            )
                        )
                        showSuccessAndFinish(body.salesforceId)
                    }
                    is EventVerifier.Result.Failed -> {
                        StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                        showRetry(
                            chip, tvResult,
                            "COMPLETE no confirmado tras ${result.attempts} intento(s): ${result.lastMessage}",
                        ) { doComplete() }
                    }
                }
            }
        }
        doComplete()
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
        val btnFaltaRepuesto = findViewById<Button>(R.id.btnFaltaRepuesto)
        val btnPrintLabel = findViewById<Button>(R.id.btnPrintLabel)
        val btnPrintConfig = findViewById<Button>(R.id.btnPrintConfig)
        val btnTakePhoto = findViewById<Button>(R.id.btnTakePhoto)

        val failureObsContainer = findViewById<View>(R.id.failureObsContainer)
        val btnFailureObs = findViewById<Button>(R.id.btnFailureObs)
        val tvFailureObs = findViewById<TextView>(R.id.tvFailureObs)

        serial = intent.getStringExtra(EXTRA_SERIAL)?.trim().orEmpty()
        val role = intent.getStringExtra(EXTRA_ROLE)?.trim().orEmpty()
        previousTechnicianValue = intent.getStringExtra(EXTRA_PREVIOUS_TECH)
        val isRevisionInicial = role == ROLE_REVISION_INICIAL
        val isQa = role == ROLE_QA
        val isRecovery = role == ROLE_RECOVERY
        val isVerificarApps = role == ROLE_VERIFICAR_APPS
        val isProgramador = role == ROLE_PROGRAMADOR
        val isLimpieza    = role == ROLE_LIMPIEZA
        val isReparacion  = role == ROLE_REPARACION

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

        // Foto: disponible para todos los roles
        btnTakePhoto.setOnClickListener {
            val intent = android.content.Intent(this, PhotoCaptureActivity::class.java)
            intent.putExtra(PhotoCaptureActivity.EXTRA_SERIAL, serial)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_PHOTO)
        }

        // Reintento de verificación E2E (Feature 3): re-ejecuta la última acción no confirmada
        findViewById<Button>(R.id.btnRetry).setOnClickListener {
            val action = pendingRetryAction
            hideRetry()
            action?.invoke()
        }

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
                val sortedIndices = recoveredPartsOptions.indices
                    .sortedByDescending { i ->
                        val part = recoveredPartsOptions[i]
                        part.usageCount ?: getPartUsageCount(part.sfId ?: part.pn)
                    }
                val sortedItems: Array<CharSequence> = sortedIndices.map { recoveredPartsOptions[it].name }.toTypedArray()
                val sortedSelected = BooleanArray(sortedIndices.size) { pos -> recoveredPartsSelected.getOrElse(sortedIndices[pos]) { false } }

                val dialog = AlertDialog.Builder(this)
                    .setTitle("Seleccionar repuestos recuperados")
                    .setMultiChoiceItems(sortedItems, sortedSelected) { _, which, checked ->
                        sortedSelected[which] = checked
                    }
                    .setPositiveButton("Aceptar") { d, _ ->
                        d.dismiss()
                        sortedIndices.forEachIndexed { sortPos, origIdx ->
                            recoveredPartsSelected[origIdx] = sortedSelected[sortPos]
                        }
                        renderRecoveredPartsText(tvRecoveredParts)
                    }
                    .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
                    .create()

                dialog.show()
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
            btnChangeState.text = "RECHAZAR TERMINAL"
            btnChangeState.isEnabled = true
            btnChangeState.alpha = 1.0f
        } else if (isLimpieza) {
            failureObsContainer.visibility = View.GONE
            recoveryContainer.visibility = View.GONE
            btnComplete.text = "LIMPIEZA COMPLETADA"
            btnChangeState.text = "RECHAZAR TERMINAL"
            btnChangeState.isEnabled = true
            btnChangeState.alpha = 1.0f
        } else if (isReparacion) {
            failureObsContainer.visibility = View.GONE
            recoveryContainer.visibility = View.GONE
            btnComplete.text = "REPARAR TERMINAL"
            btnChangeState.text = "CAMBIAR ESTADO"
            btnFaltaRepuesto.visibility = View.VISIBLE

            btnFaltaRepuesto.setOnClickListener {
                // Catálogo de repuestos según modelo (mismo que Reparación)
                val parts = repairedPartsOptions
                val sortedIndices = parts.indices.sortedByDescending { i ->
                    parts[i].usageCount ?: getPartUsageCount(parts[i].sfId ?: parts[i].pn)
                }
                val sortedItems = sortedIndices.map { parts[it].name }.toTypedArray<CharSequence>()
                val selected = BooleanArray(sortedItems.size) { false }

                val dialog = AlertDialog.Builder(this)
                    .setTitle("Repuestos faltantes")
                    .setMultiChoiceItems(sortedItems, selected) { _, which, checked ->
                        selected[which] = checked
                    }
                    .setPositiveButton("Confirmar") { d, _ ->
                        d.dismiss()
                        val chosenNames = sortedIndices
                            .filterIndexed { pos, _ -> selected[pos] }
                            .map { parts[it].name }

                        if (chosenNames.isEmpty()) {
                            AlertDialog.Builder(this)
                                .setTitle("Falta seleccionar")
                                .setMessage("Seleccioná al menos un repuesto.")
                                .setPositiveButton("OK", null)
                                .show()
                            return@setPositiveButton
                        }

                        val commentsText = "Esperando repuesto: ${chosenNames.joinToString(", ")}"

                        AlertDialog.Builder(this)
                            .setTitle("Confirmar")
                            .setMessage("Se cambiará el subestado a \"Esperando repuesto\".\n\n$commentsText")
                            .setPositiveButton("Aceptar") { _, _ ->
                                // Ruteo por la verificación E2E (mismo camino que el resto de los MODIFY)
                                executeChangeStatus(
                                    serial = serial,
                                    newStatus = currentStatus ?: "Reparación Técnica",
                                    chip = chip,
                                    tvResult = tvResult,
                                    tvStatusValue = tvStatusValue,
                                    btnChangeState = btnChangeState,
                                    finishOnSuccess = true,
                                    substatus = "Esperando repuesto",
                                    comments = commentsText
                                )
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
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
                        // N910 → solo firmware, llave y limpieza (sin apps)
                        // N950 y otros → los 4 campos
                        val isN910 = currentModelRaw.trim().equals("N910", ignoreCase = true)

                        val missing = mutableListOf<String>()
                        if (!isN910 && cs?.appOk != true)    missing.add("Faltan apps")
                        if (cs?.firmwareOk != true)          missing.add("Falta firmware")
                        if (cs?.llaveOk != true)             missing.add("Falta llave")
                        if (!present(cs?.cleanedBy))         missing.add("Falta limpieza")

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
                    if (isQa) btnChangeState.isEnabled = true  // RECHAZAR siempre habilitado al cargar
                    else if (status != null && !isVerificarApps) btnChangeState.isEnabled = true
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

                ApiClient.complete(serial, role, null, appOk = false, technicianName = getTechnicianNameFromJwt()).enqueue(object : Callback<TerminalEventResponse> {
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

            // Programador / Limpieza: rechazo con catálogo de fallas → initialDiagnosis
            if (isProgramador || isLimpieza) {
                showRejectWithFailuresDialog { failures, destStatus, destSubstatus ->
                    val rejectionStr = "Rechazado por: ${failures.joinToString(", ")}"
                    AlertDialog.Builder(this)
                        .setTitle("Confirmar rechazo")
                        .setMessage(
                            "$rejectionStr\n\nEnviar a: $destStatus" +
                            if (destSubstatus != null) " ($destSubstatus)" else ""
                        )
                        .setPositiveButton("Confirmar") { _, _ ->
                            executeChangeStatus(
                                serial = serial,
                                newStatus = destStatus,
                                chip = chip,
                                tvResult = tvResult,
                                tvStatusValue = tvStatusValue,
                                btnChangeState = btnChangeState,
                                finishOnSuccess = true,
                                substatus = destSubstatus,
                                initialDiagnosis = listOf(rejectionStr)
                            )
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
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
                    requireSelection = true,
                    // Pre-test reporta fallas de LLEGADA: la matriz las acota.
                    corresponden = FailureMatrix.diagnostico(ROLE_REVISION_INICIAL)
                ) { observations ->
                    // Form 2: versión de firmware → COMPLETE (el booleano va como firmwareBelow230 → Comentarios__c en SF)
                    val proceedWithFirmware = {
                        showFirmwareVersionDialog { firmwareBelow230 ->
                            executeComplete(
                                serial, role, chip, tvResult, btnComplete,
                                observations,
                                firmwareBelow230 = firmwareBelow230
                            )
                        }
                    }
                    if (IrreparableChecker.isIrreparable(observations, currentAccountName)) {
                        AlertDialog.Builder(this)
                            .setTitle("Advertencia: Terminal potencialmente irreparable")
                            .setMessage(
                                "Las fallas seleccionadas indican que este terminal podría ser irreparable:\n\n" +
                                        "${observations.joinToString(", ")}\n\n" +
                                        "¿Estás seguro que querés enviarlo a reparación técnica en lugar de marcarlo como Irreparable?"
                            )
                            .setPositiveButton("Sí, enviar a reparación") { _, _ -> proceedWithFirmware() }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    } else {
                        proceedWithFirmware()
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
