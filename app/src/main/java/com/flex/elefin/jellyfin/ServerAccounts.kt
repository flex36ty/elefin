package com.flex.elefin.jellyfin

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest

internal fun serverAccountId(url: String, userId: String): String =
    MessageDigest.getInstance("SHA-256").digest(
        (url.trim().trimEnd('/') + "\n" + userId).toByteArray(Charsets.UTF_8)
    ).joinToString("") { "%02x".format(it) }

data class SavedServerAccount(val id: String, val serverName: String, val serverUrl: String, val username: String)

/** Login tokens remain in app-private preferences; only account labels are exposed to the UI. */
class ServerAccounts internal constructor(
    private val prefs: android.content.SharedPreferences,
    private val activeConfig: JellyfinConfig
) {
    constructor(context: Context) : this(
        context.getSharedPreferences("jellyfin_accounts", Context.MODE_PRIVATE), JellyfinConfig(context)
    )
    private val gson = Gson()
    private fun read(): MutableMap<String, Map<String, String>> {
        val type = object : TypeToken<MutableMap<String, Map<String, String>>>() {}.type
        return gson.fromJson<MutableMap<String, Map<String, String>>>(prefs.getString("accounts", "{}"), type)
            ?: mutableMapOf()
    }
    private fun write(accounts: Map<String, Map<String, String>>) {
        check(prefs.edit().putString("accounts", gson.toJson(accounts)).commit())
    }
    fun activeId(): String? {
        val config = activeConfig
        return if (config.isConfigured()) serverAccountId(config.serverUrl, config.userId) else null
    }
    fun save(config: JellyfinConfig = activeConfig) {
        if (!config.isConfigured()) return
        val accounts = read()
        accounts[serverAccountId(config.serverUrl, config.userId)] = config.snapshot()
        write(accounts)
    }
    fun list(): List<SavedServerAccount> {
        save() // Migrate the existing single-server session without another login.
        return read().map { (id, v) ->
            SavedServerAccount(id, v["server_name"].orEmpty().ifBlank { "Jellyfin Server" },
                v["server_url"].orEmpty(), v["username"].orEmpty())
        }.sortedWith(compareBy({ it.serverName }, { it.username }))
    }
    fun activate(id: String): Boolean {
        save()
        val account = read()[id] ?: return false
        if (account["access_token"].isNullOrBlank() || account["user_id"].isNullOrBlank()) return false
        activeConfig.replace(account)
        return true
    }
    fun remove(id: String) {
        val accounts = read()
        accounts.remove(id)
        write(accounts)
        if (activeId() == id) activeConfig.clearAuth()
    }
    fun forgetActive() { activeId()?.let { remove(it) } }
}
