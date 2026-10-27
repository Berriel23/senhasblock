package com.senhasblock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    hasRecoveryKey: Boolean,
    dialog: SettingsDialog?,
    dialogError: String?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onLockNow: () -> Unit,
    onOpenDialog: (SettingsDialog) -> Unit,
    onCloseDialog: () -> Unit,
    onChangePin: (current: String, new: String, confirm: String) -> Unit,
    onChangeKeyword: (current: String, keyword: String) -> Unit,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Text(
                "Segurança",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            )
            ListItem(
                headlineContent = { Text("Trocar PIN") },
                supportingContent = { Text("Pede o PIN atual para confirmar") },
                leadingContent = { Icon(Icons.Filled.Password, null) },
                modifier = Modifier.clickable { onOpenDialog(SettingsDialog.ChangePin) },
            )
            ListItem(
                headlineContent = { Text("Palavra-chave de segurança") },
                supportingContent = {
                    Text(
                        if (hasRecoveryKey) "Cadastrada. Usada no \"Esqueci meu PIN\""
                        else "Não cadastrada. Sem ela não dá para recuperar o PIN"
                    )
                },
                leadingContent = { Icon(Icons.Filled.Key, null) },
                modifier = Modifier.clickable { onOpenDialog(SettingsDialog.ChangeKeyword) },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ListItem(
                headlineContent = { Text("Bloquear agora") },
                supportingContent = { Text("O app também bloqueia sozinho ao sair da tela") },
                leadingContent = { Icon(Icons.Filled.Lock, null) },
                modifier = Modifier.clickable(onClick = onLockNow),
            )
        }
    }

    when (dialog) {
        SettingsDialog.ChangePin -> ChangePinDialog(dialogError, onChangePin, onCloseDialog)
        SettingsDialog.ChangeKeyword -> ChangeKeywordDialog(dialogError, onChangeKeyword, onCloseDialog)
        null -> Unit
    }
}

@Composable
private fun ChangePinDialog(
    error: String?,
    onConfirm: (current: String, new: String, confirm: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trocar PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(current, { current = it }, "PIN atual", imeAction = ImeAction.Next)
                PinField(newPin, { newPin = it }, "Novo PIN", imeAction = ImeAction.Next)
                PinField(confirm, { confirm = it }, "Confirme o novo PIN")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current, newPin, confirm) }, enabled = current.length >= 4) {
                Text("Salvar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ChangeKeywordDialog(
    error: String?,
    onConfirm: (current: String, keyword: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var keyword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Palavra-chave de segurança") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ela permite criar um PIN novo na tela de bloqueio, em \"Esqueci meu PIN\".")
                PinField(current, { current = it }, "PIN atual", imeAction = ImeAction.Next)
                SecretField(
                    keyword,
                    { keyword = it },
                    "Nova palavra-chave",
                    supportingText = "Mínimo de 4 caracteres. Não diferencia maiúsculas.",
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current, keyword) }, enabled = current.length >= 4) {
                Text("Salvar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
