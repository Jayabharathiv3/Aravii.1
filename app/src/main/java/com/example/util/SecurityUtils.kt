package com.example.util

import java.security.MessageDigest

object SecurityUtils {
    private const val SALT = "ai_biz_consultant_secure_salt_2026"

    fun hashPassword(password: String): String {
        val input = "$SALT$password"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPassword(password: String, storedHash: String): Boolean {
        val calculated = hashPassword(password)
        return calculated.equals(storedHash, ignoreCase = true)
    }
}
