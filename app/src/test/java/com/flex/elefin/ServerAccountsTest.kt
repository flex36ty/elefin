package com.flex.elefin

import android.content.SharedPreferences
import com.flex.elefin.jellyfin.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class ServerAccountsTest {
    private fun memoryPreferences(): SharedPreferences {
        val values = mutableMapOf<String, Any?>()
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "putString" -> { values[args!![0] as String] = args[1]; editor }
                "remove" -> { values.remove(args!![0] as String); editor }
                "clear" -> { values.clear(); editor }
                "commit" -> true
                "apply" -> null
                else -> error(method.name)
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "edit" -> editor
                "getAll" -> values.toMap()
                "getString" -> values[args!![0]] ?: args[1]
                else -> error(method.name)
            }
        } as SharedPreferences
    }
    private fun session(url: String, user: String, token: String) =
        JellyfinConfig(memoryPreferences()).apply {
            serverUrl = url; userId = user; accessToken = token
            username = user; serverName = url; deviceId = "device-" + user
        }

    @Test fun migratesExistingLoginAndSwitchesWithoutLosingEitherToken() {
        val active = session("https://one", "user", "one-token")
        val store = ServerAccounts(memoryPreferences(), active)
        val first = store.list().single().id
        val second = session("https://two", "user", "two-token")
        store.save(second)
        assertEquals("one-token", active.accessToken)
        val secondId = store.list().first { it.serverUrl == "https://two" }.id
        assertTrue(store.activate(secondId))
        assertEquals("https://two", active.serverUrl)
        assertEquals("two-token", active.accessToken)
        assertTrue(store.activate(first))
        assertEquals("one-token", active.accessToken)
        assertEquals(2, store.list().size)
    }

    @Test fun removingInactiveAccountDoesNotLogOutActiveAccount() {
        val active = session("https://one", "user", "one-token")
        val store = ServerAccounts(memoryPreferences(), active)
        store.save()
        store.save(session("https://two", "user", "two-token"))
        store.remove(store.list().first { it.serverUrl == "https://two" }.id)
        assertTrue(active.isConfigured())
        assertEquals("one-token", active.accessToken)
        assertEquals(1, store.list().size)
    }

    @Test fun logoutDoesNotRestoreRemovedSessionButKeepsOtherServers() {
        val active = session("https://one", "user", "one-token")
        val store = ServerAccounts(memoryPreferences(), active)
        store.save()
        store.save(session("https://two", "other", "two-token"))
        store.forgetActive()
        assertFalse(active.isConfigured())
        assertEquals(listOf("https://two"), store.list().map { it.serverUrl })
        assertTrue(store.activate(store.list().single().id))
        assertEquals("two-token", active.accessToken)
    }

    @Test fun accountsAreDistinctByServerAndUser() {
        assertEquals(serverAccountId("https://one/", "a"), serverAccountId("https://one", "a"))
        assertNotEquals(serverAccountId("https://one", "a"), serverAccountId("https://one", "b"))
        assertNotEquals(serverAccountId("https://one", "a"), serverAccountId("https://two", "a"))
    }
}
