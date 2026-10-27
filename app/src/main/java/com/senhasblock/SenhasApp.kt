package com.senhasblock

import android.app.Application
import com.senhasblock.data.PasswordStore

class SenhasApp : Application() {
    // Aberto só no primeiro acesso, fora da thread principal (ver MainViewModel.entries)
    val store: PasswordStore by lazy { PasswordStore(this) }
}
