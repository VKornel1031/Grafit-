package hu.grafit.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import hu.grafit.data.OAuth
import kotlinx.coroutines.delay

/** Firka's embedded-browser login, hosted outside transformed page-transition layers. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun LoginScreen(onCode: (String, String) -> Unit, onClose: () -> Unit) {
    val verifier = rememberSaveable { OAuth.random() }
    val state = rememberSaveable { OAuth.random() }
    var browser by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var handled by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("idp.e-kreta.hu") }
    val onResult by rememberUpdatedState(onCode)
    val url = remember(verifier, state) { OAuth.loginUrl(verifier, state) }
    fun fail(message: String) { error = message; loading = false }
    fun navigate(target: String): Boolean {
        try {
            val code = OAuth.callbackCode(target, state)
            if (code != null) {
                if (!handled) { handled = true; loading = false; onResult(code, verifier) }
                return true
            }
        } catch (e: IllegalArgumentException) { fail(e.message ?: "Érvénytelen bejelentkezés."); return true }
        if (!OAuth.isBrowserNavigationAllowed(target)) {
            fail("A bejelentkezési oldal nem támogatott hivatkozást nyitott meg.")
            return true
        }
        return false
    }
    BackHandler(canGoBack && !handled) { browser?.goBack() }
    LaunchedEffect(attempt) {
        delay(30_000)
        if (loading && !handled) fail("A KRÉTA oldalának betöltése túl sokáig tart. Próbáld újra.")
    }
    Column(Modifier.fillMaxSize()) {
        Text("Bejelentkezés · $host", Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.labelMedium)
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.White)) {
            AndroidView(modifier = Modifier.fillMaxSize().testTag("kreta-login-browser"),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        setBackgroundColor(android.graphics.Color.WHITE)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, value: Int) { progress = value }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                request.isForMainFrame && navigate(request.url.toString())
                            override fun onPageStarted(view: WebView, target: String, favicon: Bitmap?) {
                                host = android.net.Uri.parse(target).host ?: host
                                loading = true
                                error = null
                            }
                            override fun onPageCommitVisible(view: WebView, target: String) { loading = false }
                            override fun onPageFinished(view: WebView, target: String) {
                                loading = false
                                canGoBack = view.canGoBack()
                                CookieManager.getInstance().flush()
                            }
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                                if (request.isForMainFrame) fail("A KRÉTA oldalát nem sikerült betölteni. Ellenőrizd az internetkapcsolatot.")
                            }
                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                if (request.isForMainFrame) fail("A KRÉTA bejelentkezési szolgáltatása hibát jelzett (${response.statusCode}).")
                            }
                            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, failure: SslError) {
                                handler.cancel()
                                fail("A KRÉTA biztonsági tanúsítványa nem ellenőrizhető.")
                            }
                            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                fail("Az Android WebView leállt. Nyisd meg újra a bejelentkezést.")
                                browser = null
                                (view.parent as? ViewGroup)?.removeView(view)
                                view.destroy()
                                return true
                            }
                        }
                        browser = this
                        // Attach and size the native view before its first navigation.
                        post { loadUrl(url) }
                    }
                }, onRelease = { view ->
                    view.stopLoading()
                    view.webChromeClient = null
                    view.destroy()
                    browser = null
                })
            androidx.compose.animation.AnimatedVisibility(visible = loading && error == null && !handled,
                enter = fadeIn(tween(150)), exit = fadeOut(tween(300))) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                        Text("KRÉTA oldal betöltése…", Modifier.padding(top = 20.dp))
                        LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.width(180.dp).padding(top = 16.dp))
                    }
                }
            }
            if (error != null || handled) Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text(error ?: "Munkamenet létrehozása…")
                    if (!handled && browser != null) Button(onClick = {
                        error = null; loading = true; progress = 0; attempt++
                        browser?.loadUrl(url)
                    }, modifier = Modifier.padding(top = 16.dp)) { Text("Újrapróbálás") }
                    TextButton(onClick = onClose) { Text("Vissza") }
                }
            }
        }
    }
}
