package com.lsl.agent_browser_kotlin

import android.util.Log
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.lsl.agentbrowser.ActionKind
import com.lsl.agentbrowser.AgentBrowser
import com.lsl.agentbrowser.FillPayload
import com.lsl.agentbrowser.SnapshotJsOptions
import com.lsl.agentbrowser.RenderOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WebViewE2eTest {
    @Test
    fun snapshot_fill_click_resnapshot_sees_dom_change() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val artifactFramesDir = prepareAppExternalFramesDir()

        ActivityScenario.launch(WebViewHarnessActivity::class.java).use { scenario ->
            lateinit var webView: WebView
            scenario.onActivity { activity -> webView = activity.webView }

            val pageLoaded = CountDownLatch(1)
            scenario.onActivity { activity ->
                activity.webView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        pageLoaded.countDown()
                    }
                }
                activity.webView.loadUrl("file:///android_asset/e2e/complex.html")
            }
            assertTrue(pageLoaded.await(10, TimeUnit.SECONDS))

            cleanFramesDir(artifactFramesDir)
            stepDelay()

            evalJs(webView, AgentBrowser.getScript())
            captureStep(device, artifactFramesDir, 1)
            stepDelay()

            val snapshot1Raw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val snapshot1 = AgentBrowser.parseSnapshot(snapshot1Raw)
            assertTrue(snapshot1.ok)

            val render1 = AgentBrowser.renderSnapshot(snapshot1Raw, RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true))
            Log.i("WebViewE2E", render1.text)
            scenario.onActivity { it.setSnapshotText("[STEP 1] initial\n\n${render1.text}") }
            captureStep(device, artifactFramesDir, 2)
            stepDelay()

            val inputRef = snapshot1.refs.values.firstOrNull { it.tag == "input" }?.ref
            val addButtonRef = snapshot1.refs.values.firstOrNull { it.tag == "button" && it.name == "Add Item" }?.ref
            assertNotNull("input ref missing", inputRef)
            assertNotNull("Add Item button ref missing", addButtonRef)

            val fillRaw = evalJs(webView, AgentBrowser.actionJs(inputRef!!, ActionKind.FILL, FillPayload("hello")))
            val fillResult = AgentBrowser.parseAction(fillRaw)
            assertTrue(fillResult.ok)
            val snapshotAfterFillRaw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val renderAfterFill = AgentBrowser.renderSnapshot(snapshotAfterFillRaw, RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true))
            scenario.onActivity { it.setSnapshotText("[STEP 2] filled input value=hello\n\n${renderAfterFill.text}") }
            captureStep(device, artifactFramesDir, 3)
            stepDelay()

            val clickRaw = evalJs(webView, AgentBrowser.actionJs(addButtonRef!!, ActionKind.CLICK))
            val clickResult = AgentBrowser.parseAction(clickRaw)
            assertTrue(clickResult.ok)
            captureStep(device, artifactFramesDir, 4)
            stepDelay()

            val snapshot2Raw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val snapshot2 = AgentBrowser.parseSnapshot(snapshot2Raw)
            assertTrue(snapshot2.ok)

            val render2 = AgentBrowser.renderSnapshot(snapshot2Raw, RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true))
            Log.i("WebViewE2E", render2.text)
            scenario.onActivity { it.setSnapshotText("[STEP 3] clicked Add Item + resnapshot\n\n${render2.text}") }
            captureStep(device, artifactFramesDir, 5)
            stepDelay()

            assertTrue("snapshot should contain the new list item", render2.text.contains("hello"))

            assertTrue("frames dir missing: ${artifactFramesDir.absolutePath}", artifactFramesDir.exists())
            assertTrue("missing step-05 frame", File(artifactFramesDir, "step-05.png").exists())
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

    private fun stepDelay(ms: Long = 3500L) {
        Thread.sleep(ms)
    }

    private fun prepareAppExternalFramesDir(): File {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val base = ctx.getExternalFilesDir("e2e") ?: error("externalFilesDir is null")
        val framesDir = File(base, "frames")
        if (!framesDir.exists()) framesDir.mkdirs()
        return framesDir
    }

    private fun cleanFramesDir(framesDir: File) {
        framesDir.mkdirs()
        framesDir.listFiles()?.forEach { it.delete() }
    }

    private fun captureStep(device: UiDevice, framesDir: File, step: Int) {
        val stepStr = if (step < 10) "0$step" else step.toString()
        val outFile = File(framesDir, "step-$stepStr.png")
        val ok = device.takeScreenshot(outFile)
        assertTrue("takeScreenshot failed: ${outFile.absolutePath}", ok)
    }
}
