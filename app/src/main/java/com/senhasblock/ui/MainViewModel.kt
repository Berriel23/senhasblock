package com.senhasblock.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.senhasblock.SenhasApp
import com.senhasblock.data.PasswordEntry
import com.senhasblock.security.BackupCrypto
import com.senhasblock.security.PinManager
import com.senhasblock.security.WrongBackupPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

enum class LockState { NeedsSetup, Locked, Unlocked }

/** Rascunho do formulário; fica no ViewModel para sobreviver à rotação da tela. */
class EditorDraft(val original: PasswordEntry?) {
    var name by mutableStateOf(original?.name.orEmpty())
    var username by mutableStateOf(original?.username.orEmpty())
    var password by mutableStateOf(original?.password.orEmpty())
    var description by mutableStateOf(original?.description.orEmpty())
}

sealed interface Screen {
    data object List : Screen
    data class Editor(val draft: EditorDraft) : Screen
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store by lazy { (app as SenhasApp).store }
    private val pinManager = PinManager(app)
    private val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR")).apply {
        strength = Collator.PRIMARY
    }

    private val _lockState = MutableStateFlow(if (pinManager.hasPin()) LockState.Locked else LockState.NeedsSetup)
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.List)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Lista filtrada pela busca e ordenada por nome. Só lê o banco depois do desbloqueio. */
    val entries: StateFlow<List<PasswordEntry>> = combine(
        _lockState.flatMapLatest { if (it == LockState.Unlocked) store.observeAll() else flowOf(emptyList()) },
        _query,
    ) { list, query ->
        val search = normalize(query.trim())
        list.filter { search.isEmpty() || normalize(it.name).contains(search) }
            .sortedWith(compareBy(collator) { it.name })
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var failedAttempts = 0
    private var lockoutUntil = 0L
    private var autoBiometricPending = _lockState.value == LockState.Locked
    private var expectingExternalActivity = false
    private var clipboardClearJob: Job? = null

    // ---- Trava ----

    fun createPin(pin: String, confirm: String) {
        when {
            pin.length !in 4..8 -> _pinError.value = "O PIN deve ter de 4 a 8 dígitos"
            pin != confirm -> _pinError.value = "Os PINs não conferem"
            else -> viewModelScope.launch {
                withContext(Dispatchers.Default) { pinManager.setPin(pin) }
                unlock()
            }
        }
    }

    fun unlockWithPin(pin: String) {
        val now = SystemClock.elapsedRealtime()
        if (now < lockoutUntil) {
            _pinError.value = "Muitas tentativas. Aguarde ${(lockoutUntil - now) / 1000 + 1} s"
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) { pinManager.verify(pin) }
            if (ok) {
                unlock()
            } else {
                failedAttempts++
                if (failedAttempts >= MAX_ATTEMPTS) {
                    // 30 s, 60 s, 120 s... a cada nova falha depois do limite
                    val wait = 30_000L shl (failedAttempts - MAX_ATTEMPTS).coerceAtMost(5)
                    lockoutUntil = SystemClock.elapsedRealtime() + wait
                    _pinError.value = "PIN incorreto. Aguarde ${wait / 1000} s"
                } else {
                    _pinError.value = "PIN incorreto"
                }
            }
        }
    }

    fun unlockWithBiometric() = unlock()

    private fun unlock() {
        failedAttempts = 0
        _pinError.value = null
        _lockState.value = LockState.Unlocked
    }

    fun lock() {
        if (_lockState.value != LockState.Unlocked) return
        _lockState.value = LockState.Locked
        _query.value = ""
        autoBiometricPending = true
    }

    /** Abre a biometria sozinha uma vez por bloqueio (e não de novo se o usuário cancelar). */
    fun consumeAutoBiometric(): Boolean {
        val pending = autoBiometricPending && _lockState.value == LockState.Locked
        autoBiometricPending = false
        return pending
    }

    fun onAppBackgrounded() {
        if (expectingExternalActivity) expectingExternalActivity = false else lock()
    }

    /** O seletor de arquivos tira o app da tela; não bloquear por causa dele. */
    fun expectExternalActivity() {
        expectingExternalActivity = true
    }

    fun externalActivityFinished() {
        expectingExternalActivity = false
    }

    // ---- Lista e edição ----

    fun setQuery(query: String) {
        _query.value = query
    }

    fun openEditor(entry: PasswordEntry?) {
        _screen.value = Screen.Editor(EditorDraft(entry))
    }

    fun closeEditor() {
        _screen.value = Screen.List
    }

    fun save(draft: EditorDraft) = viewModelScope.launch {
        val entry = (draft.original ?: PasswordEntry(name = "", password = "", description = "")).copy(
            name = draft.name.trim(),
            username = draft.username.trim(),
            password = draft.password,
            description = draft.description.trim(),
            updatedAt = System.currentTimeMillis(),
        )
        withContext(Dispatchers.IO) { store.upsert(entry) }
        _screen.value = Screen.List
        _messages.send(if (draft.original == null) "Senha adicionada" else "Alterações salvas")
    }

    fun delete(entry: PasswordEntry) = viewModelScope.launch {
        withContext(Dispatchers.IO) { store.delete(entry) }
        _messages.send("\"${entry.name}\" excluído")
    }

    fun copyUsername(entry: PasswordEntry) {
        setClip("Usuário", entry.username, sensitive = false)
        // Substituiu a senha copiada, então não precisa mais apagar depois
        clipboardClearJob?.cancel()
        viewModelScope.launch { _messages.send("Usuário copiado") }
    }

    fun copyPassword(entry: PasswordEntry) {
        val clipboard = setClip("Senha", entry.password, sensitive = true)
        clipboardClearJob?.cancel()
        clipboardClearJob = viewModelScope.launch {
            delay(CLIPBOARD_CLEAR_MS)
            if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
            else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        viewModelScope.launch { _messages.send("Senha copiada. Será apagada em 30 segundos") }
    }

    private fun setClip(label: String, text: String, sensitive: Boolean): ClipboardManager {
        val clipboard = getApplication<Application>().getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(label, text)
        if (sensitive) {
            // Marca como sensível: o Android 13+ não mostra o conteúdo na prévia do teclado/toast
            val sensitiveKey = if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE"
            clip.description.extras = PersistableBundle().apply { putBoolean(sensitiveKey, true) }
        }
        clipboard.setPrimaryClip(clip)
        return clipboard
    }

    // ---- Backup ----

    fun exportBackup(uri: Uri, password: String) = viewModelScope.launch {
        try {
            val count = withContext(Dispatchers.IO) {
                val all = store.getAll()
                val content = BackupCrypto.encrypt(all, password.toCharArray())
                val resolver = getApplication<Application>().contentResolver
                resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                    ?: error("Não foi possível abrir o arquivo")
                all.size
            }
            _messages.send("Backup salvo com $count senha(s)")
        } catch (e: Exception) {
            _messages.send("Falha ao salvar o backup")
        }
    }

    fun importBackup(uri: Uri, password: String) = viewModelScope.launch {
        try {
            val added = withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                val content = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: error("Não foi possível abrir o arquivo")
                val imported = BackupCrypto.decrypt(content, password.toCharArray())
                // Ignora itens idênticos aos que já existem, para importar duas vezes sem duplicar
                val existing = store.getAll().map { it.contentKey() }.toSet()
                val news = imported.filter { it.contentKey() !in existing }
                store.insertAll(news)
                news.size
            }
            _messages.send("$added senha(s) importada(s)")
        } catch (e: WrongBackupPasswordException) {
            _messages.send("Senha do backup incorreta")
        } catch (e: Exception) {
            _messages.send("Arquivo de backup inválido")
        }
    }

    private fun PasswordEntry.contentKey() = listOf(name, username, password, description)

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase(Locale.ROOT)

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val CLIPBOARD_CLEAR_MS = 30_000L
        val DIACRITICS = Regex("\\p{Mn}+")
    }
}
