package com.senhasblock

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.senhasblock.ui.AppRoot
import com.senhasblock.ui.MainViewModel
import com.senhasblock.ui.theme.SenhasBlockTheme

// FragmentActivity (e não ComponentActivity) porque o BiometricPrompt precisa dela
class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    // Bloqueia o app sempre que ele sai da tela
    private val backgroundObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) = viewModel.onAppBackgrounded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Impede prints/gravação de tela e esconde o conteúdo na lista de apps recentes
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        ProcessLifecycleOwner.get().lifecycle.addObserver(backgroundObserver)

        setContent {
            SenhasBlockTheme {
                AppRoot(
                    viewModel = viewModel,
                    biometricAvailable = canUseBiometric(),
                    onBiometric = ::showBiometricPrompt,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (canUseBiometric() && viewModel.consumeAutoBiometric()) showBiometricPrompt()
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(backgroundObserver)
        super.onDestroy()
    }

    private fun canUseBiometric(): Boolean =
        BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    private fun showBiometricPrompt() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.unlockWithBiometric()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear Senhas Block")
            .setSubtitle("Use sua digital ou reconhecimento facial")
            .setNegativeButtonText("Usar PIN")
            .setAllowedAuthenticators(BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }
}
