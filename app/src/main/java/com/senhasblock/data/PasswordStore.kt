package com.senhasblock.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.senhasblock.security.KeyManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Banco SQLCipher (SQLite criptografado com AES-256). Todas as funções bloqueiam,
 * então devem ser chamadas fora da thread principal.
 * A ordenação alfabética é feita no ViewModel com Collator pt-BR, que trata acentos
 * corretamente (o SQLite ordenaria "Á" depois do "Z").
 */
class PasswordStore(context: Context) {
    private val helper: SupportSQLiteOpenHelper
    private val cache = MutableStateFlow<List<PasswordEntry>?>(null)

    init {
        System.loadLibrary("sqlcipher")
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("senhas.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE entries (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "name TEXT NOT NULL, " +
                            "username TEXT NOT NULL DEFAULT '', " +
                            "password TEXT NOT NULL, " +
                            "description TEXT NOT NULL, " +
                            "updated_at INTEGER NOT NULL)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    // Versão 2: campo "usuário"
                    if (oldVersion < 2) db.execSQL("ALTER TABLE entries ADD COLUMN username TEXT NOT NULL DEFAULT ''")
                }
            })
            .build()
        helper = SupportOpenHelperFactory(KeyManager.getDatabasePassphrase(context)).create(config)
    }

    fun observeAll(): Flow<List<PasswordEntry>> = flow {
        if (cache.value == null) refresh()
        emitAll(cache.filterNotNull())
    }

    fun getAll(): List<PasswordEntry> =
        helper.readableDatabase.query("SELECT id, name, username, password, description, updated_at FROM entries").use { c ->
            List(c.count) {
                c.moveToNext()
                PasswordEntry(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getLong(5))
            }
        }

    fun upsert(entry: PasswordEntry) {
        val db = helper.writableDatabase
        if (entry.id == 0L) {
            db.insert(TABLE, SQLiteDatabase.CONFLICT_ABORT, entry.toValues())
        } else {
            db.update(TABLE, SQLiteDatabase.CONFLICT_ABORT, entry.toValues(), "id = ?", arrayOf(entry.id))
        }
        refresh()
    }

    fun insertAll(entries: List<PasswordEntry>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            entries.forEach { db.insert(TABLE, SQLiteDatabase.CONFLICT_ABORT, it.toValues()) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refresh()
    }

    fun delete(entry: PasswordEntry) {
        helper.writableDatabase.delete(TABLE, "id = ?", arrayOf(entry.id))
        refresh()
    }

    private fun refresh() {
        cache.value = getAll()
    }

    private fun PasswordEntry.toValues() = ContentValues().apply {
        put("name", name)
        put("username", username)
        put("password", password)
        put("description", description)
        put("updated_at", updatedAt)
    }

    private companion object {
        const val TABLE = "entries"
    }
}
