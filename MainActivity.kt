package com.fifo.pick

import android.annotation.SuppressLint
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var fileCb: ValueCallback<Array<Uri>>? = null

    // Scanner broadcast actions. Set your device scanner (DataWedge etc.) to "Intent output"
    // with action  com.fifo.pick.SCAN  (category DEFAULT, delivery: Broadcast intent).
    private val actions = listOf(
        "com.fifo.pick.SCAN",
        "android.intent.ACTION_DECODE_DATA",
        "nlscan.action.SCANNER_RESULT",
        "scan.rcv.message"
    )
    private val keys = listOf(
        "com.symbol.datawedge.data_string", "barcode_string", "SCAN_BARCODE1",
        "barocode", "barcode", "data", "scannerdata"
    )

    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            var code: String? = null
            for (k in keys) {
                val v = i.getStringExtra(k)
                if (!v.isNullOrBlank()) { code = v; break }
            }
            if (code != null) {
                val js = "window.onScan&&window.onScan(" + JSONObject.quote(code.trim()) + ")"
                web.post { web.evaluateJavascript(js, null) }
            }
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun saveCsv(name: String, text: String) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    val cv = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val u = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
                    contentResolver.openOutputStream(u)!!.use { it.write(text.toByteArray()) }
                } else {
                    val d = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
                    File(d, name).writeText(text)
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Save failed: " + e.message, Toast.LENGTH_LONG).show() }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "Android")

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(r.url)
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(w: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                fileCb?.onReceiveValue(null); fileCb = cb
                return try { startActivityForResult(p.createIntent(), 7); true }
                catch (e: Exception) { fileCb = null; false }
            }
        }
        val f = IntentFilter(); actions.forEach { f.addAction(it) }
        ContextCompat.registerReceiver(this, rx, f, ContextCompat.RECEIVER_EXPORTED)
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(r: Int, res: Int, d: Intent?) {
        if (r == 7) {
            fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, d)); fileCb = null
        } else super.onActivityResult(r, res, d)
    }

    override fun onDestroy() { unregisterReceiver(rx); super.onDestroy() }
}
