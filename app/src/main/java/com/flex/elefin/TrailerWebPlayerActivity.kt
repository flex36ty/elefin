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

/** YouTube's supported embedded player, hosted entirely inside Elefin. */
class TrailerWebPlayerActivity : ComponentActivity() {
    private var player: WebView? = null

    companion object {
        fun createIntent(context: Context, videoId: String): Intent =
            Intent(context, TrailerWebPlayerActivity::class.java).putExtra("video_id", videoId)
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
        setContentView(webView)
        webView.loadDataWithBaseURL("$origin/", """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta name="referrer" content="strict-origin-when-cross-origin">
            <style>html,body{margin:0;width:100%;height:100%;background:#000;color:white;font:20px sans-serif}
            #player{width:100%;height:100%}#status{position:absolute;inset:45% 8%;text-align:center;pointer-events:none}</style>
            </head><body><div id="player"></div><div id="status">Loading trailer…</div>
            <script>
            var player;
            function onYouTubeIframeAPIReady(){
              player=new YT.Player('player',{
                videoId:${JSONObject.quote(videoId)},
                playerVars:{autoplay:1,playsinline:1,controls:1,fs:0,origin:${JSONObject.quote(origin)}},
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
        webView.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val script = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "togglePlayback();"
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> "seekBy(-10);"
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "seekBy(10);"
            KeyEvent.KEYCODE_MEDIA_PLAY -> "if(player&&player.playVideo)player.playVideo();"
            KeyEvent.KEYCODE_MEDIA_PAUSE -> "if(player&&player.pauseVideo)player.pauseVideo();"
            else -> null
        }
        if (script != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) player?.evaluateJavascript(script, null)
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
