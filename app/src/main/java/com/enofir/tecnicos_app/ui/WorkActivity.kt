package com.enofir.tecnicos_app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import com.enofir.tecnicos_app.R
import com.enofir.tecnicos_app.core.ApiClient
import com.enofir.tecnicos_app.core.SessionManager
import com.enofir.tecnicos_app.core.UpdateChecker
import com.enofir.tecnicos_app.model.TerminalEventResponse
import com.enofir.tecnicos_app.sdk.ScanActivity
import com.enofir.tecnicos_app.utils.ChipState
import com.enofir.tecnicos_app.utils.StatusChip
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class WorkActivity : BaseActivity() {

    private lateinit var etSerial: EditText
    private lateinit var session: SessionManager

    private var tvActiveRole: TextView? = null

    private lateinit var btnProcess: Button
    private lateinit var btnScan: Button
    private lateinit var tvResult: TextView
    private lateinit var chip: TextView

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                val code = res.data?.getStringExtra(ScanActivity.EXTRA_RESULT)?.trim()
                if (!code.isNullOrEmpty()) etSerial.setText(code)
            }
        }

    // PASO 4: volver de Details y limpiar serial SOLO si COMPLETE fue OK
    private val detailsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                etSerial.setText("")
                etSerial.requestFocus()
                tvResult.text = ""
                StatusChip.apply(chip, ChipState.OK, "LISTO")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        session = SessionManager(this)
        if (!session.isLoggedIn()) {
            goToLogin()
            return
        }

        setContentView(R.layout.activity_work)

        // UI
        chip = findViewById(R.id.statusChip)
        etSerial = findViewById(R.id.etSerial)
        btnProcess = findViewById(R.id.btnProcess)
        btnScan = findViewById(R.id.btnScan)
        tvResult = findViewById(R.id.tvResult)

        // Evita crash si no existe en el XML
        tvActiveRole = findViewById<TextView?>(R.id.tvActiveRole)

        // Botón hamburguesa
        val btnMenu = findViewById<ImageButton>(R.id.btnMenu)
        btnMenu.setOnClickListener { showMenu(btnMenu) }

        StatusChip.apply(chip, ChipState.ERROR, "LISTO")

        // Escanear
        btnScan.setOnClickListener {
            scanLauncher.launch(Intent(this, ScanActivity::class.java))
        }

        // Procesar terminal (ASSIGN)
        btnProcess.setOnClickListener {
            val serial = etSerial.text.toString().trim()

            val activeRole = session.getRole()?.trim().orEmpty()
            val technicianName = session.getTechnicianName()?.trim().orEmpty()

            if (serial.isEmpty()) {
                tvResult.text = "Ingresá un número de serie."
                return@setOnClickListener
            }
            if (activeRole.isEmpty()) {
                tvResult.text = "No hay rol activo. Configuralo en Ajustes."
                return@setOnClickListener
            }
            if (technicianName.isEmpty()) {
                tvResult.text = "Sesión inválida: falta technicianName."
                return@setOnClickListener
            }

            // Limpia estado visual previo
            tvResult.text = ""

            // Evitar doble toque mientras procesa
            btnProcess.isEnabled = false
            btnScan.isEnabled = false

            StatusChip.apply(chip, ChipState.PROCESSING, "PROCESANDO")
            tvResult.text = "Procesando terminal"

            ApiClient.assign(serial, activeRole, technicianName)
                .enqueue(object : Callback<TerminalEventResponse> {

                    override fun onResponse(
                        call: Call<TerminalEventResponse>,
                        response: Response<TerminalEventResponse>
                    ) {
                        // Rehabilitar controles
                        btnProcess.isEnabled = true
                        btnScan.isEnabled = true

                        val body = response.body()
                        if (!response.isSuccessful || body == null) {
                            StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                            val raw = response.errorBody()?.string()?.trim().orEmpty()
                            val msg = if (raw.isNotEmpty()) raw else "Error en respuesta"
                            tvResult.text = "HTTP ${response.code()} - $msg"
                            return
                        }

                        if (body.ok) {
                            StatusChip.apply(chip, ChipState.OK, "OK")
                            tvResult.text = body.message

                            val intent = Intent(this@WorkActivity, TerminalDetailsActivity::class.java).apply {
                                putExtra(TerminalDetailsActivity.EXTRA_SERIAL, serial)
                                putExtra(TerminalDetailsActivity.EXTRA_ROLE, activeRole)
                            }
                            detailsLauncher.launch(intent)
                        } else {
                            StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                            // Mensaje amigable para errores conocidos
                            tvResult.text = when (body.errorCode) {
                                "SF_NOT_FOUND" -> "La terminal no se encuentra cargada en Salesforce"
                                else -> body.message
                            }
                        }
                    }

                    override fun onFailure(call: Call<TerminalEventResponse>, t: Throwable) {
                        // Rehabilitar controles
                        btnProcess.isEnabled = true
                        btnScan.isEnabled = true

                        StatusChip.apply(chip, ChipState.ERROR, "ERROR")
                        tvResult.text = "Falla de conexión: ${t.message}"
                    }
                })
        }
    }

    override fun onResume() {
        super.onResume()
        val role = session.getRole()?.trim().orEmpty()
        val displayRole = if (role == "Recovery") "Parts Recovery" else role
        tvActiveRole?.text = "Rol activo: ${if (role.isEmpty()) "(no configurado)" else displayRole}"
    }

    private fun showMenu(anchor: ImageButton) {
        val popup = PopupMenu(this, anchor)
        val currentRole = session.getRole()?.trim().orEmpty()

        popup.menu.add(Menu.NONE, 1, 1, "Inicio")
        popup.menu.add(Menu.NONE, 2, 2, "Ajustes")
        popup.menu.add(Menu.NONE, 3, 3, "Historial")

        // Solo mostrar opción de imprimir etiqueta para QA
        if (currentRole == "QA") {
            popup.menu.add(Menu.NONE, 5, 4, "Imprimir Etiqueta")
        }

        popup.menu.add(Menu.NONE, 6, 5, "Buscar actualizaciones")
        popup.menu.add(Menu.NONE, 4, 6, "Cerrar sesión")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> true
                2 -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                3 -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    true
                }
                4 -> {
                    logout()
                    true
                }
                5 -> {
                    startActivity(Intent(this, PrintLabelActivity::class.java))
                    true
                }
                6 -> {
                    UpdateChecker.check(this, showNoUpdateMessage = true)
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun logout() {
        session.clear()
        goToLogin()
    }
}
