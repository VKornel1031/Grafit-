package hu.grafit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.InputDevice
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class KretaLoginActivityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun intent() = Intent(instrumentation.targetContext, KretaLoginActivity::class.java)

    @Test fun nativeBrowserRendersAndAcceptsTouchWhileLoading() {
        ActivityScenario.launch<KretaLoginActivity>(intent()).use { scenario ->
            val loaded = CountDownLatch(1)
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val browser = activity.findViewById<WebView>(R.id.kreta_login_webview)
                assertNotNull("The Android test image must include a WebView provider", browser)
                assertTrue(browser.width > 0)
                assertTrue(browser.height > 200)
                browser.stopLoading()
                browser.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        if (url == "https://idp.e-kreta.hu/grafit-local-fixture") {
                            view.evaluateJavascript("document.getElementById('submit') !== null") {
                                if (it == "true") loaded.countDown()
                            }
                        }
                    }
                }
                // The production progress bar remains visible throughout this fixture load.
                activity.findViewById<View>(R.id.kreta_login_progress).visibility = View.VISIBLE
                browser.loadDataWithBaseURL("https://idp.e-kreta.hu/grafit-local-fixture", """
                    <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                    <title>Login fixture</title></head><body style="font:20px sans-serif;padding:20px">
                    <h2>KRÉTA login test</h2><label>Username <input id="username"></label><br><br>
                    <button id="submit" style="width:220px;height:60px" onclick="document.title='submitted:'+document.getElementById('username').value">Continue</button>
                    </body></html>
                """.trimIndent(), "text/html", "UTF-8", "https://idp.e-kreta.hu/grafit-local-fixture")
            }
            assertTrue("Local HTML did not finish loading", loaded.await(60, TimeUnit.SECONDS))
            val rect = JSONObject(js(scenario, """
                document.getElementById('username').value='tanulo';
                (function(){var r=document.getElementById('submit').getBoundingClientRect();
                return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2,width:window.innerWidth});})()
            """.trimIndent()))
            var x = 0f
            var y = 0f
            scenario.onActivity { activity ->
                val browser = activity.findViewById<WebView>(R.id.kreta_login_webview)
                val location = IntArray(2)
                browser.getLocationOnScreen(location)
                val scale = browser.width / rect.getDouble("width")
                x = (location[0] + rect.getDouble("x") * scale).toFloat()
                y = (location[1] + rect.getDouble("y") * scale).toFloat()
                assertTrue(browser.isShown)
                activity.findViewById<View>(R.id.kreta_login_progress).visibility = View.VISIBLE
            }
            val time = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(time, time + 100, MotionEvent.ACTION_UP, x, y, 0)
            down.source = InputDevice.SOURCE_TOUCHSCREEN
            up.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                scenario.onActivity { activity ->
                    val origin = IntArray(2)
                    activity.window.decorView.getLocationOnScreen(origin)
                    down.offsetLocation(-origin[0].toFloat(), -origin[1].toFloat())
                    up.offsetLocation(-origin[0].toFloat(), -origin[1].toFloat())
                    assertTrue(activity.dispatchTouchEvent(down))
                    assertTrue(activity.dispatchTouchEvent(up))
                }
            } finally {
                down.recycle()
                up.recycle()
            }
            instrumentation.waitForIdleSync()
            val deadline = SystemClock.uptimeMillis() + 15_000
            var title = js(scenario, "document.title")
            while (title != "submitted:tanulo" && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(100)
                title = js(scenario, "document.title")
            }
            val screenshot = instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/grafit-login-fixture.png")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(screenshot).use { it.readBytes() }
            assertEquals("submitted:tanulo", title)
        }
    }

    @Test fun nativeBrowserReturnsCodeAndRetainsPkceAcrossRecreation() {
        ActivityScenario.launchActivityForResult<KretaLoginActivity>(intent()).use { scenario ->
            val original = Bundle()
            scenario.onActivity { instrumentation.callActivityOnSaveInstanceState(it, original) }
            scenario.recreate()
            scenario.onActivity { activity ->
                val restored = Bundle()
                instrumentation.callActivityOnSaveInstanceState(activity, restored)
                assertEquals(original.getString(KretaLoginActivity.VERIFIER), restored.getString(KretaLoginActivity.VERIFIER))
                assertEquals(original.getString("kreta.oauth_state"), restored.getString("kreta.oauth_state"))
                val callback = Uri.parse("https://mobil.e-kreta.hu/ellenorzo-student/prod/oauthredirect").buildUpon()
                    .appendQueryParameter("code", "fixture-code").appendQueryParameter("state", restored.getString("kreta.oauth_state")).build()
                val browser = activity.findViewById<WebView>(R.id.kreta_login_webview)
                assertNotNull("The Android test image must include a WebView provider", browser)
                val request = object : WebResourceRequest {
                    override fun getUrl() = callback
                    override fun isForMainFrame() = true
                    override fun isRedirect() = true
                    override fun hasGesture() = false
                    override fun getMethod() = "GET"
                    override fun getRequestHeaders() = emptyMap<String, String>()
                }
                assertTrue(browser.webViewClient.shouldOverrideUrlLoading(browser, request))
            }
            assertEquals(android.app.Activity.RESULT_OK, scenario.result.resultCode)
            assertEquals("fixture-code", scenario.result.resultData.getStringExtra(KretaLoginActivity.CODE))
            assertEquals(original.getString(KretaLoginActivity.VERIFIER), scenario.result.resultData.getStringExtra(KretaLoginActivity.VERIFIER))
        }
    }

    private fun js(scenario: ActivityScenario<KretaLoginActivity>, script: String): String {
        val value = AtomicReference<String>()
        val ready = CountDownLatch(1)
        scenario.onActivity { activity ->
            activity.findViewById<WebView>(R.id.kreta_login_webview).evaluateJavascript(script) {
                value.set(it)
                ready.countDown()
            }
        }
        assertTrue("Browser JavaScript did not respond", ready.await(30, TimeUnit.SECONDS))
        return JSONTokener(value.get()).nextValue().toString()
    }
}
