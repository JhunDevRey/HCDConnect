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
import com.hcdc.hcdconnect.databinding.ActivityRegisterBinding
import kotlinx.coroutines.launch

/** Sign-up screen, opened from the login screen. Back, or "Already have an account?", returns to login. */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: RegisterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        with(binding) {
            // Carry over whatever was typed on the login screen.
            if (savedInstanceState == null) editEmail.setText(intent.getStringExtra(EXTRA_EMAIL))

            editFullName.doAfterTextChanged { viewModel.onFullNameEdited() }
            editEmail.doAfterTextChanged { viewModel.onEmailEdited() }
            editPassword.doAfterTextChanged { viewModel.onPasswordEdited() }
            editConfirmPassword.doAfterTextChanged { viewModel.onConfirmPasswordEdited() }

            buttonRegister.setOnClickListener {
                viewModel.register(
                    fullName = editFullName.text.toString(),
                    email = editEmail.text.toString(),
                    password = editPassword.text.toString(),
                    confirmPassword = editConfirmPassword.text.toString()
                )
            }
            buttonGoToLogin.setOnClickListener { finish() }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: RegisterUiState) = with(binding) {
        if (state.isRegistered) {
            // Opens as a new task, so Back from the dashboard doesn't return to login or register.
            startActivity(MainActivity.newIntent(this@RegisterActivity))
            finish()
            return@with
        }

        layoutFullName.error = state.fullNameError?.let(::getString)
        layoutEmail.error = state.emailError?.let(::getString)
        layoutPassword.error = state.passwordError?.let(::getString)
        layoutConfirmPassword.error = state.confirmPasswordError?.let(::getString)

        progressIndicator.isVisible = state.isLoading
        buttonRegister.isEnabled = !state.isLoading
        buttonGoToLogin.isEnabled = !state.isLoading

        state.message?.let {
            Snackbar.make(root, it, Snackbar.LENGTH_LONG).show()
            viewModel.onMessageShown()
        }
    }

    companion object {
        private const val EXTRA_EMAIL = "email"

        fun newIntent(context: Context, email: String? = null): Intent =
            Intent(context, RegisterActivity::class.java).putExtra(EXTRA_EMAIL, email)
    }
}
