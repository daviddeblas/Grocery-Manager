package com.example.frontend.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.frontend.R
import com.example.frontend.databinding.ActivityForgotPasswordBinding
import com.example.frontend.viewmodel.AuthViewModel
import kotlinx.coroutines.launch
import java.util.regex.Pattern

/**
 * Activity for handling forgotten passwords.
 * Allows users to request their credentials be sent to their email.
 */
class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private lateinit var viewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Back to login button
        binding.tvBackToLogin.setOnClickListener {
            finish()
        }

        // Send credentials button
        binding.btnSendCredentials.setOnClickListener {
            if (validateEmail()) {
                sendCredentials()
            }
        }
    }

    private fun validateEmail(): Boolean {
        val email = binding.etEmail.text.toString().trim()

        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.error_email_required)
            return false
        } else if (!isValidEmail(email)) {
            binding.tilEmail.error = getString(R.string.error_invalid_email)
            return false
        } else {
            binding.tilEmail.error = null
            return true
        }
    }

    private fun isValidEmail(email: String): Boolean {
        val pattern = Pattern.compile(
            "[a-zA-Z0-9+._%\\-]{1,256}" +
                    "@" +
                    "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
                    "(" +
                    "\\." +
                    "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
                    ")+"
        )
        return pattern.matcher(email).matches()
    }

    private fun sendCredentials() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSendCredentials.isEnabled = false

        val email = binding.etEmail.text.toString().trim()

        lifecycleScope.launch {
            try {
                val result = viewModel.sendCredentials(email)

                result.fold(
                    onSuccess = {
                        Toast.makeText(this@ForgotPasswordActivity, it.message, Toast.LENGTH_LONG).show()
                        finish() // Return to login screen
                    },
                    onFailure = { e ->
                        Toast.makeText(this@ForgotPasswordActivity, e.message ?: getString(R.string.network_error), Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(this@ForgotPasswordActivity, R.string.network_error, Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnSendCredentials.isEnabled = true
            }
        }
    }
}