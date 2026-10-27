package com.senhasblock.security

import com.senhasblock.data.PasswordEntry
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class WrongBackupPasswordException : Exception()

/**
 * Arquivo de backup: JSON com salt, IV e os dados cifrados em AES-256-GCM.
 * A chave vem da senha do backup, então o arquivo pode ser restaurado em outro celular.
 */
object BackupCrypto {
    private const val FORMAT = "senhas-block-backup"
    private const val VERSION = 1
    private const val ITERATIONS = 210_000

    fun encrypt(entries: List<PasswordEntry>, password: CharArray): String {
        val salt = Crypto.randomBytes(16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt))
        val data = cipher.doFinal(toJson(entries).toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("salt", Crypto.encode(salt))
            .put("iv", Crypto.encode(cipher.iv))
            .put("data", Crypto.encode(data))
            .toString()
    }

    fun decrypt(file: String, password: CharArray): List<PasswordEntry> {
        val root = JSONObject(file)
        require(root.optString("format") == FORMAT) { "Arquivo não é um backup do Senhas Block" }
        val salt = Crypto.decode(root.getString("salt"))
        val iv = Crypto.decode(root.getString("iv"))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        val plain = try {
            cipher.doFinal(Crypto.decode(root.getString("data")))
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongBackupPasswordException()
        }
        return fromJson(String(plain, Charsets.UTF_8))
    }

    private fun deriveKey(password: CharArray, salt: ByteArray) =
        SecretKeySpec(Crypto.pbkdf2(password, salt, ITERATIONS), "AES")

    private fun toJson(entries: List<PasswordEntry>): String {
        val array = JSONArray()
        entries.forEach {
            array.put(
                JSONObject()
                    .put("name", it.name)
                    .put("username", it.username)
                    .put("password", it.password)
                    .put("description", it.description)
                    .put("updatedAt", it.updatedAt)
            )
        }
        return array.toString()
    }

    private fun fromJson(json: String): List<PasswordEntry> {
        val array = JSONArray(json)
        return List(array.length()) { i ->
            val item = array.getJSONObject(i)
            PasswordEntry(
                name = item.getString("name"),
                username = item.optString("username"),  // ausente em backups antigos
                password = item.getString("password"),
                description = item.optString("description"),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
            )
        }
    }
}
