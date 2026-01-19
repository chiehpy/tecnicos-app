package com.enofir.tecnicos_app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.enofir.tecnicos_app.R
import com.enofir.tecnicos_app.core.ApiClient
import com.enofir.tecnicos_app.core.SessionManager
import com.enofir.tecnicos_app.model.LoginResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etUser = findViewById<EditText>(R.id.etUser)
        val etPass = findViewById<EditText>(R.id.etPass)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvMsg = findViewById<TextView>(R.id.tvLoginMsg)

        val session = SessionManager(this)

        // Si ya hay sesión, salteo login
        if (session.isLoggedIn()) {
            goToWork()
            return
        }

        btnLogin.setOnClickListener {
            tvMsg.text = ""

            val user = etUser.text.toString().trim().lowercase()
            val pin = etPass.text.toString().trim()

            if (user.isEmpty()) {
                tvMsg.text = "Ingresá tu usuario."
                return@setOnClickListener
            }

            if (pin.isEmpty()) {
                tvMsg.text = "Ingresá tu PIN."
                return@setOnClickListener
            }

            if (pin.length != 4) {
                tvMsg.text = "El PIN debe ser de 4 dígitos."
                return@setOnClickListener
            }

            // Deshabilitar botón mientras procesa
            btnLogin.isEnabled = false
            tvMsg.text = "Verificando..."

            ApiClient.login(user, pin).enqueue(object : Callback<LoginResponse> {

                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    btnLogin.isEnabled = true

                    val body = response.body()

                    if (!response.isSuccessful || body == null) {
                        val raw = response.errorBody()?.string()?.trim().orEmpty()
                        tvMsg.text = if (raw.isNotEmpty()) raw else "Error de conexión (HTTP ${response.code()})"
                        return
                    }

                    if (!body.ok || body.token == null || body.user == null) {
                        tvMsg.text = body.message ?: "Credenciales inválidas."
                        return
                    }

                    // Login exitoso: guardar sesión
                    session.saveSession(body.token, body.user)

                    goToWork()
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    btnLogin.isEnabled = true
                    tvMsg.text = "Error de conexión: ${t.message}"
                }
            })
        }
    }

    private fun goToWork() {
        val intent = Intent(this, WorkActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
