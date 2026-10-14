package com.flex.elefin.discovery

import android.app.job.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.tvprovider.media.tv.*
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.flex.elefin.MainActivity
import com.flex.elefin.jellyfin.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

/** Launcher data contains only public metadata and local artwork, never server credentials. */
object TvHome {
    private const val PERIODIC = 73140
    private const val REFRESH = 73141
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun supported(c: Context) = Build.VERSION.SDK_INT >= 26 && c.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    fun account(c: Context): String = JellyfinConfig(c).let { hash(it.serverUrl.trimEnd('/') + "|" + it.userId) }
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun prefs(c: Context) = c.getSharedPreferences("tv_home", Context.MODE_PRIVATE)
    fun schedule(c: Context) {
        if (!supported(c)) return
        val app = c.applicationContext
        val scheduler = app.getSystemService(JobScheduler::class.java)
        if (!AppSettings(app).tvHomeRecommendations || !JellyfinConfig(app).isConfigured()) {
            scheduler.cancel(PERIODIC); scheduler.cancel(REFRESH)
            scope.launch { runCatching { sync(app) } }
            return
        }
        val component = ComponentName(app, TvHomeJob::class.java)
        if (scheduler.getPendingJob(PERIODIC) == null) scheduler.schedule(JobInfo.Builder(PERIODIC, component)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(30 * 60 * 1000L).setPersisted(true).build())
        scheduler.schedule(JobInfo.Builder(REFRESH, component).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setMinimumLatency(1500).build())
    }
    fun accountChanged(c: Context) {
        if (!supported(c)) return
        // Purge asynchronously even when offline; provider also gates reads by current account.
        scope.launch { runCatching { lock.withLock { clearPrograms(c); prefs(c).edit().remove("account").apply() } }; schedule(c) }
    }
    suspend fun sync(c: Context) = lock.withLock {
        if (!supported(c)) return@withLock
        val config = JellyfinConfig(c)
        val owner = account(c)
        if (prefs(c).getString("account", null) != owner) clearPrograms(c)
        if (!AppSettings(c).tvHomeRecommendations || !config.isConfigured()) {
            clearPrograms(c); return@withLock
        }
        val api = JellyfinApiService(config.serverUrl, config.accessToken, config.userId, config)
        try {
        val resume = api.getContinueWatching(20, throwOnError = true)
        val recent = (api.getRecentlyAddedMovies(15, throwOnError = true) + api.getRecentlyAddedEpisodes(15, throwOnError = true))
            .distinctBy { it.Id }.sortedByDescending { it.DateCreated.orEmpty() }.take(20)
        if (owner != account(c) || !AppSettings(c).tvHomeRecommendations) return@withLock
        prefs(c).edit().putString("account", owner).apply()
        publish(c, api, owner, "continue", "Continue Watching", resume)
        publish(c, api, owner, "recent", "Recently Added", recent)
        File(c.filesDir, "tv-art").listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 7 * 86400000L }?.forEach { it.delete() }
        android.util.Log.i("TvHome", "Updated launcher rows: continue=${resume.size}, recent=${recent.size}")
        } finally { api.close() }
    }
    private fun link(c: Context, owner: String, item: String): Uri = Uri.parse(Intent(c, TvHomeActivity::class.java)
        .setData(Uri.Builder().scheme("elefin-tv").authority("item").appendPath(owner).appendPath(item).build())
        .toUri(Intent.URI_INTENT_SCHEME))
    private fun channel(c: Context, key: String, name: String): Long {
        val resolver = c.contentResolver
        val channels = resolver.query(TvContractCompat.Channels.CONTENT_URI, arrayOf("_id", "internal_provider_id"), null, null, null)
        channels?.use { while(it.moveToNext()) if(it.getString(1) == "elefin:$key") return it.getLong(0) }
        val values = Channel.Builder().setType(TvContractCompat.Channels.TYPE_PREVIEW).setDisplayName("Elefin · $name")
            .setInternalProviderId("elefin:$key").setAppLinkIntentUri(Uri.parse(Intent(c, MainActivity::class.java).toUri(Intent.URI_INTENT_SCHEME))).build().toContentValues()
        val id = ContentUris.parseId(requireNotNull(resolver.insert(TvContractCompat.Channels.CONTENT_URI, values)))
        ChannelLogoUtils.storeChannelLogo(c, id, c.packageManager.getApplicationIcon(c.packageName).toBitmap(160, 160))
        if (key == "continue") TvContractCompat.requestChannelBrowsable(c, id)
        return id
    }
    suspend fun addRecent(c: Context): Long = withContext(Dispatchers.IO) { lock.withLock {
        channel(c, "continue", "Continue Watching")
        channel(c, "recent", "Recently Added")
    } }
    private suspend fun publish(c: Context, api: JellyfinApiService, owner: String, key: String, title: String, items: List<JellyfinItem>) {
        val id = channel(c, key, title)
        val existing = mutableMapOf<String, Long>()
        c.contentResolver.query(TvContractCompat.buildPreviewProgramsUriForChannel(id), arrayOf("_id", "internal_provider_id"), null, null, null)?.use {
            while (it.moveToNext()) existing[it.getString(1)] = it.getLong(0)
        }
        for ((rank, item) in items.withIndex()) {
            if (owner != account(c) || !AppSettings(c).tvHomeRecommendations) return
            val artId = if (item.ImageTags?.containsKey("Primary") == true) item.Id else item.SeriesId ?: item.Id
            val artName = hash(owner + artId + item.ImageTags?.get("Primary")) + ".jpg"
            val dir = File(c.filesDir, "tv-art").apply { mkdirs() }
            val file = File(dir, artName)
            if (!file.exists()) {
                val result = c.imageLoader.execute(ImageRequest.Builder(c).data(api.getImageUrl(artId, maxWidth = 480))
                    .headers(api.getImageRequestHeaders()).size(480, 720).allowHardware(false).build())
                if (result is SuccessResult) {
                    val temp = File.createTempFile("art", ".tmp", dir)
                    temp.outputStream().use { result.drawable.toBitmap().compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
                    temp.renameTo(file); temp.delete()
                }
            }
            file.setLastModified(System.currentTimeMillis())
            val program = PreviewProgram.Builder().setChannelId(id).setInternalProviderId("$owner:${item.Id}")
                .setType(if(item.Type == "Episode") TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE else TvContractCompat.PreviewPrograms.TYPE_MOVIE)
                .setTitle(if(item.Type == "Episode") "${item.SeriesName ?: item.Name} · ${item.Name}" else item.Name)
                .setDescription(item.Overview.orEmpty()).setIntentUri(link(c, owner, item.Id)).setWeight(items.size - rank)
                .setDurationMillis(((item.RunTimeTicks ?: 0) / 10000).coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
                .setLastPlaybackPositionMillis(((item.UserData?.PositionTicks ?: 0) / 10000).coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
                .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
            if(file.exists()) program.setPosterArtUri(Uri.parse("content://${c.packageName}.tvart/$owner/$artName"))
            if (owner != account(c) || !AppSettings(c).tvHomeRecommendations) return
            val old = existing.remove("$owner:${item.Id}")
            if(old == null) c.contentResolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, program.build().toContentValues())
            else c.contentResolver.update(TvContractCompat.buildPreviewProgramUri(old), program.build().toContentValues(), null, null)
        }
        existing.values.forEach { c.contentResolver.delete(TvContractCompat.buildPreviewProgramUri(it), null, null) }
    }
    private fun clearPrograms(c: Context) {
        c.contentResolver.query(TvContractCompat.Channels.CONTENT_URI, arrayOf("_id", "internal_provider_id"), null, null, null)?.use { rows ->
            while(rows.moveToNext()) if(rows.getString(1)?.startsWith("elefin:") == true) {
                c.contentResolver.query(TvContractCompat.buildPreviewProgramsUriForChannel(rows.getLong(0)), arrayOf("_id"), null, null, null)?.use { programs ->
                    while(programs.moveToNext()) c.contentResolver.delete(TvContractCompat.buildPreviewProgramUri(programs.getLong(0)), null, null)
                }
            }
        }
        File(c.filesDir, "tv-art").listFiles()?.forEach { it.delete() }
    }
}

class TvHomeJob : JobService() {
    private val jobs = mutableMapOf<Int, Job>()
    override fun onStartJob(params: JobParameters): Boolean {
        jobs[params.jobId] = CoroutineScope(Dispatchers.IO).launch {
            val retry = try { withTimeout(120000) { TvHome.sync(applicationContext) }; false }
                catch(e: TimeoutCancellationException) { true }
                catch(e: CancellationException) { throw e }
                catch(e: Exception) { android.util.Log.w("TvHome", "Launcher sync failed: ${e.javaClass.simpleName}"); true }
            withContext(Dispatchers.Main) { jobs.remove(params.jobId); jobFinished(params, retry) }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { jobs.remove(params.jobId)?.cancel(); return true }
}
