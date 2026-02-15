package com.lsl.agent_browser_kotlin

import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lsl.agentbrowser.ActionKind
import com.lsl.agentbrowser.AgentBrowser
import com.lsl.agentbrowser.FillPayload
import com.lsl.agentbrowser.SnapshotJsOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WebViewE2eTest {
    @Test
    fun snapshot_fill_click_resnapshot_sees_dom_change() {
        ActivityScenario.launch(WebViewHarnessActivity::class.java).use { scenario ->
            lateinit var webView: WebView
            scenario.onActivity { activity -> webView = activity.webView }

            val html = """
                <!doctype html>
                <html>
                  <head><meta charset="utf-8"><title>Harness</title></head>
                  <body>
                    <input id="q" placeholder="Search" />
                    <button id="go" onclick="this.textContent=document.getElementById('q').value">Go</button>
                  </body>
                </html>
            """.trimIndent()

            val pageLoaded = CountDownLatch(1)
            scenario.onActivity { activity ->
                activity.webView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        pageLoaded.countDown()
                    }
                }
                activity.webView.loadDataWithBaseURL("https://example.invalid/", html, "text/html", "utf-8", null)
            }
            assertTrue(pageLoaded.await(10, TimeUnit.SECONDS))

            evalJs(webView, AgentBrowser.getScript())

            val snapshot1Raw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = true)))
            val snapshot1 = AgentBrowser.parseSnapshot(snapshot1Raw)
            assertTrue(snapshot1.ok)

            val inputRef = snapshot1.refs.values.firstOrNull { it.tag == "input" }?.ref
            val buttonRef = snapshot1.refs.values.firstOrNull { it.tag == "button" }?.ref
            assertNotNull("input ref missing", inputRef)
            assertNotNull("button ref missing", buttonRef)

            val fillRaw = evalJs(webView, AgentBrowser.actionJs(inputRef!!, ActionKind.FILL, FillPayload("hello")))
            val fillResult = AgentBrowser.parseAction(fillRaw)
            assertTrue(fillResult.ok)

            val clickRaw = evalJs(webView, AgentBrowser.actionJs(buttonRef!!, ActionKind.CLICK))
            val clickResult = AgentBrowser.parseAction(clickRaw)
            assertTrue(clickResult.ok)

            val snapshot2Raw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = true)))
            val snapshot2 = AgentBrowser.parseSnapshot(snapshot2Raw)
            assertTrue(snapshot2.ok)

            val updatedButton = snapshot2.refs.values.firstOrNull { it.tag == "button" }
            assertNotNull(updatedButton)
            assertEquals("hello", updatedButton!!.name)
        }
    }

    private fun evalJs(webView: WebView, script: String): String {
        val latch = CountDownLatch(1)
        var result: String? = null
        webView.post {
            webView.evaluateJavascript(script) { value ->
                result = value
                latch.countDown()
            }
        }
        assertTrue("evaluateJavascript timed out", latch.await(10, TimeUnit.SECONDS))
        return result ?: "null"
    }
}

