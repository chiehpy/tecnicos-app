package com.enofir.tecnicos_app.core

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.enofir.tecnicos_app.BuildConfig
import com.enofir.tecnicos_app.model.AppVersionResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

/**
 * Verifica si hay actualizaciones disponibles y gestiona la descarga/instalación.
 */
object UpdateChecker {

    private var downloadId: Long = -1

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

                if (isNewerVersion(serverVersion, currentVersion)) {
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

    private fun showUpdateDialog(activity: Activity, version: String, url: String) {
        AlertDialog.Builder(activity)
            .setTitle("Actualización disponible")
            .setMessage("Hay una nueva versión disponible: v$version\n\n¿Descargar ahora?")
            .setPositiveButton("Descargar") { _, _ ->
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

    private fun downloadAndInstall(activity: Activity, url: String, version: String) {
        val fileName = "tecnicos-app-$version.apk"

        // Eliminar APK anterior si existe
        val downloadDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadDir, fileName)
        if (file.exists()) file.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Descargando actualización")
            .setDescription("TechFlow App v$version")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)

        // Registrar receiver para cuando termine la descarga
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    activity.unregisterReceiver(this)
                    installApk(activity, file)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            activity.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }

        showMessage(activity, "Descarga iniciada. Recibirás una notificación cuando esté lista.")
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
