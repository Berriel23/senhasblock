package com.senhasblock.security

import android.content.Context
import java.security.MessageDigest

/** Guarda apenas o hash do PIN (PBKDF2 + salt), nunca o PIN em si. */
class PinManager(context: Context) {
    private val prefs = context.getSharedPreferences("pin", Context.MODE_PRIVATE)

    fun hasPin(): Boolean = prefs.contains(KEY_HASH)

    fun setPin(pin: String) {
        val salt = Crypto.randomBytes(16)
        prefs.edit()
            .putString(KEY_SALT, Crypto.encode(salt))
            .putString(KEY_HASH, Crypto.encode(hash(pin, salt)))
            .apply()
    }

    fun verify(pin: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null)?.let(Crypto::decode) ?: return false
        val stored = prefs.getString(KEY_HASH, null)?.let(Crypto::decode) ?: return false
        return MessageDigest.isEqual(hash(pin, salt), stored)
    }

    private fun hash(pin: String, salt: ByteArray) = Crypto.pbkdf2(pin.toCharArray(), salt, ITERATIONS)

    private companion object {
        const val KEY_SALT = "salt"
        const val KEY_HASH = "hash"
        const val ITERATIONS = 120_000
    }
}
