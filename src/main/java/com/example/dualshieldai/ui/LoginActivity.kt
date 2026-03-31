package com.example.dualshieldai.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dualshieldai.R
import com.example.dualshieldai.utils.DatabaseHelper
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        dbHelper = DatabaseHelper(this)

        val prefs = getSharedPreferences("DualShieldPrefs", MODE_PRIVATE)

        val emailInput = findViewById<TextInputEditText>(R.id.etEmail)
        val passwordInput = findViewById<TextInputEditText>(R.id.etPassword)
        val loginButton = findViewById<Button>(R.id.btnLogin)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)

        loginButton.setOnClickListener {

            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Thread {
                val role = dbHelper.checkUser(email, password)

                runOnUiThread {

                    if (role != null) {

                        prefs.edit().putString("USER_EMAIL", email).apply()
                        prefs.edit().putString("USER_ROLE", role).apply()

                        val intent = if (role.equals("Worker", true)) {
                            Intent(this, WorkerDashboardActivity::class.java)
                        } else {
                            Intent(this, SupervisorDashboardActivity::class.java)
                        }

                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK

                        startActivity(intent)
                        finish()

                    } else {
                        Toast.makeText(this, "Invalid Credentials", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}