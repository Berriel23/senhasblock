package com.senhasblock.data

data class PasswordEntry(
    val id: Long = 0,
    val name: String,
    val username: String = "",
    val password: String,
    val description: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
