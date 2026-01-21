package com.enofir.tecnicos_app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.enofir.tecnicos_app.core.ApiClient

/**
 * Activity base que escucha el broadcast AUTH_FAILED para redirigir
 * automáticamente al login cuando el token expira y el refresh falla.
 */
abstract class BaseActivity : AppCompatActivity() {

    private val authFailedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ApiClient.ACTION_AUTH_FAILED) {
                onAuthFailed()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerAuthReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterAuthReceiver()
    }

    private fun registerAuthReceiver() {
        val filter = IntentFilter(ApiClient.ACTION_AUTH_FAILED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(authFailedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(authFailedReceiver, filter)
        }
    }

    private fun unregisterAuthReceiver() {
        try {
            unregisterReceiver(authFailedReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver no registrado
        }
    }

    /**
     * Llamado cuando la autenticación falla (token expirado y refresh falló).
     * Por defecto redirige al login.
     */
    protected open fun onAuthFailed() {
        Toast.makeText(this, "Sesión expirada. Iniciá sesión nuevamente.", Toast.LENGTH_LONG).show()
        goToLogin()
    }

    protected fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
