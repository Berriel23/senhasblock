package com.senhasblock.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

@Composable
fun AppRoot(
    viewModel: MainViewModel,
    biometricAvailable: Boolean,
    onBiometric: () -> Unit,
) {
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val pinError by viewModel.pinError.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    var exportUri by remember { mutableStateOf<Uri?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        viewModel.externalActivityFinished()
        exportUri = it
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        viewModel.externalActivityFinished()
        importUri = it
    }

    when (lockState) {
        LockState.NeedsSetup, LockState.Locked -> LockScreen(
            isSetup = lockState == LockState.NeedsSetup,
            error = pinError,
            biometricAvailable = biometricAvailable,
            onCreatePin = viewModel::createPin,
            onUnlock = viewModel::unlockWithPin,
            onBiometric = onBiometric,
        )

        LockState.Unlocked -> when (val current = screen) {
            Screen.List -> ListScreen(
                entries = entries,
                query = query,
                snackbarHostState = snackbarHostState,
                onQueryChange = viewModel::setQuery,
                onAdd = { viewModel.openEditor(null) },
                onEdit = { viewModel.openEditor(it) },
                onDelete = { viewModel.delete(it) },
                onCopyUsername = viewModel::copyUsername,
                onCopyPassword = viewModel::copyPassword,
                onExport = {
                    viewModel.expectExternalActivity()
                    exportLauncher.launch("senhas-block-backup-${LocalDate.now()}.json")
                },
                onImport = {
                    viewModel.expectExternalActivity()
                    importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                },
                onLock = viewModel::lock,
            )

            is Screen.Editor -> EditScreen(
                draft = current.draft,
                snackbarHostState = snackbarHostState,
                onSave = { viewModel.save(current.draft) },
                onBack = viewModel::closeEditor,
            )
        }
    }

    if (lockState == LockState.Unlocked) {
        exportUri?.let { uri ->
            BackupPasswordDialog(
                isExport = true,
                onConfirm = { viewModel.exportBackup(uri, it); exportUri = null },
                onDismiss = { exportUri = null },
            )
        }
        importUri?.let { uri ->
            BackupPasswordDialog(
                isExport = false,
                onConfirm = { viewModel.importBackup(uri, it); importUri = null },
                onDismiss = { importUri = null },
            )
        }
    }
}

@Composable
private fun BackupPasswordDialog(
    isExport: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isExport) "Senha do backup" else "Abrir backup") },
        text = {
            Column {
                Text(
                    if (isExport) "Crie uma senha para proteger o arquivo. Sem ela não será possível restaurar o backup."
                    else "Digite a senha usada ao criar o backup."
                )
                BackupField(password, { password = it }, "Senha")
                if (isExport) BackupField(confirm, { confirm = it }, "Confirme a senha")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    isExport && password.length < 8 -> "Use pelo menos 8 caracteres"
                    isExport && password != confirm -> "As senhas não conferem"
                    password.isEmpty() -> "Digite a senha"
                    else -> null
                }
                if (error == null) onConfirm(password)
            }) { Text(if (isExport) "Salvar" else "Importar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun BackupField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}
