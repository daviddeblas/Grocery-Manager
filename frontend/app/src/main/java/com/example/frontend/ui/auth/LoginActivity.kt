package com.example.frontend.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.frontend.R
import com.example.frontend.databinding.ActivityLoginBinding
import com.example.frontend.ui.activities.ShoppingListActivity
import com.example.frontend.viewmodel.AuthViewModel
import kotlinx.coroutines.launch
import com.example.frontend.services.SyncScheduler

/**
 * LoginActivity allows users to log in to the app.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        // Check if the user is already logged in
        if (viewModel.isLoggedIn()) {
            navigateToMain()
            return
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            if (validateForm()) {
                login()
            }
        }

        // Link to registration
        binding.tvSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        // Link to forgot password
        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true

        if (binding.etUsername.text.isNullOrBlank()) {
            binding.tilUsername.error = getString(R.string.error_username_required)
            isValid = false
        } else {
            binding.tilUsername.error = null
        }

        if (binding.etPassword.text.isNullOrBlank()) {
            binding.tilPassword.error = getString(R.string.error_password_required)
            isValid = false
        } else {
            binding.tilPassword.error = null
        }

        return isValid
    }

    private fun login() {
        binding.progressBar.visibility = View.VISIBLE

        binding.btnLogin.isEnabled = false
        binding.tvSignup.isEnabled = false
        binding.tvForgotPassword.isEnabled = false

        val username = binding.etUsername.text.toString()
        val password = binding.etPassword.text.toString()

        lifecycleScope.launch {
            try {
                val result = viewModel.login(username, password)

                result.fold(
                    onSuccess = {
                        Toast.makeText(this@LoginActivity, R.string.login_success, Toast.LENGTH_SHORT).show()

                        // Trigger synchronization after login
                        SyncScheduler.requestImmediateSync(this@LoginActivity)

                        navigateToMain()
                    },
                    onFailure = { e ->
                        val errorMessage = when {
                            e.message?.contains("404") == true -> getString(R.string.error_account_not_found)
                            e.message?.contains("401") == true -> getString(R.string.error_wrong_password)
                            else -> e.message ?: getString(R.string.login_failed)
                        }

                        Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_LONG).show()
                        binding.btnLogin.isEnabled = true
                        binding.tvSignup.isEnabled = true
                        binding.tvForgotPassword.isEnabled = true
                        binding.progressBar.visibility = View.GONE
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, R.string.network_error, Toast.LENGTH_LONG).show()
                binding.btnLogin.isEnabled = true
                binding.tvSignup.isEnabled = true
                binding.tvForgotPassword.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, ShoppingListActivity::class.java))
        finish()
    }
}