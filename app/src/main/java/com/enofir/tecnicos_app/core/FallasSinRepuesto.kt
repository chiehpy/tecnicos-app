package com.enofir.tecnicos_app.core

import com.google.gson.JsonElement

/**
 * Reparación, al completar: fallas declaradas que exigen un repuesto que el técnico no eligió
 * (dueño, 20-11-2026: avisar, no bloquear).
 *
 * La regla NO vive acá: el MDW la calcula con `reglas_entrega` (la misma que usa la red de seguridad
 * al aprobar QA) y la manda en `GET /app/catalogs`, clave aditiva `failure_parts`:
 *
 *  - `por_falla`: falla -> claves de pieza que la justifican (cualquiera alcanza).
 *  - `por_repuesto`: Id de Repuesto__c (15 caracteres) o PN del catálogo -> clave de pieza.
 *  - `piezas`: clave -> nombre para mostrar.
 *
 * Sin `failure_parts` (MDW viejo, sin nombres de Salesforce todavía) no se pregunta nada: igual que antes.
 * Una falla que no figura en `por_falla` no exige pieza (Tamper, No comunica, Sin falla...).
 * El film lo pone el rol Film después de Reparación: «Film dañado» nunca se pregunta acá.
 *
 * Lógica pura, sin Android.
 */
object FallasSinRepuesto {

    data class Mapa(
        val piezas: Map<String, String>,
        val porFalla: Map<String, List<String>>,
        val porRepuesto: Map<String, String>,
    )

    enum class Origen { PRETEST, PROPIA }

    /** Una falla a confirmar. [falla] es el texto tal como lo tiene el caso o lo eligió el técnico. */
    data class Pendiente(val falla: String, val origen: Origen, val piezas: List<String>)

    /** Lo que se manda al completar después de las decisiones del técnico. */
    data class Envio(val initialDiagnosis: List<String>, val discardedFailures: List<String>)

    const val PIEZA_FILM = "film"

    const val TITULO = "Falla sin repuesto registrado"
    const val BOTON_DESCARTAR = "Descartar falla"
    const val BOTON_REGISTRAR = "Registrar repuesto"

    private val PREFIJO_RECHAZO = Regex("""^rechazado por:\s*""", RegexOption.IGNORE_CASE)

    /** Como la lee el MDW (`split_fallas`): sin «Rechazado por:» adelante ni punto final. */
    fun normalizar(falla: String): String =
        PREFIJO_RECHAZO.replace(falla.trim(), "").trim().trimEnd('.').trim()

    /** Parseo tolerante: null si falta o viene mal formado. */
    fun parse(el: JsonElement?): Mapa? = runCatching {
        val obj = el?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        fun strMap(key: String): Map<String, String> =
            obj.get(key)?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()
                ?.mapNotNull { (k, v) -> if (v.isJsonPrimitive) k to v.asString else null }
                ?.toMap() ?: emptyMap()
        val porFalla = obj.get("por_falla")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val pf = porFalla.entrySet().mapNotNull { (falla, v) ->
            if (!v.isJsonArray) return@mapNotNull null
            val claves = v.asJsonArray.mapNotNull { runCatching { it.asString }.getOrNull() }
            if (claves.isEmpty()) null else falla to claves
        }.toMap()
        val porRepuesto = strMap("por_repuesto")
        if (pf.isEmpty() || porRepuesto.isEmpty()) return null
        Mapa(strMap("piezas"), pf, porRepuesto)
    }.getOrNull()

    /** Clave de pieza de un id de los que manda la app en `spareParts` (Id de SF de 15/18 o PN). */
    fun piezaDe(mapa: Mapa, id: String): String? {
        val t = id.trim()
        if (t.isEmpty()) return null
        return mapa.porRepuesto[t] ?: if (t.length >= 15) mapa.porRepuesto[t.take(15)] else null
    }

    /**
     * @param preTest fallas que ya trae el caso (FailureObservations__c de Revisión inicial).
     * @param propias fallas que el técnico eligió en el formulario de fallas.
     * @param repuestos ids que se van a mandar en `spareParts` (ya declarados + nuevos).
     * @return las fallas a confirmar, en orden (primero Pre-test), una por falla.
     */
    fun detectar(mapa: Mapa?, preTest: List<String>, propias: List<String>, repuestos: List<String>): List<Pendiente> {
        if (mapa == null) return emptyList()
        val elegidas = repuestos.mapNotNull { piezaDe(mapa, it) }.toSet()
        val vistas = mutableSetOf<String>()
        val out = mutableListOf<Pendiente>()
        for ((origen, lista) in listOf(Origen.PRETEST to preTest, Origen.PROPIA to propias)) {
            for (f in lista) {
                val n = normalizar(f)
                if (n.isEmpty() || !vistas.add(n.lowercase())) continue
                val req = mapa.porFalla[n] ?: mapa.porFalla[f.trim()] ?: continue
                if (PIEZA_FILM in req) continue
                if (req.any { it in elegidas }) continue
                out.add(Pendiente(f.trim(), origen, req.map { mapa.piezas[it] ?: it }))
            }
        }
        return out
    }

    fun mensaje(p: Pendiente): String =
        "La falla «${normalizar(p.falla)}» requiere el reemplazo de «${p.piezas.joinToString("» o «")}», " +
            "que no figura en los repuestos utilizados."

    /**
     * Arma lo que se manda con las fallas descartadas: las propias no se mandan; las de Pre-test
     * van en `discardedFailures` para que el MDW las quite del caso.
     */
    fun aplicar(propias: List<String>, descartadas: List<Pendiente>): Envio {
        // Todas: una falla de Pre-test que el técnico además tildó tampoco se manda.
        val fueraPropias = descartadas.map { normalizar(it.falla).lowercase() }.toSet()
        return Envio(
            initialDiagnosis = propias.filter { normalizar(it).lowercase() !in fueraPropias },
            discardedFailures = descartadas.filter { it.origen == Origen.PRETEST }.map { it.falla }.distinct(),
        )
    }
}
