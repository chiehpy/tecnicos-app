package com.enofir.tecnicos_app.core

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.enofir.tecnicos_app.BuildConfig
import com.enofir.tecnicos_app.model.AppVersionResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

/**
 * Verifica si hay actualizaciones disponibles y gestiona la descarga/instalación.
 */
object UpdateChecker {

    private const val PREFS = "update_checker"
    private const val KEY_DOWNLOADED_VERSION = "downloaded_version"

    /**
     * Verifica si hay una versión más nueva disponible.
     * Llama al endpoint /app/version y compara con la versión instalada.
     */
    fun check(activity: Activity, showNoUpdateMessage: Boolean = false) {
        ApiClient.getAppVersion().enqueue(object : Callback<AppVersionResponse> {
            override fun onResponse(call: Call<AppVersionResponse>, response: Response<AppVersionResponse>) {
                val body = response.body()

                if (!response.isSuccessful || body == null || !body.ok) {
                    if (showNoUpdateMessage) {
                        showMessage(activity, "No se pudo verificar actualizaciones")
                    }
                    return
                }

                val serverVersion = body.version ?: return
                val serverUrl = body.url ?: return
                val currentVersion = BuildConfig.VERSION_NAME

                if (isNewerVersion(serverVersion, currentVersion) &&
                    isNewerVersion(serverVersion, getDownloadedVersion(activity))) {
                    showUpdateDialog(activity, serverVersion, serverUrl)
                } else if (showNoUpdateMessage) {
                    showMessage(activity, "Ya tenés la última versión ($currentVersion)")
                }
            }

            override fun onFailure(call: Call<AppVersionResponse>, t: Throwable) {
                if (showNoUpdateMessage) {
                    showMessage(activity, "Error de conexión: ${t.message}")
                }
            }
        })
    }

    /**
     * Compara versiones semánticas (ej: 1.3.5 vs 1.3.2)
     * Retorna true si serverVersion es mayor que currentVersion
     */
    private fun isNewerVersion(serverVersion: String, currentVersion: String): Boolean {
        try {
            val server = serverVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val current = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }

            for (i in 0 until maxOf(server.size, current.size)) {
                val s = server.getOrElse(i) { 0 }
                val c = current.getOrElse(i) { 0 }
                if (s > c) return true
                if (s < c) return false
            }
        } catch (_: Exception) {}
        return false
    }

    private fun getDownloadedVersion(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DOWNLOADED_VERSION, "0.0.0") ?: "0.0.0"
    }

    private fun saveDownloadedVersion(context: Context, version: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DOWNLOADED_VERSION, version)
            .apply()
    }

    private fun showUpdateDialog(activity: Activity, version: String, url: String) {
        AlertDialog.Builder(activity)
            .setTitle("Actualización disponible")
            .setMessage("Hay una nueva versión disponible: v$version\n\n¿Descargar ahora?")
            .setPositiveButton("Descargar") { _, _ ->
                saveDownloadedVersion(activity, version)
                downloadAndInstall(activity, url, version)
            }
            .setNegativeButton("Más tarde", null)
            .setCancelable(false)
            .show()
    }

    private fun showMessage(activity: Activity, message: String) {
        AlertDialog.Builder(activity)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun downloadAndInstall(activity: Activity, url: String, version: String) {
        val fileName = "tecnicos-app-$version.apk"

        // Usar almacenamiento interno de la app
        val downloadDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: activity.filesDir
        val file = File(downloadDir, fileName)

        // Eliminar APK anterior si existe
        if (file.exists()) file.delete()

        // Mostrar progreso
        val progressDialog = ProgressDialog(activity).apply {
            setMessage("Descargando actualización...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            progress = 0
            setCancelable(false)
            show()
        }

        thread {
            try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, SecureRandom())

                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    .hostnameVerifier { _, _ -> true }
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    activity.runOnUiThread {
                        progressDialog.dismiss()
                        showMessage(activity, "Error al descargar: HTTP ${response.code()}")
                    }
                    return@thread
                }

                val body = response.body()
                if (body == null) {
                    activity.runOnUiThread {
                        progressDialog.dismiss()
                        showMessage(activity, "Error: respuesta vacía")
                    }
                    return@thread
                }

                val contentLength = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(file)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead: Long = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    if (contentLength > 0) {
                        val progress = ((totalBytesRead * 100) / contentLength).toInt()
                        activity.runOnUiThread {
                            progressDialog.progress = progress
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                activity.runOnUiThread {
                    progressDialog.dismiss()
                    installApk(activity, file)
                }

            } catch (e: Exception) {
                activity.runOnUiThread {
                    progressDialog.dismiss()
                    showMessage(activity, "Error de descarga: ${e.message}")
                }
            }
        }
    }

    private fun installApk(activity: Activity, file: File) {
        if (!file.exists()) {
            showMessage(activity, "Error: archivo no encontrado")
            return
        }

        val intent = Intent(Intent.ACTION_VIEW)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file
            )
        } else {
            Uri.fromFile(file)
        }

        intent.setDataAndType(uri, "application/vnd.android.package-archive")

        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            showMessage(activity, "Error al instalar: ${e.message}")
        }
    }
}
