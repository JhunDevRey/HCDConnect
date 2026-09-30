package com.hcdc.hcdconnect.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.hcdc.hcdconnect.MainActivity
import com.hcdc.hcdconnect.R
import com.hcdc.hcdconnect.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        with(binding) {
            editEmail.doAfterTextChanged { viewModel.onEmailEdited() }
            editPassword.doAfterTextChanged { viewModel.onPasswordEdited() }
            editConfirmPassword.doAfterTextChanged { viewModel.onConfirmPasswordEdited() }

            buttonSubmit.setOnClickListener {
                viewModel.submit(
                    email = editEmail.text.toString(),
                    password = editPassword.text.toString(),
                    confirmPassword = editConfirmPassword.text.toString()
                )
            }
            buttonForgotPassword.setOnClickListener {
                viewModel.sendPasswordReset(editEmail.text.toString())
            }
            buttonToggleMode.setOnClickListener {
                editConfirmPassword.text = null
                viewModel.toggleMode()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: LoginUiState) = with(binding) {
        if (state.isSignedIn) {
            startActivity(MainActivity.newIntent(this@LoginActivity))
            finish()
            return@with
        }

        val isSignUp = state.mode == AuthMode.SIGN_UP
        textHeading.setText(if (isSignUp) R.string.create_account_heading else R.string.sign_in_heading)
        buttonSubmit.setText(if (isSignUp) R.string.create_account else R.string.sign_in)
        buttonToggleMode.setText(if (isSignUp) R.string.have_account_prompt else R.string.no_account_prompt)
        layoutConfirmPassword.isVisible = isSignUp
        buttonForgotPassword.isVisible = !isSignUp

        layoutEmail.error = state.emailError?.let(::getString)
        layoutPassword.error = state.passwordError?.let(::getString)
        layoutConfirmPassword.error = state.confirmPasswordError?.let(::getString)

        progressIndicator.isVisible = state.isLoading
        buttonSubmit.isEnabled = !state.isLoading
        buttonForgotPassword.isEnabled = !state.isLoading
        buttonToggleMode.isEnabled = !state.isLoading

        state.message?.let {
            Snackbar.make(root, it, Snackbar.LENGTH_LONG).show()
            viewModel.onMessageShown()
        }
    }

    companion object {
        /** Opens the login screen as a fresh task, e.g. after signing out. */
        fun newIntent(context: Context): Intent =
            Intent(context, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
