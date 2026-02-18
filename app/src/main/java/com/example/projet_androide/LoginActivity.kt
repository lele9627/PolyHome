package com.example.projet_androide

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.projet_androide.data.api.Api
import com.example.projet_androide.data.api.ApiRoutes
import com.example.projet_androide.data.model.AuthRequest
import com.example.projet_androide.data.model.AuthResponse
import com.example.projet_androide.data.storage.TokenStore

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etLogin = findViewById<EditText>(R.id.etLogin)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnDoLogin = findViewById<Button>(R.id.btnDoLogin)
        val tvSavedAccountsLabel = findViewById<TextView>(R.id.tvSavedAccountsLabel)
        val spinnerSavedAccounts = findViewById<Spinner>(R.id.spinnerSavedAccounts)
        val btnAddConnection = findViewById<Button>(R.id.btnAddConnection)
        val btnBackToSaved = findViewById<Button>(R.id.btnBackToSaved)
        val tvTokenHint = findViewById<TextView>(R.id.tvTokenHint)
        val tvGoToRegister = findViewById<TextView>(R.id.tvGoToRegister)

        tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        val api = Api()
        val tokenStore = TokenStore(this)
        val knownUsers = tokenStore.getKnownUsers()
        var manualMode = knownUsers.isEmpty()

        fun switchToTokenMode() {
            manualMode = false
            tvSavedAccountsLabel.visibility = View.VISIBLE
            spinnerSavedAccounts.visibility = View.VISIBLE
            btnAddConnection.visibility = View.VISIBLE
            tvTokenHint.visibility = View.VISIBLE

            etLogin.visibility = View.GONE
            etPassword.visibility = View.GONE
            btnBackToSaved.visibility = View.GONE

            etPassword.text?.clear()
            btnDoLogin.text = "Se connecter"
        }

        fun switchToManualMode() {
            manualMode = true
            tvSavedAccountsLabel.visibility = View.GONE
            spinnerSavedAccounts.visibility = View.GONE
            btnAddConnection.visibility = View.GONE
            tvTokenHint.visibility = View.GONE

            etLogin.visibility = View.VISIBLE
            etPassword.visibility = View.VISIBLE
            btnBackToSaved.visibility = if (knownUsers.isNotEmpty()) View.VISIBLE else View.GONE
            btnDoLogin.text = "Ajouter et connecter"
            etLogin.requestFocus()
        }

        if (knownUsers.isNotEmpty()) {
            spinnerSavedAccounts.adapter = ArrayAdapter(
                this,
                R.layout.item_spinner_selected,
                knownUsers
            ).also { adapter ->
                adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
            }

            val currentUser = tokenStore.getUsername()
            val currentIndex = knownUsers.indexOf(currentUser)
            if (currentIndex >= 0) {
                spinnerSavedAccounts.setSelection(currentIndex)
                etLogin.setText(knownUsers[currentIndex])
            } else {
                etLogin.setText(knownUsers.first())
            }

            spinnerSavedAccounts.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    etLogin.setText(knownUsers[position])
                    etPassword.text?.clear()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

            btnAddConnection.setOnClickListener {
                etLogin.text?.clear()
                etPassword.text?.clear()
                switchToManualMode()
            }
            btnBackToSaved.setOnClickListener { switchToTokenMode() }
            switchToTokenMode()
        } else {
            switchToManualMode()
        }

        btnDoLogin.setOnClickListener {
            if (!manualMode) {
                val selectedUser = spinnerSavedAccounts.selectedItem?.toString()?.trim().orEmpty()
                if (selectedUser.isBlank()) {
                    Toast.makeText(this, "Choisis un compte", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (tokenStore.activateUser(selectedUser)) {
                    startActivity(Intent(this, DevicesActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Token introuvable pour ce compte", Toast.LENGTH_SHORT).show()
                    switchToManualMode()
                }
                return@setOnClickListener
            }

            val login = etLogin.text.toString().trim()
            val password = etPassword.text.toString()
            if (login.isEmpty()) {
                Toast.makeText(this, "Renseigne le login", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isBlank()) {
                Toast.makeText(this, "Renseigne le mot de passe", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnDoLogin.isEnabled = false

            api.post<AuthRequest, AuthResponse>(
                ApiRoutes.AUTH,
                AuthRequest(login, password),
                onSuccess = { code, body ->
                    Log.d("API", "AUTH code=$code body=$body")
                    btnDoLogin.isEnabled = true

                    if (code in 200..299 && body?.token?.isNotBlank() == true) {
                        val token = body.token
                        tokenStore.saveSession(login, token)
                        startActivity(Intent(this, DevicesActivity::class.java))
                        finish()
                    } else {
                        val msg = when (code) {
                            401 -> "Identifiants invalides"
                            400 -> "Requête invalide"
                            else -> "Erreur connexion ($code)"
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}
