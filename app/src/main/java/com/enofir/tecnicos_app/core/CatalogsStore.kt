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
            "Sin falla", "Vinculada", "Audio defectuoso",
            "Carcasa posterior rayada", "Carcasa posterior golpeada",
            "Carcasa posterior rota", "Carcasa posterior desgastada",
            "Carcasa frontal rayada", "Carcasa frontal golpeada",
            "Carcasa frontal rota", "Carcasa frontal desgastada",
            "Se tilda terminal", "Sin tapa de bateria", "Sin tapa de impresora",
            "Display rayado", "Display golpeado", "Display roto",
            "Botón de inicio defectuoso", "Tactil roto", "Film dañado", "Impresora rota",
            "Tapa de bateria rayada", "Tapa de bateria golpeada",
            "Tapa de bateria rota", "Tapa de bateria desgastada",
            "Tapa de impresora rayada", "Tapa de impresora golpeada",
            "Tapa de impresora rota", "Tapa de impresora desgastada",
            "No enciende", "Cucarachas en placa principal",
            "Placa dañada - no bootea", "Placa dañada - no enciende",
            "Placa dañada - no anda impresora", "Placa dañada - sobrecalienta",
            "Placa dañada - sulfatada", "Placa dañada - tamper permanente",
            "Camara frontal rota", "Camara trasera rota", "Entrada USB dañada",
            "Pin de carga dañado", "Boton home", "USB sucio", "Lectora chip IC sucio",
            "Lectora chip IC dañada", "No comunica", "No bootea", "Tamper",
            "Falla lector magnetico", "Falla lector NFC",
            "No carga USB", "No da video", "Terminal sobrecalienta", "Sin rodillo"
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
        ),
        repairedParts = mapOf(
            "N910" to listOf(
                // Usados
                RecoveredPartItem("111012496U", "Carcasa frontal",             sfId = "a03Vr00000rHEQPIA4"),
                RecoveredPartItem("300052005U", "Carcasa frontal + Display",   sfId = "a03Vr00000lGEHIIA4"),
                RecoveredPartItem("111016684U", "Carcasa posterior",           sfId = "a03Vr00000lGEHKIA4"),
                RecoveredPartItem("111016685U", "Tapa bateria",                sfId = "a03Vr00000lGEHLIA4"),
                RecoveredPartItem("111016682U", "Tapa impresora",              sfId = "a03Vr00000lGEHJIA4"),
                RecoveredPartItem("102050193U", "Display",                     sfId = "a03Vr00000rHEQOIA4"),
                RecoveredPartItem("102040064U", "Impresora",                   sfId = "a03Vr00000lGEHRIA4"),
                RecoveredPartItem("115080285U", "Rodillo",                     sfId = "a03Vr00000lGEHqIAO"),
                RecoveredPartItem("301823114U", "Placa IO",                    sfId = "a03Vr00000lGEHMIA4"),
                RecoveredPartItem("111011983U", "Bracket placa IO",            sfId = "a03Vr00000uUpcmIAC"),
                RecoveredPartItem("108010045U", "Lectora chip IC",             sfId = "a03Vr00000lGEHSIA4"),
                RecoveredPartItem("102030103U", "Cabezal magnetico",           sfId = "a03Vr00000lGEHQIA4"),
                RecoveredPartItem("102020268U", "Camara posterior",            sfId = "a03Vr00000lGEHOIA4"),
                RecoveredPartItem("102020269U", "Camara frontal",              sfId = "a03Vr00000lGEHPIA4"),
                RecoveredPartItem("115030008U", "Parlante",                    sfId = "a03Vr00000lGEHTIA4"),
                RecoveredPartItem("108010078U", "Pin de carga",                sfId = "a03Vr00000lGEHYIA4"),
                RecoveredPartItem("108010143U", "Ficha micro USB",             sfId = "a03Vr00000lGEHZIA4"),
                RecoveredPartItem("111040263U", "Almohadilla de apoyo",        sfId = "a03Vr00000lGEHUIA4"),
                // Nuevos
                RecoveredPartItem("111012496",  "Carcasa frontal",             sfId = "a03Vr00000sIqKRIA0"),
                RecoveredPartItem("300052005",  "Carcasa frontal + Display",   sfId = "a03Vr00000lAFNqIAO"),
                RecoveredPartItem("111016684",  "Carcasa posterior",           sfId = "a03Vr00000lAFNwIAO"),
                RecoveredPartItem("111016685",  "Tapa bateria",                sfId = "a03Vr00000lAFNxIAO"),
                RecoveredPartItem("111016682",  "Tapa impresora",              sfId = "a03Vr00000lAFNvIAO"),
                RecoveredPartItem("102050193",  "Display",                     sfId = "a03Vr00000sIqKQIA0"),
                RecoveredPartItem("102040064",  "Impresora",                   sfId = "a03Vr00000lAFO5IAO"),
                RecoveredPartItem("115080285",  "Rodillo",                     sfId = "a03Vr00000lAFODIA4"),
                RecoveredPartItem("301823114",  "Placa IO",                    sfId = "a03Vr00000lAFO0IAO"),
                RecoveredPartItem("111011983",  "Bracket placa IO",            sfId = "a03Vr00000lAFNrIAO"),
                RecoveredPartItem("108010045",  "Lectora chip IC",             sfId = "a03Vr00000lAFO8IAO"),
                RecoveredPartItem("102030103",  "Cabezal magnetico",           sfId = "a03Vr00000lAFO4IAO"),
                RecoveredPartItem("102020268",  "Camara posterior",            sfId = "a03Vr00000lAFO2IAO"),
                RecoveredPartItem("102020269",  "Camara frontal",              sfId = "a03Vr00000lAFO3IAO"),
                RecoveredPartItem("115030008",  "Parlante",                    sfId = "a03Vr00000lAFOCIA4"),
                RecoveredPartItem("108010078",  "Pin de carga",                sfId = "a03Vr00000lAFOYIA4"),
                RecoveredPartItem("108010143",  "Ficha micro USB",             sfId = "a03Vr00000lAFOZIA4"),
                RecoveredPartItem("111040263",  "Almohadilla de apoyo",        sfId = "a03Vr00000lAFOOIA4"),
                RecoveredPartItem("113010034",  "Pila CR2025 - 034",           sfId = "a03Vr00000lAFO6IAO"),
                RecoveredPartItem("113010025",  "Pila CR2025 - 025",           sfId = "a03Vr00000lAFO7IAO")
            ),
            "N950_amarilla" to listOf(
                // Usados
                RecoveredPartItem("111018011U", "Carcasa frontal + Display (amarilla)", sfId = "a03Vr00000lGEHbIAO"),
                RecoveredPartItem("111018119U", "Carcasa posterior (amarilla)",         sfId = "a03Vr00000lGEHkIAO"),
                RecoveredPartItem("111018014U", "Tapa bateria (amarilla)",              sfId = "a03Vr00000lGEHlIAO"),
                RecoveredPartItem("111018010U", "Tapa impresora (amarilla)",            sfId = "a03Vr00000lGEHjIAO"),
                RecoveredPartItem("102050488U", "Display",                              sfId = "a03Vr00000vfy2gIAA"),
                RecoveredPartItem("102040062U", "Impresora",                            sfId = "a03Vr00000lGEHdIAO"),
                RecoveredPartItem("115080285U", "Rodillo",                              sfId = "a03Vr00000lGEHpIAO"),
                RecoveredPartItem("301981004U", "Placa IO",                             sfId = "a03Vr00000lGEHaIAO"),
                RecoveredPartItem("112030145U", "PET MESH",                             sfId = "a03Vr00000qSKilIAG"),
                RecoveredPartItem("112020225U", "Flex BEyF",                            sfId = "a03Vr00000qSKinIAG"),
                RecoveredPartItem("111050508U", "Botones",                              sfId = "a03Vr00000qSKimIAG"),
                RecoveredPartItem("111017228U", "Zebra bar bracket",                    sfId = "a03Vr00000qSKikIAG"),
                RecoveredPartItem("102020377U", "Camara posterior",                     sfId = "a03Vr00000lGEHfIAO"),
                RecoveredPartItem("102020263U", "Camara frontal",                       sfId = "a03Vr00000lGEHgIAO"),
                RecoveredPartItem("115030044U", "Parlante",                             sfId = "a03Vr00000lGEHeIAO"),
                RecoveredPartItem("108010100U", "Lectora chip IC",                      sfId = "a03Vr00000lGEHiIAO"),
                RecoveredPartItem("112020395U", "Flex carga",                           sfId = "a03Vr00000qSKioIAG"),
                RecoveredPartItem("102030174U", "Cabezal magnetico",                    sfId = "a03Vr00000qSKipIAG"),
                RecoveredPartItem("111015638U", "Soporte cabezal magnetico",            sfId = "a03Vr00000qSKiqIAG"),
                RecoveredPartItem("113010051U", "Pila",                                 sfId = "a03Vr00000lGEHhIAO"),
                // Nuevos
                RecoveredPartItem("111018011",  "Carcasa frontal + Display (amarilla)", sfId = "a03Vr00000lAFOdIAO"),
                RecoveredPartItem("111018119",  "Carcasa posterior (amarilla)",         sfId = "a03Vr00000lAFP5IAO"),
                RecoveredPartItem("111018014",  "Tapa bateria (amarilla)",              sfId = "a03Vr00000lAFP6IAO"),
                RecoveredPartItem("111018010",  "Tapa impresora (amarilla)",            sfId = "a03Vr00000lAFP4IAO"),
                RecoveredPartItem("102040062",  "Impresora",                            sfId = "a03Vr00000lAFOfIAO"),
                RecoveredPartItem("115080285",  "Rodillo",                              sfId = "a03Vr00000lAFPFIA4"),
                RecoveredPartItem("301981004",  "Placa IO",                             sfId = "a03Vr00000lAFOcIAO"),
                RecoveredPartItem("112020225",  "Flex BEyF",                            sfId = "a03Vr00000lAFOkIAO"),
                RecoveredPartItem("111017228",  "Zebra bar bracket",                    sfId = "a03Vr00000lAFOyIAO"),
                RecoveredPartItem("102020377",  "Camara posterior",                     sfId = "a03Vr00000lAFOiIAO"),
                RecoveredPartItem("102020263",  "Camara frontal",                       sfId = "a03Vr00000lAFOjIAO"),
                RecoveredPartItem("115030044",  "Parlante",                             sfId = "a03Vr00000lAFOhIAO"),
                RecoveredPartItem("108010100",  "Lectora chip IC",                      sfId = "a03Vr00000lAFOrIAO"),
                RecoveredPartItem("112020395",  "Flex carga",                           sfId = "a03Vr00000lAFOoIAO"),
                RecoveredPartItem("102030174",  "Cabezal magnetico",                    sfId = "a03Vr00000lAFOgIAO"),
                RecoveredPartItem("111015638",  "Soporte cabezal magnetico",            sfId = "a03Vr00000lAFP2IAO"),
                RecoveredPartItem("113010051",  "Pila",                                 sfId = "a03Vr00000lAFOpIAO")
            ),
            "N950_celeste" to listOf(
                // Usados
                RecoveredPartItem("111017208U", "Carcasa frontal + Display (blanca)", sfId = "a03Vr00000lGEHcIAO"),
                RecoveredPartItem("111017890U", "Carcasa posterior (blanca)",         sfId = "a03Vr00000lGEHnIAO"),
                RecoveredPartItem("111017042U", "Tapa bateria (blanca)",              sfId = "a03Vr00000lGEHoIAO"),
                RecoveredPartItem("111017052U", "Tapa impresora (celeste)",           sfId = "a03Vr00000lGEHmIAO"),
                RecoveredPartItem("102050488U", "Display",                            sfId = "a03Vr00000vfy2gIAA"),
                RecoveredPartItem("102040062U", "Impresora",                          sfId = "a03Vr00000lGEHdIAO"),
                RecoveredPartItem("115080285U", "Rodillo",                            sfId = "a03Vr00000lGEHpIAO"),
                RecoveredPartItem("301981004U", "Placa IO",                           sfId = "a03Vr00000lGEHaIAO"),
                RecoveredPartItem("112030145U", "PET MESH",                           sfId = "a03Vr00000qSKilIAG"),
                RecoveredPartItem("112020225U", "Flex BEyF",                          sfId = "a03Vr00000qSKinIAG"),
                RecoveredPartItem("111050508U", "Botones",                            sfId = "a03Vr00000qSKimIAG"),
                RecoveredPartItem("111017228U", "Zebra bar bracket",                  sfId = "a03Vr00000qSKikIAG"),
                RecoveredPartItem("102020377U", "Camara posterior",                   sfId = "a03Vr00000lGEHfIAO"),
                RecoveredPartItem("102020263U", "Camara frontal",                     sfId = "a03Vr00000lGEHgIAO"),
                RecoveredPartItem("115030044U", "Parlante",                           sfId = "a03Vr00000lGEHeIAO"),
                RecoveredPartItem("108010100U", "Lectora chip IC",                    sfId = "a03Vr00000lGEHiIAO"),
                RecoveredPartItem("112020395U", "Flex carga",                         sfId = "a03Vr00000qSKioIAG"),
                RecoveredPartItem("102030174U", "Cabezal magnetico",                  sfId = "a03Vr00000qSKipIAG"),
                RecoveredPartItem("111015638U", "Soporte cabezal magnetico",          sfId = "a03Vr00000qSKiqIAG"),
                RecoveredPartItem("113010051U", "Pila",                               sfId = "a03Vr00000lGEHhIAO"),
                // Nuevos
                RecoveredPartItem("111017208",  "Carcasa frontal + Display (blanca)", sfId = "a03Vr00000lAFOeIAO"),
                RecoveredPartItem("111017890",  "Carcasa posterior (blanca)",         sfId = "a03Vr00000lAFP8IAO"),
                RecoveredPartItem("111017042",  "Tapa bateria (blanca)",              sfId = "a03Vr00000lAFP9IAO"),
                RecoveredPartItem("111017052",  "Tapa impresora (celeste)",           sfId = "a03Vr00000lAFP7IAO"),
                RecoveredPartItem("102040062",  "Impresora",                          sfId = "a03Vr00000lAFOfIAO"),
                RecoveredPartItem("115080285",  "Rodillo",                            sfId = "a03Vr00000lAFPFIA4"),
                RecoveredPartItem("301981004",  "Placa IO",                           sfId = "a03Vr00000lAFOcIAO"),
                RecoveredPartItem("112020225",  "Flex BEyF",                          sfId = "a03Vr00000lAFOkIAO"),
                RecoveredPartItem("111017228",  "Zebra bar bracket",                  sfId = "a03Vr00000lAFOyIAO"),
                RecoveredPartItem("102020377",  "Camara posterior",                   sfId = "a03Vr00000lAFOiIAO"),
                RecoveredPartItem("102020263",  "Camara frontal",                     sfId = "a03Vr00000lAFOjIAO"),
                RecoveredPartItem("115030044",  "Parlante",                           sfId = "a03Vr00000lAFOhIAO"),
                RecoveredPartItem("108010100",  "Lectora chip IC",                    sfId = "a03Vr00000lAFOrIAO"),
                RecoveredPartItem("112020395",  "Flex carga",                         sfId = "a03Vr00000lAFOoIAO"),
                RecoveredPartItem("102030174",  "Cabezal magnetico",                  sfId = "a03Vr00000lAFOgIAO"),
                RecoveredPartItem("111015638",  "Soporte cabezal magnetico",          sfId = "a03Vr00000lAFP2IAO"),
                RecoveredPartItem("113010051",  "Pila",                               sfId = "a03Vr00000lAFOpIAO")
            )
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
    fun getRepairedParts(serial: String): List<RecoveredPartItem> {
        val key = when {
            serial.startsWith("NCC", ignoreCase = true) -> "N950_amarilla"
            serial.startsWith("NCB", ignoreCase = true) -> "N950_celeste"
            else -> "N910"
        }
        val map = _cached?.repairedParts ?: defaults.repairedParts
        return map?.get(key) ?: map?.get("N910") ?: emptyList()
    }
}
