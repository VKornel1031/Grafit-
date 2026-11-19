package hu.grafit

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.view.doOnLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import hu.grafit.data.OAuth

/** The authentication document has its own native window and never sits behind a loading overlay. */
class KretaLoginActivity : ComponentActivity() {
    private lateinit var verifier: String
    private lateinit var state: String
    private lateinit var pageHost: TextView
    private lateinit var notice: TextView
    private lateinit var progress: ProgressBar
    private lateinit var browserContainer: FrameLayout
    private var browser: WebView? = null
    private var completed = false
    private val handler = Handler(Looper.getMainLooper())
    private val loadTimeout = Runnable {
        showNotice("A betöltés lassú. Az oldalt továbbra is használhatod, vagy válaszd az Újratöltés gombot.")
        progress.visibility = View.INVISIBLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        verifier = savedInstanceState?.getString(VERIFIER) ?: OAuth.random()
        state = savedInstanceState?.getString(STATE) ?: OAuth.random()
        buildLayout()
        createBrowser(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) { goBack() }
    }

    private fun buildLayout() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        val back = Button(this).apply {
            text = "Vissza"
            setOnClickListener { goBack() }
        }
        val title = TextView(this).apply {
            text = "KRÉTA bejelentkezés"
            textSize = 18f
            setTextColor(Color.rgb(28, 33, 29))
            setPadding(dp(8), 0, dp(4), 0)
        }
        val retry = Button(this).apply {
            id = R.id.kreta_login_retry
            text = "Újratöltés"
            setOnClickListener { restartLogin() }
        }
        toolbar.addView(back, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        toolbar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        toolbar.addView(retry, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        pageHost = TextView(this).apply {
            text = "idp.e-kreta.hu"
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(dp(16), dp(4), dp(16), dp(8))
        }
        content.addView(pageHost)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            id = R.id.kreta_login_progress
            max = 100
        }
        content.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
        notice = TextView(this).apply {
            id = R.id.kreta_login_notice
            visibility = View.GONE
            textSize = 14f
            setTextColor(Color.rgb(93, 47, 28))
            setBackgroundColor(Color.rgb(255, 239, 222))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        content.addView(notice)
        browserContainer = FrameLayout(this)
        content.addView(browserContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(content)
        WindowCompat.getInsetsController(window, content).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createBrowser(savedState: Bundle? = null) {
        val view = try { WebView(this) } catch (_: RuntimeException) {
            showNotice("Az Android WebView nem indítható. Frissítsd vagy engedélyezd az Android System WebView alkalmazást.")
            progress.visibility = View.GONE
            return
        }
        browser = view
        view.id = R.id.kreta_login_webview
        view.setBackgroundColor(Color.WHITE)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        view.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(webView: WebView, value: Int) {
                progress.progress = value
                if (value == 100) finishLoading()
            }
        }
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(webView: WebView, request: WebResourceRequest): Boolean =
                request.isForMainFrame && handleNavigation(request.url.toString())

            override fun onPageStarted(webView: WebView, url: String, favicon: Bitmap?) {
                pageHost.text = Uri.parse(url).host ?: "KRÉTA"
                startLoading()
            }

            override fun onPageCommitVisible(webView: WebView, url: String) { finishLoading() }
            override fun onPageFinished(webView: WebView, url: String) {
                finishLoading()
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(webView: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    finishLoading()
                    showNotice("A bejelentkezési oldal betöltése sikertelen (${error.errorCode}). Az Újratöltés gombbal újrapróbálhatod.")
                }
            }

            override fun onReceivedHttpError(webView: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) {
                    finishLoading()
                    showNotice("A KRÉTA szolgáltatása hibát jelzett (${response.statusCode}). Próbáld újra később.")
                }
            }

            override fun onReceivedSslError(webView: WebView, sslHandler: SslErrorHandler, error: SslError) {
                sslHandler.cancel()
                finishLoading()
                showNotice("A bejelentkezési oldal biztonsági tanúsítványa nem ellenőrizhető.")
            }

            override fun onRenderProcessGone(webView: WebView, detail: RenderProcessGoneDetail): Boolean {
                finishLoading()
                browserContainer.removeView(webView)
                webView.destroy()
                browser = null
                showNotice("Az Android WebView leállt. Az Újratöltés gomb újraindítja a böngészőt.")
                return true
            }
        }
        browserContainer.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val restored = savedState?.let(view::restoreState)
        startLoading()
        view.doOnLayout {
            if (restored == null) view.loadUrl(OAuth.loginUrl(verifier, state))
        }
    }

    private fun handleNavigation(url: String): Boolean {
        val code = try { OAuth.callbackCode(url, state) } catch (e: IllegalArgumentException) {
            finishLoading()
            showNotice(e.message ?: "Érvénytelen bejelentkezési válasz.")
            return true
        }
        if (code != null) {
            if (!completed) {
                completed = true
                setResult(RESULT_OK, Intent().putExtra(CODE, code).putExtra(VERIFIER, verifier))
                finish()
            }
            return true
        }
        if (!OAuth.isBrowserNavigationAllowed(url)) {
            finishLoading()
            showNotice("Ez a bejelentkezési hivatkozás nem támogatott.")
            return true
        }
        return false
    }

    private fun restartLogin() {
        if (completed) return
        notice.visibility = View.GONE
        val view = browser
        if (view == null) createBrowser() else {
            view.stopLoading()
            startLoading()
            view.loadUrl(OAuth.loginUrl(verifier, state))
        }
    }

    private fun startLoading() {
        progress.progress = 0
        progress.visibility = View.VISIBLE
        handler.removeCallbacks(loadTimeout)
        handler.postDelayed(loadTimeout, 30_000)
    }

    private fun finishLoading() {
        handler.removeCallbacks(loadTimeout)
        progress.visibility = View.INVISIBLE
    }

    private fun showNotice(message: String) { notice.text = message; notice.visibility = View.VISIBLE }
    private fun goBack() { if (browser?.canGoBack() == true) browser?.goBack() else finish() }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(VERIFIER, verifier)
        outState.putString(STATE, state)
        browser?.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        browser?.let { browserContainer.removeView(it); it.stopLoading(); it.destroy() }
        browser = null
        super.onDestroy()
    }

    companion object {
        const val CODE = "kreta.authorization_code"
        const val VERIFIER = "kreta.code_verifier"
        private const val STATE = "kreta.oauth_state"
    }
}
