package com.enofir.tecnicos_app.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.enofir.tecnicos_app.R
import com.enofir.tecnicos_app.core.ApiClient
import com.enofir.tecnicos_app.core.SessionManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        session = SessionManager(this)
        if (!session.isLoggedIn()) {
            finish()
            return
        }

        setContentView(R.layout.activity_settings)

        val spRole = findViewById<Spinner>(R.id.spRole)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val tvCurrent = findViewById<TextView>(R.id.tvCurrentRole)

        // Roles exactos MDW
        val roles: List<String> = ApiClient.allowedRoles.toList()

        val adapter: ArrayAdapter<String> = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            roles
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spRole.adapter = adapter

        // Seleccionar rol actual si existe
        val currentRole = session.getRole()?.trim().orEmpty()
        tvCurrent.text = "Rol actual: ${if (currentRole.isEmpty()) "(no configurado)" else currentRole}"

        if (currentRole.isNotEmpty()) {
            val idx = roles.indexOf(currentRole)
            if (idx >= 0) spRole.setSelection(idx)
        }

        btnSave.setOnClickListener {
            val selectedRole = spRole.selectedItem?.toString()?.trim().orEmpty()
            if (selectedRole.isEmpty()) {
                Toast.makeText(this, "Seleccioná un rol.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            session.setRole(selectedRole)
            Toast.makeText(this, "Rol guardado: $selectedRole", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
