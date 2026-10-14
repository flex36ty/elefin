package com.flex.elefin.jellyfin

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal fun libraryAddedMillis(value: String?): Long? = runCatching {
    if (value == null) return null
    val normalized = value.replace(Regex("\\.(\\d+)")) { "." + it.groupValues[1].take(3).padEnd(3, '0') }
    val pattern = if (normalized.contains('.')) "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" else "yyyy-MM-dd'T'HH:mm:ssXXX"
    SimpleDateFormat(pattern, Locale.ROOT).apply { isLenient = false; timeZone = TimeZone.getTimeZone("UTC") }.parse(normalized)?.time
}.getOrNull()

internal fun libraryHasNewContent(added: Long?, previousOpen: Long, libraryOpened: Long): Boolean =
    added != null && added > maxOf(previousOpen, libraryOpened)

/** Baselines are fixed for a foreground session, not advanced by redraws or polling. */
internal object LibraryNewContent {
    private val baselines = mutableMapOf<String, Long>()
    fun newForegroundSession() { baselines.clear() }
    fun account(context: Context): String? = JellyfinConfig(context).let {
        if (it.isConfigured()) serverAccountId(it.serverUrl, it.userId) else null
    }
    private fun prefs(context: Context) = context.getSharedPreferences("library_new_content", Context.MODE_PRIVATE)
    fun baseline(context: Context, account: String): Long = baselines.getOrPut(account) {
        val now = System.currentTimeMillis()
        val previous = prefs(context).getLong("$account:last_open", now)
        prefs(context).edit().putLong("$account:last_open", now).apply()
        previous
    }
    fun opened(context: Context, account: String, library: String) {
        prefs(context).edit().putLong("$account:library:$library", System.currentTimeMillis()).apply()
    }
    fun hasNew(context: Context, account: String, library: String, added: Long?): Boolean =
        libraryHasNewContent(added, baseline(context, account), prefs(context).getLong("$account:library:$library", 0))
}
