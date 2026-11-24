package com.enofir.tecnicos_app.core

import android.content.Context
import com.enofir.tecnicos_app.BuildConfig
import com.enofir.tecnicos_app.model.TerminalEventRequest
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Modo DEMO (buildType `demo`): contesta TODAS las llamadas del MDW con datos de ejemplo,
 * sin salir a la red. Sólo se agrega al cliente OkHttp si BuildConfig.DEMO (ver ApiClient).
 *
 * Los formatos salen de los modelos reales (paquete model) y de SalesforceApi.kt.
 * El estado de cada serie vive en memoria: ASSIGN/COMPLETE/MODIFY lo hacen avanzar y el
 * lookup siguiente (incluida la 2da barrera del EventVerifier) lo ve.
 */
class DemoInterceptor(private val catalogsJson: () -> String) : Interceptor {

    constructor(context: Context) : this({
        context.assets.open("demo_catalogs.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
    })

    private val gson = Gson()
    private val json = MediaType.parse("application/json; charset=utf-8")

    /** fallas = FailureObservations__c simulado (null = el de ejemplo del modelo). */
    private data class Estado(var status: String, var substatus: String?, var tecnico: String?, var fallas: String? = null)

    private val estados = ConcurrentHashMap<String, Estado>()

    @Volatile private var usuario = "demo"

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val path = req.url().encodedPath().trimStart('/')
        val method = req.method()

        return when {
            path == "auth/login" -> {
                val body = bodyText(req)
                val u = runCatching { gson.fromJson(body, Map::class.java)["username"] as? String }.getOrNull()
                if (!u.isNullOrBlank()) usuario = u
                ok(req, mapOf(
                    "ok" to true,
                    "token" to fakeJwt(),
                    "refresh_token" to "demo-refresh-token",
                    "user" to mapOf(
                        "username" to usuario,
                        "technicianName" to TECNICO,
                        "allowedRoles" to ROLES,
                    ),
                ))
            }
            path == "auth/refresh" -> ok(req, mapOf("ok" to true, "token" to fakeJwt()))
            path == "app/version" -> ok(req, mapOf(
                "ok" to true,
                "version" to BuildConfig.VERSION_NAME,   // igual a la instalada: no pide actualizar
                "url" to "http://demo.invalid/techflow-demo.apk",
                "message" to "DEMO",
            ))
            path == "app/catalogs" -> raw(req, catalogsJson())
            path == ApiClient.MOCHILA_PATH -> ok(req, mochila())
            path == "app/mis-pendientes" -> ok(req, mapOf("pendientes" to emptyList<Any>(), "reasons" to emptyList<Any>()))
            path.startsWith("app/mis-pendientes/") -> ok(req, mapOf("ok" to true, "already" to false))
            path == "app/repuestos/fotos" -> ok(req, mapOf("fotos" to emptyMap<String, Long>()))
            path.startsWith("app/repuestos/fotos/") -> status(req, 404, "Not Found", """{"detail":"sin foto"}""")
            path == "terminal/lookup" -> ok(req, lookup(req.url().queryParameter("serial").orEmpty()))
            path == "terminal/event" -> ok(req, evento(bodyText(req)))
            path == "terminal/history" -> ok(req, mapOf("ok" to true, "data" to emptyList<Any>()))
            path == "terminal/photo" -> ok(req, mapOf("ok" to true, "message" to "Foto recibida (demo)", "sf_id" to "068DEMO000000001AAA"))
            path == "terminal/notify" -> ok(req, mapOf("ok" to true, "message" to "OK (demo)"))
            path == "vm/check" -> ok(req, mapOf("exists" to false))
            path == "print/label" -> ok(req, mapOf("ok" to false, "message" to "Impresión no disponible en la demo"))
            path.startsWith("recovery/") -> ok(req, mapOf("ok" to true, "message" to "OK (demo)", "verified" to true))
            // Cualquier otro: vacío válido, para que nada rompa.
            method == "GET" -> ok(req, emptyMap<String, Any>())
            else -> ok(req, mapOf("ok" to true, "message" to "OK (demo)", "verified" to true))
        }
    }

    // ── Respuestas ────────────────────────────────────────────────────────────

    private fun lookup(serialRaw: String): Map<String, Any?> {
        val serial = serialRaw.trim().uppercase()
        val n950 = serial.startsWith("NCC") || serial.startsWith("NCD") || serial.startsWith("NCB")
        val fallas = if (n950) FALLAS_N950 else FALLAS_N910
        val est = estados.getOrPut(serial) { Estado("Reparación Técnica", "Reparación", null) }
        val cs = mutableMapOf<String, Any?>(
            "id" to "500DEMO0000000${serial.takeLast(3).padStart(3, '0')}",
            "accountName" to "Mercado Libre SA",
            "failureObservations" to (est.fallas ?: fallas),
            "status" to est.status,
            "substatus" to est.substatus,
            "qaRejectCount" to 0,
            "receivedBy" to "Demo Recepción",
            "pretestReviewedBy" to "Demo Revisión",
        )
        if (est.tecnico != null) cs["repairTechnician"] = est.tecnico
        return mapOf(
            "ok" to true,
            "data" to mapOf(
                "success" to true,
                "serial" to serial,
                "cs" to cs,
                "asset" to mapOf(
                    "productName" to if (n950) "N950" else "N910",
                    "imei" to "35${serial.hashCode().toLong().let { Math.abs(it) }.toString().padStart(13, '0').take(13)}",
                    "serialNumber" to serial,
                    "id" to "02iDEMO000000001AAA",
                ),
            ),
        )
    }

    private fun evento(body: String): Map<String, Any?> {
        val r = runCatching { gson.fromJson(body, TerminalEventRequest::class.java) }.getOrNull()
            ?: return mapOf("ok" to false, "message" to "Request inválido (demo)", "error_code" to "INVALID_REQUEST")
        val serial = r.serial.trim().uppercase()
        val est = estados.getOrPut(serial) { Estado("Reparación Técnica", "Reparación", null) }
        val rol = r.role.orEmpty()
        when (r.action.uppercase()) {
            "ASSIGN" -> est.tecnico = r.technicianName
            "RELEASE" -> est.tecnico = null
            "COMPLETE" -> {
                SIGUIENTE[rol]?.let { est.status = it; est.substatus = null }
                // Igual que el MDW: las fallas de Pre-test descartadas en Reparación salen del caso.
                val fuera = r.discardedFailures.orEmpty().map { FallasSinRepuesto.normalizar(it).lowercase() }.toSet()
                if (fuera.isNotEmpty()) {
                    val n950 = serial.startsWith("NCC") || serial.startsWith("NCD") || serial.startsWith("NCB")
                    val quedan = (est.fallas ?: if (n950) FALLAS_N950 else FALLAS_N910).split(",")
                        .map { it.trim() }.filter { it.isNotEmpty() && FallasSinRepuesto.normalizar(it).lowercase() !in fuera }
                    est.fallas = quedan.joinToString(", ").ifEmpty { "Sin falla" }
                }
            }
            "MODIFY" -> r.targetStatus?.takeIf { it.isNotBlank() }?.let {
                est.status = it
                est.substatus = r.targetSubstatus
            }
            "REJECT" -> { est.status = "Reparación Técnica"; est.substatus = null }
        }
        return mapOf(
            "ok" to true,
            "message" to "Demo: ${r.action} OK" + (if (rol.isNotEmpty()) " ($rol)" else "") +
                (r.discardedFailures?.takeIf { it.isNotEmpty() }?.let { ". Descartadas: ${it.joinToString(", ")}" } ?: ""),
            "salesforce_id" to "500DEMO000000000AAA",
            "previous_technician" to null,
            "verified" to true,
            "status" to est.status,
        )
    }

    private fun mochila(): Map<String, Any?> = mapOf(
        "tecnico" to TECNICO,
        "tieneMochila" to true,
        "activa" to true,
        "mochilaId" to "a0DDEMO000000001AAA",
        "sinControl" to emptyList<String>(),
        "items" to listOf(
            item("a03Vr00000lGEHkIAO", "Carcasa posterior (amarilla) USADA", 3),
            item("a03Vr00000lAFP5IAO", "Carcasa posterior (amarilla)", 2),
            item("a03Vr00000lAFOkIAO", "Flex BEyF", 5),
            item("a03Vr00000lGEHjIAO", "Tapa impresora (amarilla) USADA", 2),
            item("a03Vr00000lAFP6IAO", "Tapa bateria (amarilla)", 4),
            item("a03Vr00000lGEHbIAO", "Carcasa frontal + Display (amarilla) USADA", 2),
            item("a03Vr000012ROBFIA4", "Display", 1),
            item("a03Vr00000lGEGzIAO", "Film pantalla", 20),
        ),
    )

    private fun item(id: String, nombre: String, cantidad: Int) =
        mapOf("repuestoId" to id, "nombre" to nombre, "cantidad" to cantidad)

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** JWT de mentira con technician_name (la app lo lee del payload). */
    private fun fakeJwt(): String {
        fun b64(s: String): String {   // base64url sin padding, sin depender de android.util
            val tabla = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
            val bytes = s.toByteArray(Charsets.UTF_8)
            val sb = StringBuilder()
            var i = 0
            while (i < bytes.size) {
                val n = bytes.size - i
                val b0 = bytes[i].toInt() and 0xFF
                val b1 = if (n > 1) bytes[i + 1].toInt() and 0xFF else 0
                val b2 = if (n > 2) bytes[i + 2].toInt() and 0xFF else 0
                sb.append(tabla[b0 shr 2])
                sb.append(tabla[((b0 and 3) shl 4) or (b1 shr 4)])
                if (n > 1) sb.append(tabla[((b1 and 15) shl 2) or (b2 shr 6)])
                if (n > 2) sb.append(tabla[b2 and 63])
                i += 3
            }
            return sb.toString()
        }
        val header = b64("""{"alg":"none","typ":"JWT"}""")
        val payload = b64(gson.toJson(mapOf(
            "sub" to usuario,
            "technician_name" to TECNICO,
            "exp" to 4102444800L,
        )))
        return "$header.$payload.demo"
    }

    private fun bodyText(req: Request): String {
        val b = req.body() ?: return ""
        val buf = Buffer()
        b.writeTo(buf)
        return buf.readUtf8()
    }

    private fun ok(req: Request, body: Any): Response = raw(req, gson.toJson(body))

    private fun raw(req: Request, body: String): Response = status(req, 200, "OK", body)

    private fun status(req: Request, code: Int, msg: String, body: String): Response =
        Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(msg)
            .body(ResponseBody.create(json, body))
            .build()

    companion object {
        const val TECNICO = "Demo Técnico"
        private val ROLES = listOf(
            "Reparación", "Film", "Revisión inicial", "Limpieza", "QA",
            "Recovery", "Programador (carga de firmwares)", "Verificar Apps",
        )
        private const val FALLAS_N950 = "Carcasa posterior rota, Botón de inicio defectuoso, Tapa de bateria rayada"
        private const val FALLAS_N910 = "Display roto, Tamper"

        /** Estado al que pasa la terminal al COMPLETAR el rol (Film no cambia el estado). */
        private val SIGUIENTE = mapOf(
            "Revisión inicial" to "Reparación Técnica",
            "Reparación" to "Limpieza",
            "Limpieza" to "Testeo",
            "QA" to "Pendiente de Envío",
        )
    }
}
