package com.lsl.agent_browser_kotlin.agent

import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import com.lsl.agent_browser_kotlin.WebViewHarnessActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

internal fun loadUrlAndWait(
    scenario: ActivityScenario<WebViewHarnessActivity>,
    url: String,
) {
    val pageLoaded = CountDownLatch(1)
    scenario.onActivity { activity ->
        activity.webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                pageLoaded.countDown()
            }
        }
        activity.webView.loadUrl(url)
    }
    assertTrue("page load timed out: $url", pageLoaded.await(10, TimeUnit.SECONDS))
}

internal suspend fun evalJs(
    webView: WebView,
    script: String,
): String {
    return suspendCancellableCoroutine { cont ->
        webView.post {
            webView.evaluateJavascript(script) { value ->
                if (cont.isActive) cont.resume(value ?: "null")
            }
        }
    }
}

internal fun stepDelay(ms: Long = 3500L) {
    Thread.sleep(ms)
}

