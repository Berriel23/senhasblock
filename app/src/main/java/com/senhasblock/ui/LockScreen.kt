package com.senhasblock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** O que a tela de bloqueio está mostrando. */
enum class LockMode { Setup, Unlock, Recovery, NewPin }

@Composable
fun LockScreen(
    mode: LockMode,
    error: String?,
    biometricAvailable: Boolean,
    onCreatePin: (pin: String, confirm: String, keyword: String) -> Unit,
    onUnlock: (pin: String) -> Unit,
    onBiometric: () -> Unit,
    onForgotPin: () -> Unit,
    onVerifyKeyword: (keyword: String) -> Unit,
    onResetPin: (pin: String, confirm: String) -> Unit,
    onCancelRecovery: () -> Unit,
) {
    var pin by remember(mode) { mutableStateOf("") }
    var confirm by remember(mode) { mutableStateOf("") }
    var keyword by remember(mode) { mutableStateOf("") }

    val submit = {
        when (mode) {
            LockMode.Setup -> onCreatePin(pin, confirm, keyword)
            LockMode.Unlock -> onUnlock(pin)
            LockMode.Recovery -> onVerifyKeyword(keyword)
            LockMode.NewPin -> onResetPin(pin, confirm)
        }
        pin = ""
        confirm = ""
        if (mode == LockMode.Recovery) keyword = ""
    }

    if (mode == LockMode.Recovery || mode == LockMode.NewPin) BackHandler(onBack = onCancelRecovery)

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(
                when (mode) {
                    LockMode.Recovery -> "Esqueci meu PIN"
                    LockMode.NewPin -> "Novo PIN"
                    else -> "Senhas Block"
                },
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (mode) {
                    LockMode.Setup -> "Crie um PIN de 4 a 8 dígitos e uma palavra-chave de segurança. " +
                        "A palavra-chave serve para criar um PIN novo se você esquecer o atual."
                    LockMode.Unlock -> "Digite seu PIN para desbloquear"
                    LockMode.Recovery -> "Digite sua palavra-chave de segurança para criar um PIN novo."
                    LockMode.NewPin -> "Palavra-chave confirmada. Crie seu novo PIN."
                },
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            if (mode == LockMode.Recovery) {
                SecretField(keyword, { keyword = it }, "Palavra-chave de segurança", onDone = submit)
            } else {
                val needsConfirm = mode == LockMode.Setup || mode == LockMode.NewPin
                PinField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = if (mode == LockMode.NewPin) "Novo PIN" else "PIN",
                    imeAction = if (needsConfirm) ImeAction.Next else ImeAction.Done,
                    onDone = submit,
                )
                if (needsConfirm) {
                    Spacer(Modifier.height(8.dp))
                    PinField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = "Confirme o PIN",
                        imeAction = if (mode == LockMode.Setup) ImeAction.Next else ImeAction.Done,
                        onDone = submit,
                    )
                }
                if (mode == LockMode.Setup) {
                    Spacer(Modifier.height(8.dp))
                    SecretField(
                        keyword,
                        { keyword = it },
                        "Palavra-chave de segurança",
                        supportingText = "Mínimo de 4 caracteres. Não diferencia maiúsculas.",
                        onDone = submit,
                    )
                }
            }

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = submit,
                enabled = if (mode == LockMode.Recovery) keyword.isNotBlank() else pin.length >= 4,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when (mode) {
                        LockMode.Setup -> "Criar PIN"
                        LockMode.Unlock -> "Desbloquear"
                        LockMode.Recovery -> "Verificar"
                        LockMode.NewPin -> "Salvar novo PIN"
                    }
                )
            }

            if (mode == LockMode.Unlock) {
                if (biometricAvailable) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onBiometric, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Fingerprint, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Usar biometria")
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onForgotPin) { Text("Esqueci meu PIN") }
            }
            if (mode == LockMode.Recovery || mode == LockMode.NewPin) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancelRecovery) { Text("Voltar") }
            }
        }
    }
}

@Composable
internal fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(8)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Campo de texto oculto com botão de olho, usado para a palavra-chave. */
@Composable
internal fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    onDone: () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    if (visible) "Ocultar palavra-chave" else "Mostrar palavra-chave",
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}
