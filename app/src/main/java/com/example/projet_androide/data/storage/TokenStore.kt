package com.example.projet_androide.data.storage

import android.content.Context

class TokenStore(context: Context) {

    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val usersPrefs = context.getSharedPreferences("auth_users", Context.MODE_PRIVATE)
    private val usersKey = "known_users"
    private val tokenPrefix = "token_"

    fun saveToken(token: String) {
        prefs.edit().putString("token", token).apply()
    }

    fun getToken(): String? {
        return prefs.getString("token", null)
    }

    fun saveUsername(username: String) {
        prefs.edit().putString("username", username).apply()
    }

    fun getUsername(): String? {
        return prefs.getString("username", null)
    }

    fun clearToken() {
        prefs.edit().remove("token").remove("username").apply()
    }

    fun saveSession(username: String, token: String) {
        val login = username.trim()
        if (login.isBlank()) return
        usersPrefs.edit()
            .putString(tokenPrefix + login, token)
            .apply()
        val knownUsers = getKnownUsers().toMutableSet()
        knownUsers.add(login)
        usersPrefs.edit().putStringSet(usersKey, knownUsers).apply()
        saveUsername(login)
        saveToken(token)
    }

    fun getKnownUsers(): List<String> {
        val users = usersPrefs.getStringSet(usersKey, emptySet()).orEmpty()
        return users.filter { it.isNotBlank() }.sortedBy { it.lowercase() }
    }

    fun getTokenForUser(username: String): String? {
        val login = username.trim()
        if (login.isBlank()) return null
        return usersPrefs.getString(tokenPrefix + login, null)
    }

    fun activateUser(username: String): Boolean {
        val token = getTokenForUser(username) ?: return false
        saveUsername(username.trim())
        saveToken(token)
        return true
    }

    fun clearActiveSession() {
        prefs.edit().remove("token").remove("username").apply()
    }
}
