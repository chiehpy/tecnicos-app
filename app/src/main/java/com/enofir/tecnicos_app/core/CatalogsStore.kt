package com.enofir.tecnicos_app.core

import android.content.Context
import com.enofir.tecnicos_app.model.CatalogsResponse
import com.enofir.tecnicos_app.model.RecoveredPartItem
import com.google.gson.Gson

object CatalogsStore {

    private const val PREFS_NAME = "tecnicos_app_catalogs"
    private const val KEY_CATALOGS = "catalogs_json"

    private val gson = Gson()
    private var _cached: CatalogsResponse? = null

    private val defaults = CatalogsResponse(
        failureObservations = listOf(
            "Sin falla", "Vinculada", "Audio defectuoso", "Carcasa posterior rota",
            "Carcasa frontal rota", "Se tilda terminal", "Sin tapa de bateria",
            "Sin tapa de impresora", "Display roto", "Botón de inicio defectuoso",
            "Tactil roto", "Film dañado", "Impresora rota", "Tapa de bateria rota",
            "Tapa de impresora rota", "No enciende", "Cucarachas en placa principal",
            "Placa dañada - no bootea", "Placa dañada - no enciende",
            "Placa dañada - no anda impresora", "Placa dañada - sobrecalienta",
            "Placa dañada - sulfatada", "Placa dañada - tamper permanente",
            "Camara frontal rota", "Camara trasera rota", "Entrada USB dañada",
            "Pin de carga dañado", "Boton home", "USB sucio", "Lectora chip IC sucio",
            "Lectora chip IC dañada", "No comunica", "No bootea", "Tamper",
            "Falla lector magnetico", "Falla lector NFC"
        ),
        qaOptions = listOf(
            "Falta de limpieza: Carcasa posterior", "Falta de limpieza: Carcasa frontal",
            "Falta de limpieza: Tapa de bateria", "Falta de limpieza: Tapa de impresora",
            "Daño estetico: Carcasa posterior", "Daño estetico: Carcasa frontal",
            "Daño estetico: Dientes Impresora", "Daño estetico: Tapa de bateria",
            "Daño estetico: Tapa de impresora", "Daño estetico: Carcasa frontal gastada (amarilla)",
            "Daño estetico: Carcasa posterior gastada (amarilla)",
            "Daño estetico: Tapa de bateria gastada (amarilla)",
            "Daño estetico: Tapa de impresora (amarilla)", "Faltan tornillos", "Tamper",
            "Camara trasera", "Camara frontal", "Sin audio", "Vinculada", "Film dañado",
            "Faltan Apps", "Faltan llaves", "Display defectuoso", "Falla de hardware",
            "Falla placa principal", "Pin de carga defectuoso", "Lente trasero de camara",
            "No enciende", "Imprime claro", "Imprime corrido", "No imprime", "Fuga de luz",
            "Lectora de chip no funciona", "Lectora de chip bloqueada",
            "Lectora magnetica no funciona", "No bootea", "No toma señal GPRS"
        ),
        recoveredParts = mapOf(
            "N910" to listOf(
                RecoveredPartItem("111012496U", "Carcasa frontal"),
                RecoveredPartItem("300052005U", "Carcasa frontal + Display"),
                RecoveredPartItem("111016684U", "Carcasa posterior"),
                RecoveredPartItem("111016685U", "Tapa bateria"),
                RecoveredPartItem("111016682U", "Tapa impresora"),
                RecoveredPartItem("102050193U", "Display"),
                RecoveredPartItem("102040064U", "Impresora"),
                RecoveredPartItem("115080285U", "Rodillo"),
                RecoveredPartItem("301823114U", "Placa IO"),
                RecoveredPartItem("111011983U", "Bracket placa IO"),
                RecoveredPartItem("108010045U", "Lectora chip IC"),
                RecoveredPartItem("102030103U", "Cabezal magnetico"),
                RecoveredPartItem("102020268U", "Camara posterior"),
                RecoveredPartItem("102020269U", "Camara frontal"),
                RecoveredPartItem("115030008U", "Parlante"),
                RecoveredPartItem("108010078U", "Pin de carga"),
                RecoveredPartItem("108010143U", "Ficha micro USB"),
                RecoveredPartItem("111040263U", "Almohadilla de apoyo")
            ),
            "N950_amarilla" to listOf(
                // TODO: re-habilitar cuando estén mapeados en SF con SF ID:
                // RecoveredPartItem("111018011UND", "Carcasa frontal (amarilla)"),
                // RecoveredPartItem("111018011U",   "Carcasa frontal + Display (amarilla)"),
                // RecoveredPartItem("102050488U",   "Display"),
                RecoveredPartItem("111018119U", "Carcasa posterior (amarilla)"),
                RecoveredPartItem("111018014U", "Tapa bateria (amarilla)"),
                RecoveredPartItem("111018010U", "Tapa impresora (amarilla)"),
                RecoveredPartItem("102040062U", "Impresora"),
                RecoveredPartItem("115080285U", "Rodillo"),
                RecoveredPartItem("301981004U", "Placa IO"),
                RecoveredPartItem("112030145U", "PET MESH"),
                RecoveredPartItem("112020225U", "Flex BEyF"),
                RecoveredPartItem("111050508U", "Botones"),
                RecoveredPartItem("111017228U", "Zebra bar bracket"),
                RecoveredPartItem("102020377U", "Camara posterior"),
                RecoveredPartItem("102020263U", "Camara frontal")
            ),
            "N950_celeste" to listOf(
                // TODO: re-habilitar cuando estén mapeados en SF con SF ID:
                // RecoveredPartItem("111017208UND", "Carcasa frontal (blanca)"),
                // RecoveredPartItem("111017208U",   "Carcasa frontal + Display (blanca)"),
                // RecoveredPartItem("102050488U",   "Display"),
                RecoveredPartItem("111017890U", "Carcasa posterior (blanca)"),
                RecoveredPartItem("111017042U", "Tapa bateria (blanca)"),
                RecoveredPartItem("111017052U", "Tapa impresora (celeste)"),
                RecoveredPartItem("102040062U", "Impresora"),
                RecoveredPartItem("115080285U", "Rodillo"),
                RecoveredPartItem("301981004U", "Placa IO"),
                RecoveredPartItem("112030145U", "PET MESH"),
                RecoveredPartItem("112020225U", "Flex BEyF"),
                RecoveredPartItem("111050508U", "Botones"),
                RecoveredPartItem("111017228U", "Zebra bar bracket"),
                RecoveredPartItem("102020377U", "Camara posterior"),
                RecoveredPartItem("102020263U", "Camara frontal")
            )
        ),
        statuses = listOf(
            "Revisión inicial", "Reparación Técnica", "Limpieza", "Testeo", "Irreparable"
        ),
        substatusReparacion = listOf(
            "Carga de firmware", "Reparación", "Carga de firmware + Inyección"
        )
    )

    /** Carga la caché del disco a memoria. Llamar al inicio de la app. */
    fun init(context: Context) {
        _cached = load(context) ?: defaults
    }

    /** Guarda una respuesta fresca del servidor en memoria y en disco. */
    fun save(catalogs: CatalogsResponse, context: Context) {
        _cached = catalogs
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CATALOGS, gson.toJson(catalogs))
            .apply()
    }

    /** Lee desde SharedPreferences. Devuelve null si nunca se guardó. */
    private fun load(context: Context): CatalogsResponse? {
        val json = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CATALOGS, null) ?: return null
        return runCatching { gson.fromJson(json, CatalogsResponse::class.java) }.getOrNull()
    }

    val failureObservations: List<String> get() = _cached?.failureObservations ?: defaults.failureObservations
    val qaOptions: List<String> get() = _cached?.qaOptions ?: defaults.qaOptions
    fun getRecoveredParts(serial: String): List<RecoveredPartItem> {
        val key = when {
            serial.startsWith("NCC", ignoreCase = true) -> "N950_amarilla"
            serial.startsWith("NCB", ignoreCase = true) -> "N950_celeste"
            else -> "N910"
        }
        val map = _cached?.recoveredParts ?: defaults.recoveredParts
        return map[key] ?: map["N910"] ?: emptyList()
    }
    val statuses: List<String> get() = _cached?.statuses ?: defaults.statuses
    val substatusReparacion: List<String> get() = _cached?.substatusReparacion ?: defaults.substatusReparacion
}
