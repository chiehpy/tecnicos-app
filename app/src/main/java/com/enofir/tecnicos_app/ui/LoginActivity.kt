package com.enofir.tecnicos_app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.enofir.tecnicos_app.R
import com.enofir.tecnicos_app.core.SessionManager

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

        // Pre-cargado (MVP)
        etUser.setText("wramos")
        etPass.setText("1234")

        btnLogin.setOnClickListener {
            tvMsg.text = ""

            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString()

            if (user.isEmpty() || pass.isEmpty()) {
                tvMsg.text = "Completá usuario y contraseña."
                return@setOnClickListener
            }

            // MVP: auth local
            if (user != "wramos" || pass != "1234") {
                tvMsg.text = "Credenciales inválidas."
                return@setOnClickListener
            }

            // Guardar sesión
            session.saveSession(
                token = "local",
                username = "wramos",
                role = "Limpieza",
                technicianName = "Walter Ramos"
            )


            goToWork()
        }
    }

    private fun goToWork() {
        val intent = Intent(this, WorkActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
