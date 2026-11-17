package com.enofir.tecnicos_app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.enofir.tecnicos_app.model.FotosRepuestosResponse
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

/**
 * Miniaturas de repuestos con caché en el equipo.
 *
 * - El MDW publica un índice (Id de 15 -> versión). La app baja sólo las fotos nuevas o cambiadas
 *   y las guarda en filesDir/fotos_repuestos/<id15>_<version>.jpg; las viejas se borran.
 * - Sin red o con error, se usa lo que ya hay en disco. Un repuesto sin foto no muestra miniatura.
 * - Los bitmaps decodificados quedan en memoria (LruCache) para no leer disco en cada renglón.
 */
object FotosRepuestos {
    /**
     * Interruptor. En false la app no pide el índice, no baja nada y no muestra miniaturas.
     * Para habilitar: cargar las fotos en el MDW (ruta app/repuestos/fotos) y poner true.
     */
    const val HABILITADO = false

    private const val DIR = "fotos_repuestos"
    private val memoria = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    @Volatile private var refrescando = false

    private fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, DIR).apply { mkdirs() }

    private fun key(id: String?): String? = id?.trim()?.take(15)?.takeIf { it.length == 15 }

    /** Archivo en caché para ese repuesto (cualquier versión), o null. */
    private fun archivo(ctx: Context, id15: String): File? =
        dir(ctx).listFiles { f -> f.name.startsWith(id15 + "_") && f.name.endsWith(".jpg") }?.maxByOrNull { it.lastModified() }

    /** Miniatura del repuesto si ya está en el equipo; null si no tiene foto o todavía no se bajó. */
    fun get(ctx: Context, sfId: String?): Bitmap? {
        if (!HABILITADO) return null
        val id15 = key(sfId) ?: return null
        memoria.get(id15)?.let { return it }
        val f = archivo(ctx, id15) ?: return null
        val bmp = runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull() ?: return null
        memoria.put(id15, bmp)
        return bmp
    }

    /** Pide el índice y baja lo que falta. Silencioso: si falla, queda lo que había. */
    fun refresh(ctx: Context) {
        if (!HABILITADO || refrescando) return
        refrescando = true
        val app = ctx.applicationContext
        ApiClient.fetchFotosRepuestos().enqueue(object : Callback<FotosRepuestosResponse> {
            override fun onResponse(call: Call<FotosRepuestosResponse>, response: Response<FotosRepuestosResponse>) {
                val indice = response.body()?.fotos
                if (!response.isSuccessful || indice == null) { refrescando = false; return }
                Thread {
                    try { sincronizar(app, indice) } finally { refrescando = false }
                }.start()
            }
            override fun onFailure(call: Call<FotosRepuestosResponse>, t: Throwable) { refrescando = false }
        })
    }

    private fun sincronizar(ctx: Context, indice: Map<String, Long>) {
        val d = dir(ctx)
        val esperados = indice.mapNotNull { (id, v) -> key(id)?.let { "${it}_$v.jpg" } }.toSet()
        // Borra versiones viejas y fotos que ya no están en el server.
        d.listFiles()?.forEach { f ->
            if (f.name !in esperados) {
                memoria.remove(f.name.substringBefore("_"))
                f.delete()
            }
        }
        for ((id, v) in indice) {
            val id15 = key(id) ?: continue
            val destino = File(d, "${id15}_$v.jpg")
            if (destino.exists()) continue
            val body: ResponseBody = runCatching { ApiClient.fetchFotoRepuesto(id15).execute() }
                .getOrNull()?.takeIf { it.isSuccessful }?.body() ?: continue
            val tmp = File(d, "${id15}_$v.tmp")
            runCatching {
                body.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
                if (BitmapFactory.decodeFile(tmp.absolutePath) != null) tmp.renameTo(destino) else tmp.delete()
            }.onFailure { tmp.delete() }
            memoria.remove(id15)
        }
    }
}
