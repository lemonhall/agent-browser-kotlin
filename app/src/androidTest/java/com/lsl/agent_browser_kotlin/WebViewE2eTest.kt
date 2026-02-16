package com.lsl.agent_browser_kotlin

import android.content.ContentUris
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.lsl.agentbrowser.ActionKind
import com.lsl.agentbrowser.AgentBrowser
import com.lsl.agentbrowser.FillPayload
import com.lsl.agentbrowser.RenderOptions
import com.lsl.agentbrowser.SnapshotJsOptions
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WebViewE2eTest {
    @Test
    fun snapshot_fill_click_resnapshot_sees_dom_change() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val downloadRelativePath = "Download/agent-browser-kotlin/e2e/frames/"

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

            clearOldFrames(instrumentation, downloadRelativePath)
            stepDelay()

            evalJs(webView, AgentBrowser.getScript())
            captureStep(instrumentation, device, downloadRelativePath, 1)
            stepDelay()

            val snapshot1Raw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val snapshot1 = AgentBrowser.parseSnapshot(snapshot1Raw)
            assertTrue(snapshot1.ok)

            val render1 = AgentBrowser.renderSnapshot(
                snapshot1Raw,
                RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true),
            )
            Log.i("WebViewE2E", render1.text)
            scenario.onActivity { it.setSnapshotText("[STEP 1] initial\n\n${render1.text}") }
            captureStep(instrumentation, device, downloadRelativePath, 2)
            stepDelay()

            val inputRef = snapshot1.refs.values.firstOrNull { it.tag == "input" }?.ref
            val addButtonRef = snapshot1.refs.values.firstOrNull { it.tag == "button" && it.name == "Add Item" }?.ref
            assertNotNull("input ref missing", inputRef)
            assertNotNull("Add Item button ref missing", addButtonRef)

            val fillRaw = evalJs(webView, AgentBrowser.actionJs(inputRef!!, ActionKind.FILL, FillPayload("hello")))
            val fillResult = AgentBrowser.parseAction(fillRaw)
            assertTrue(fillResult.ok)

            val snapshotAfterFillRaw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val renderAfterFill = AgentBrowser.renderSnapshot(
                snapshotAfterFillRaw,
                RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true),
            )
            scenario.onActivity { it.setSnapshotText("[STEP 2] filled input value=hello\n\n${renderAfterFill.text}") }
            captureStep(instrumentation, device, downloadRelativePath, 3)
            stepDelay()

            val clickRaw = evalJs(webView, AgentBrowser.actionJs(addButtonRef!!, ActionKind.CLICK))
            val clickResult = AgentBrowser.parseAction(clickRaw)
            assertTrue(clickResult.ok)
            captureStep(instrumentation, device, downloadRelativePath, 4)
            stepDelay()

            val snapshot2Raw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val snapshot2 = AgentBrowser.parseSnapshot(snapshot2Raw)
            assertTrue(snapshot2.ok)

            val render2 = AgentBrowser.renderSnapshot(
                snapshot2Raw,
                RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true),
            )
            Log.i("WebViewE2E", render2.text)
            scenario.onActivity { it.setSnapshotText("[STEP 3] clicked Add Item + resnapshot\n\n${render2.text}") }
            captureStep(instrumentation, device, downloadRelativePath, 5)
            stepDelay()

            assertTrue("snapshot should contain the new list item", render2.text.contains("hello"))
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

    private fun clearOldFrames(instrumentation: android.app.Instrumentation, relativePath: String) {
        if (Build.VERSION.SDK_INT < 29) return
        val resolver = instrumentation.targetContext.contentResolver
        val projection = arrayOf(MediaStore.Downloads._ID)
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
            arrayOf(relativePath, "step-%.png"),
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                resolver.delete(uri, null, null)
            }
        }
    }

    private fun captureStep(
        instrumentation: android.app.Instrumentation,
        device: UiDevice,
        relativePath: String,
        step: Int,
    ) {
        val stepStr = if (step < 10) "0$step" else step.toString()
        val displayName = "step-$stepStr.png"

        val tmp = File(instrumentation.targetContext.cacheDir, "tmp-$displayName")
        if (tmp.exists()) tmp.delete()
        val ok = device.takeScreenshot(tmp)
        assertTrue("takeScreenshot failed: ${tmp.absolutePath}", ok)
        assertTrue("tmp screenshot missing: ${tmp.absolutePath}", tmp.exists() && tmp.length() > 0)

        val uri = saveToDownloads(instrumentation, relativePath, displayName, tmp)
        assertPngReadable(instrumentation, uri)
        tmp.delete()
    }

    private fun saveToDownloads(
        instrumentation: android.app.Instrumentation,
        relativePath: String,
        displayName: String,
        sourceFile: File,
    ): android.net.Uri {
        val resolver = instrumentation.targetContext.contentResolver

        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, "image/png")
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed for $displayName")
            resolver.openOutputStream(uri, "w")!!.use { out ->
                FileInputStream(sourceFile).use { input -> input.copyTo(out) }
            }
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }.also {
                resolver.update(uri, it, null, null)
            }
            return uri
        }

        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloads, relativePath.removePrefix("Download/"))
        dir.mkdirs()
        val target = File(dir, displayName)
        sourceFile.copyTo(target, overwrite = true)
        return android.net.Uri.fromFile(target)
    }

    private fun assertPngReadable(instrumentation: android.app.Instrumentation, uri: android.net.Uri) {
        val resolver = instrumentation.targetContext.contentResolver
        val header = ByteArray(8)
        resolver.openInputStream(uri)!!.use { input ->
            val read = input.read(header)
            assertEquals(8, read)
        }
        assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), header)
    }
}

