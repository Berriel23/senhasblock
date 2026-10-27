package com.senhasblock.security

import android.content.Context
import java.security.MessageDigest
import java.util.Locale

/**
 * Guarda apenas hashes (PBKDF2 + salt) do PIN e da palavra-chave de segurança, nunca os valores.
 * A palavra-chave permite criar um PIN novo quando o usuário esquece o atual.
 */
class PinManager(context: Context) {
    private val prefs = context.getSharedPreferences("pin", Context.MODE_PRIVATE)

    fun hasPin(): Boolean = prefs.contains(KEY_HASH)

    fun setPin(pin: String) = save(KEY_SALT, KEY_HASH, pin)

    fun verify(pin: String): Boolean = check(KEY_SALT, KEY_HASH, pin)

    fun hasRecoveryKey(): Boolean = prefs.contains(KEY_RECOVERY_HASH)

    fun setRecoveryKey(key: String) = save(KEY_RECOVERY_SALT, KEY_RECOVERY_HASH, normalizeKey(key))

    fun verifyRecoveryKey(key: String): Boolean = check(KEY_RECOVERY_SALT, KEY_RECOVERY_HASH, normalizeKey(key))

    // Sem diferenciar maiúsculas/minúsculas nem espaços nas pontas, para evitar erro de digitação
    private fun normalizeKey(key: String) = key.trim().lowercase(Locale.ROOT)

    private fun save(saltKey: String, hashKey: String, secret: String) {
        val salt = Crypto.randomBytes(16)
        prefs.edit()
            .putString(saltKey, Crypto.encode(salt))
            .putString(hashKey, Crypto.encode(hash(secret, salt)))
            .apply()
    }

    private fun check(saltKey: String, hashKey: String, secret: String): Boolean {
        val salt = prefs.getString(saltKey, null)?.let(Crypto::decode) ?: return false
        val stored = prefs.getString(hashKey, null)?.let(Crypto::decode) ?: return false
        return MessageDigest.isEqual(hash(secret, salt), stored)
    }

    private fun hash(secret: String, salt: ByteArray) = Crypto.pbkdf2(secret.toCharArray(), salt, ITERATIONS)

    private companion object {
        const val KEY_SALT = "salt"
        const val KEY_HASH = "hash"
        const val KEY_RECOVERY_SALT = "recovery_salt"
        const val KEY_RECOVERY_HASH = "recovery_hash"
        const val ITERATIONS = 120_000
    }
}
