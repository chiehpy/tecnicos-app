package com.enofir.tecnicos_app.core

import com.enofir.tecnicos_app.model.FailureGroup

/**
 * Resuelve QUÉ fallas ve el técnico en cada diálogo, a partir de la matriz que sirve el
 * MDW en `GET /app/catalogs` (catálogo dinámico 2.0).
 *
 * La app **no contiene ninguna regla**: sólo recibe listas y las dibuja. Cambiar quién ve
 * qué es un PATCH al middleware, no un release. Ver
 * `[Techflow] catalogo-dinamico-2.0-matriz-rol-proceso.md` en el vault.
 *
 * Tres ideas que conviene no mezclar:
 *  - **diagnóstico** — qué encontró un rol al COMPLETAR su paso (un hallazgo).
 *  - **rebote** — con qué razón le pasa la terminal a OTRO rol (un motivo). Depende del
 *    PAR (rol origen, destino), y el destino incluye el subestado: "Reparación Técnica"
 *    sola no distingue si va a Reparación o al Programador.
 *  - **irreparable** — qué razones sostienen sacarla de la línea.
 *
 * Y una regla de oro: **lo que la matriz deja afuera no desaparece**. Va al grupo
 * `Otras fallas →`, desde donde se puede elegir igual. La matriz ordena, no prohíbe: si
 * un técnico tiene que ir a buscar algo ahí seguido, es la celda la que está mal.
 */
object FailureMatrix {

    const val OTRAS_FALLAS = "Otras fallas"

    /** Un ítem del árbol: o es una falla suelta, o un grupo que se despliega. */
    sealed class Item {
        data class Falla(val nombre: String) : Item()
        data class Grupo(val nombre: String, val miembros: List<String>) : Item()
    }

    /**
     * Clave de destino. Tiene que coincidir con `destino_key()` del MDW.
     * El subestado forma parte de la clave cuando existe.
     */
    fun destinoKey(estado: String, subestado: String?): String =
        if (subestado.isNullOrBlank()) estado else "$estado|$subestado"

    /** A qué rol le llega la terminal con ese (estado, subestado). Null si no se sabe. */
    fun rolDestino(estado: String, subestado: String?): String? =
        CatalogsStore.destinos?.get(destinoKey(estado, subestado))?.rol

    // ---------------------------------------------------------------- celdas

    /** Fallas que `rol` puede reportar al completar su paso. Vacío = sin matriz. */
    fun diagnostico(rol: String): List<String> =
        CatalogsStore.failureDiagnostico?.get(rol).orEmpty()

    /** Razones con las que `rol` puede declarar irreparable. */
    fun irreparable(rol: String): List<String> =
        CatalogsStore.failureIrreparable?.get(rol).orEmpty()

    /**
     * Razones válidas para que `rol` le pase la terminal al destino (estado, subestado).
     *
     * Para Programador la celda depende además de su rama: el de llaves sólo puede
     * reclamarle al de firmware y viceversa — nadie reclama por su propio dominio.
     */
    fun rebote(
        rol: String,
        estado: String,
        subestado: String? = null,
        progLlaves: Boolean = false,
        progFirmware: Boolean = false,
    ): List<String> {
        val clave = destinoKey(estado, subestado)
        if (rol.startsWith("Programador")) {
            val porRama = CatalogsStore.failureRebotePorRama ?: return emptyList()
            val rama = when {
                progLlaves && progFirmware -> "ambas"
                progLlaves -> "llaves"
                progFirmware -> "firmware"
                // Sin config no se puede saber qué rama NO hace: se devuelve la unión,
                // que es lo más permisivo y no bloquea a nadie.
                else -> null
            }
            if (rama != null) return porRama[rama]?.get(clave).orEmpty()
            return listOf("llaves", "firmware")
                .flatMap { porRama[it]?.get(clave).orEmpty() }
                .distinct()
        }
        return CatalogsStore.failureRebote?.get(rol)?.get(clave).orEmpty()
    }

    // ---------------------------------------------------------------- árbol

    /**
     * Arma el árbol del diálogo: primero lo que corresponde (agrupado), y al final
     * `Otras fallas →` con todo el resto, también agrupado.
     *
     * @param corresponden lo que dice la matriz para este par. Si viene vacío —porque el
     *        MDW es viejo o la celda no está definida— se devuelve el universo entero sin
     *        el grupo `Otras fallas`, que es exactamente el comportamiento anterior.
     * @param universo todas las fallas seleccionables (el catálogo del diálogo).
     */
    fun construirArbol(corresponden: List<String>, universo: List<String>): List<Item> {
        if (corresponden.isEmpty()) return agrupar(universo)

        val enCelda = corresponden.toSet()
        val principal = universo.filter { it in enCelda }
        val resto = universo.filter { it !in enCelda }

        val items = agrupar(principal).toMutableList()
        if (resto.isNotEmpty()) {
            // El resto va agrupado adentro de "Otras fallas", no como lista plana: para
            // el Programador, cuya celda tiene 2 fallas, si no serían 80 ítems sueltos.
            items += Item.Grupo(OTRAS_FALLAS, resto)
        }
        return items
    }

    /** Aplica el agrupamiento del MDW. Lo que no cae en ningún grupo queda suelto. */
    fun agrupar(fallas: List<String>): List<Item> {
        val grupos: List<FailureGroup> = CatalogsStore.failureGroups.orEmpty()
        if (grupos.isEmpty()) return fallas.map { Item.Falla(it) }

        val presentes = fallas.toSet()
        val items = mutableListOf<Item>()
        val yaAsignadas = mutableSetOf<String>()

        for (g in grupos) {
            // Sólo los miembros que están en esta celda. Grupo sin miembros no se muestra.
            val miembros = g.miembros.filter { it in presentes && it !in yaAsignadas }
            if (miembros.isEmpty()) continue
            items += Item.Grupo(g.nombre, miembros)
            yaAsignadas += miembros
        }
        items += fallas.filter { it !in yaAsignadas }.map { Item.Falla(it) }
        return items
    }

    /** true si el MDW está sirviendo la matriz (o sea, si hay algo que filtrar). */
    val disponible: Boolean
        get() = CatalogsStore.failureRebote != null || CatalogsStore.failureDiagnostico != null
}
