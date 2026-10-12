package com.flex.elefin

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.tv.material3.*
import com.flex.elefin.screens.PlayerControlButton
import com.flex.elefin.screens.PlayerSeekBar
import com.flex.elefin.player.cinema.cinemaTime
import kotlinx.coroutines.delay

/** YouTube's supported embedded player, hosted entirely inside Elefin. */
class TrailerWebPlayerActivity : ComponentActivity() {
    private var player: WebView? = null
    private var controls by mutableStateOf(true)
    private var paused by mutableStateOf(false)
    private var position by mutableStateOf(0.0)
    private var duration by mutableStateOf(0.0)
    private var interaction by mutableStateOf(0L)
    private fun execute(script: String) { interaction++; player?.evaluateJavascript(script, null) }

    companion object {
        fun createIntent(context: Context, videoId: String, title: String = "Trailer"): Intent =
            Intent(context, TrailerWebPlayerActivity::class.java).putExtra("video_id", videoId).putExtra("title", title)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoId = intent.getStringExtra("video_id")
        if (videoId == null || !videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView)
            .hide(WindowInsetsCompat.Type.systemBars())
        val origin = "https://$packageName"
        val webView = WebView(this)
        player = webView
        webView.setBackgroundColor(android.graphics.Color.BLACK)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Player subframes may load, but links must never launch another app or replace Elefin.
                return request.isForMainFrame || request.url.scheme !in listOf("https", "http")
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (message.message().startsWith("ElefinTrailer:")) {
                    Log.d("TrailerWebPlayer", message.message())
                }
                return true
            }
        }
        setContent { JellyfinAppTheme { TrailerControls(webView) } }
        webView.loadDataWithBaseURL("$origin/", """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta name="referrer" content="strict-origin-when-cross-origin">
            <style>html,body{margin:0;width:100%;height:100%;background:#000;color:white;font:20px sans-serif}
            #player{width:100%;height:100%}#status{position:absolute;inset:45% 8%;text-align:center;pointer-events:none}</style>
            </head><body><div id="player"></div><div id="status">Loading trailerâ€¦</div>
            <script>
            var player;
            function onYouTubeIframeAPIReady(){
              player=new YT.Player('player',{
                videoId:${JSONObject.quote(videoId)},
                playerVars:{autoplay:1,playsinline:1,controls:0,fs:0,origin:${JSONObject.quote(origin)}},
                events:{
                  onReady:function(e){document.getElementById('status').textContent='';e.target.playVideo();console.log('ElefinTrailer: ready');},
                  onStateChange:function(e){console.log('ElefinTrailer: state '+e.data);},
                  onError:function(e){document.getElementById('status').textContent='This trailer cannot be played here. Press Back to return.';console.log('ElefinTrailer: error '+e.data);}
                }
              });
            }
            function togglePlayback(){if(player&&player.getPlayerState){player.getPlayerState()===1?player.pauseVideo():player.playVideo();}}
            function seekBy(seconds){if(player&&player.getCurrentTime){player.seekTo(Math.max(0,player.getCurrentTime()+seconds),true);}}
            </script><script src="https://www.youtube.com/iframe_api"></script></body></html>
        """.trimIndent(), "text/html", "UTF-8", null)

    }

    @Composable
    private fun TrailerControls(webView: WebView) {
        val playFocus = remember { FocusRequester() }
        BackHandler { if (controls) controls = false else finish() }
        LaunchedEffect(webView) {
            while (true) {
                webView.evaluateJavascript("(function(){return player&&player.getDuration?{position:player.getCurrentTime(),duration:player.getDuration(),state:player.getPlayerState()}:null})()") { value ->
                    runCatching {
                        val state = JSONObject(value)
                        position = state.optDouble("position", 0.0)
                        duration = state.optDouble("duration", 0.0)
                        paused = state.optInt("state") != 1
                    }
                }
                delay(500)
            }
        }
        LaunchedEffect(controls, paused, interaction) {
            if (controls && !paused) { delay(5_000); controls = false }
        }
        LaunchedEffect(controls) {
            if (controls) { withFrameNanos { }; runCatching { playFocus.requestFocus() } }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (controls) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .4f))) {
                    Surface(modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = SurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = .5f))) {
                        Text(intent.getStringExtra("title") ?: "Trailer", color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), maxLines = 2)
                    }
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 32.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cinemaTime(position), color = Color.White)
                            Text(cinemaTime(duration), color = Color.White)
                        }
                        Spacer(Modifier.height(8.dp))
                        PlayerSeekBar(currentPosition = (position * 1000).toLong(),
                            duration = (duration * 1000).toLong(),
                            onSeek = { execute("if(player&&player.seekTo)player.seekTo(${it / 1000.0},true);") })
                        Spacer(Modifier.height(24.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically) {
                            PlayerControlButton(Icons.Default.FastRewind, "Rewind 15s", { execute("seekBy(-15);") })
                            Spacer(Modifier.width(32.dp))
                            PlayerControlButton(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                if (paused) "Play" else "Pause", { execute("togglePlayback();") }, Modifier.focusRequester(playFocus))
                            Spacer(Modifier.width(32.dp))
                            PlayerControlButton(Icons.Default.FastForward, "Forward 15s", { execute("seekBy(15);") })
                        }
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) interaction++
        if (!controls && event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
            if (event.action == KeyEvent.ACTION_DOWN) controls = true
            return true
        }
        val script = when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "togglePlayback();"
            KeyEvent.KEYCODE_MEDIA_REWIND -> "seekBy(-15);"
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "seekBy(15);"
            KeyEvent.KEYCODE_MEDIA_PLAY -> "if(player&&player.playVideo)player.playVideo();"
            KeyEvent.KEYCODE_MEDIA_PAUSE -> "if(player&&player.pauseVideo)player.pauseVideo();"
            else -> null
        }
        if (script != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) { execute(script); controls = true }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        player?.evaluateJavascript("if(player&&player.pauseVideo)player.pauseVideo();", null)
        player?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        player?.onResume()
    }

    override fun onDestroy() {
        player?.let { view ->
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            view.stopLoading()
            view.destroy()
        }
        player = null
        super.onDestroy()
    }
}
