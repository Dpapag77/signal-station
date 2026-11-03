package app.signalstation

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    // Serves bundled assets from a real https origin so fetch() + CORS behave like a normal site.
    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    private val pickFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uris = WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data)
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true          // case file + favorites persist in localStorage
            allowFileAccess = false
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)  // target=_blank links route through shouldOverrideUrlLoading
        }
        web.setBackgroundColor(0xFF0A0D12.toInt())
        web.addJavascriptInterface(Bridge(), "AndroidBridge")

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(req.url)

            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                if (req.url.host == APP_HOST) return false
                // Every external link (search engines, explorers, maps) opens in the user's browser/app.
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, req.url)) }
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                pendingShare?.let { injectPivot(it); pendingShare = null }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = cb
                return try { pickFile.launch(params.createIntent()); true } catch (e: Exception) { fileCallback = null; false }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("window.ssBack&&window.ssBack()") { r ->
                    if (r != "true") { if (web.canGoBack()) web.goBack() else finish() }
                }
            }
        })

        pendingShare = sharedText(intent)
        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(START_URL)
    }

    private var pendingShare: String? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedText(intent)?.let { injectPivot(it) }
    }

    private fun sharedText(i: Intent?): String? =
        if (i?.action == Intent.ACTION_SEND) i.getStringExtra(Intent.EXTRA_TEXT) else null

    private fun injectPivot(text: String) {
        val js = JSONObject.quote(text.trim())
        web.evaluateJavascript(
            "(function(){var p=document.getElementById('pv');if(!p)return;if(window.ssBack)window.ssBack();p.value=$js;p.dispatchEvent(new Event('input'));scrollTo(0,0)})()", null
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    /** Exposed to the page as window.AndroidBridge */
    inner class Bridge {
        @JavascriptInterface
        fun saveText(name: String, text: String) = save(name, text.toByteArray(), mime(name))

        @JavascriptInterface
        fun saveDataUrl(name: String, dataUrl: String) {
            val comma = dataUrl.indexOf(',')
            if (comma < 0) return
            save(name, Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT), mime(name))
        }

        @JavascriptInterface
        fun copy(text: String) {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Signal Station", text))
        }
    }

    private fun mime(name: String) = when (name.substringAfterLast('.').lowercase()) {
        "md" -> "text/markdown"; "json" -> "application/json"
        "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; else -> "text/plain"
    }

    private fun save(name: String, bytes: ByteArray, mime: String) {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safe)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SignalStation")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
                contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
            } else {
                val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
                File(dir, safe).writeBytes(bytes)
            }
        }.isSuccess
        runOnUiThread {
            Toast.makeText(this, if (ok) "Saved to Downloads/SignalStation" else "Could not save file", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val APP_HOST = "appassets.androidplatform.net"
        private const val START_URL = "https://$APP_HOST/assets/www/index.html"
    }
}
