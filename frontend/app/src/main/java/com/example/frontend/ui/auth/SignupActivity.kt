package com.example.frontend.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.frontend.R
import com.example.frontend.api.model.SignupRequest
import com.example.frontend.databinding.ActivitySignupBinding
import com.example.frontend.viewmodel.AuthViewModel
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var viewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnSignup.setOnClickListener {
            if (validateForm()) {
                signup()
            }
        }

        binding.tvLogin.setOnClickListener {
            finish() // Return to the login screen
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

        val email = binding.etEmail.text.toString()
        if (email.isBlank()) {
            binding.tilEmail.error = getString(R.string.error_email_required)
            isValid = false
        } else if (!isValidEmail(email)) {
            binding.tilEmail.error = getString(R.string.error_invalid_email)
            isValid = false
        } else {
            binding.tilEmail.error = null
        }

        val password = binding.etPassword.text.toString()
        if (password.isBlank()) {
            binding.tilPassword.error = getString(R.string.error_password_required)
            isValid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.error_password_short)
            isValid = false
        } else {
            binding.tilPassword.error = null
        }

        return isValid
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

    private fun signup() {
        binding.progressBar.visibility = View.VISIBLE

        binding.btnSignup.isEnabled = false
        binding.tvLogin.isEnabled = false

        val username = binding.etUsername.text.toString()
        val email = binding.etEmail.text.toString()
        val password = binding.etPassword.text.toString()

        lifecycleScope.launch {
            try {
                val signupRequest = SignupRequest(username, email, password)
                val response = viewModel.signup(signupRequest)

                if (response.isSuccessful) {
                    val messageResponse = response.body()
                    Toast.makeText(this@SignupActivity, messageResponse?.message ?: getString(R.string.signup_success), Toast.LENGTH_SHORT).show()

                    // Redirect to login screen with pre-filled credentials
                    val intent = Intent(this@SignupActivity, LoginActivity::class.java)
                    intent.putExtra("username", username)
                    startActivity(intent)
                    finish()
                } else {
                    val errorBody = response.errorBody()?.string() ?: getString(R.string.signup_failed)
                    Toast.makeText(this@SignupActivity, errorBody, Toast.LENGTH_LONG).show()
                    binding.btnSignup.isEnabled = true
                    binding.tvLogin.isEnabled = true
                }
            } catch (e: Exception) {
                Toast.makeText(this@SignupActivity, R.string.network_error, Toast.LENGTH_LONG).show()
                binding.btnSignup.isEnabled = true
                binding.tvLogin.isEnabled = true
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
}