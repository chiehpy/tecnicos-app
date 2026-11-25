package com.enofir.tecnicos_app.model

/**
 * Respuesta de `GET app/repuestos/catalogo?serial=<serial>` (repuestos por dueño, 24-11-2026).
 * El MDW decide qué fichas corresponden a la terminal: comunes de Enofir Systems + plásticos/usados
 * del cliente dueño. Todo nullable: un MDW viejo (o la demo) manda otra cosa y la app usa su catálogo.
 */
data class RepuestosCatalogoResponse(
    val repuestos: List<RepuestoCatalogoItem>? = null,
)

data class RepuestoCatalogoItem(
    val id: String? = null,
    val nombre: String? = null,
    val pn: String? = null,
    val cuenta: String? = null,
    val usado: Boolean? = null,
) {
    /** null si la ficha no trae Id o nombre: no se puede registrar. */
    fun toPart(): RecoveredPartItem? {
        val n = nombre?.trim().orEmpty()
        val i = id?.trim().orEmpty()
        if (n.isEmpty() || i.isEmpty()) return null
        return RecoveredPartItem(pn = pn?.trim().orEmpty(), name = n, sfId = i)
    }
}
