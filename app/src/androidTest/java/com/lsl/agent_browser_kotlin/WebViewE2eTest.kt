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
import com.lsl.agentbrowser.PageKind
import com.lsl.agentbrowser.PagePayload
import com.lsl.agentbrowser.QueryKind
import com.lsl.agentbrowser.QueryPayload
import com.lsl.agentbrowser.RenderOptions
import com.lsl.agentbrowser.SelectPayload
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
        val runPrefix = "run-${System.currentTimeMillis()}"

        ActivityScenario.launch(WebViewHarnessActivity::class.java).use { scenario ->
            lateinit var webView: WebView
            scenario.onActivity { activity -> webView = activity.webView }

            loadUrlAndWait(scenario, "file:///android_asset/e2e/complex.html")

            clearOldFrames(instrumentation)
            stepDelay()

            evalJs(webView, AgentBrowser.getScript())
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 1)
            stepDelay()

            val snapshot1Raw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val snapshot1 = AgentBrowser.parseSnapshot(snapshot1Raw)
            assertTrue(snapshot1.ok)
            assertTrue("content nodes should have refs in v4 (expected h1 ref)", snapshot1.refs.values.any { it.tag == "h1" })
            val render1 = AgentBrowser.renderSnapshot(
                snapshot1Raw,
                RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true),
            )
            Log.i("WebViewE2E", render1.text)
            scenario.onActivity { it.setSnapshotText("[STEP 1] initial\n\n${render1.text}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 2)
            stepDelay()

            assertTrue("aria-hidden section should not appear", !render1.text.contains("Should Not Appear"))
            assertTrue("hidden action should not appear before toggling", !render1.text.contains("Hidden Action"))

            val inputRef1 = snapshot1.refs.values.firstOrNull { it.tag == "input" }?.ref
            assertNotNull("input ref missing", inputRef1)
            val fillRaw = evalJs(webView, AgentBrowser.actionJs(inputRef1!!, ActionKind.FILL, FillPayload("hello")))
            val fillResult = AgentBrowser.parseAction(fillRaw)
            assertTrue(fillResult.ok)

            val q1Raw = evalJs(webView, AgentBrowser.queryJs(inputRef1, QueryKind.VALUE, QueryPayload(limitChars = 200)))
            val q1 = AgentBrowser.parseQuery(q1Raw)
            assertTrue(q1.ok)
            assertEquals("hello", q1.value)
            scenario.onActivity { it.setSnapshotText("[STEP 2] query(value) after fill = ${q1.value}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 3)
            stepDelay()

            val clearRaw = evalJs(webView, AgentBrowser.actionJs(inputRef1, ActionKind.CLEAR))
            val clearResult = AgentBrowser.parseAction(clearRaw)
            assertTrue(clearResult.ok)
            val q2Raw = evalJs(webView, AgentBrowser.queryJs(inputRef1, QueryKind.VALUE, QueryPayload(limitChars = 200)))
            val q2 = AgentBrowser.parseQuery(q2Raw)
            assertTrue(q2.ok)
            assertEquals("", q2.value)
            scenario.onActivity { it.setSnapshotText("[STEP 3] query(value) after clear = \"${q2.value}\"") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 4)
            stepDelay()

            val fillAgainRaw = evalJs(webView, AgentBrowser.actionJs(inputRef1, ActionKind.FILL, FillPayload("hello")))
            val fillAgainResult = AgentBrowser.parseAction(fillAgainRaw)
            assertTrue(fillAgainResult.ok)

            val snapshot2Raw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val snapshot2 = AgentBrowser.parseSnapshot(snapshot2Raw)
            assertTrue(snapshot2.ok)
            val render2 = AgentBrowser.renderSnapshot(snapshot2Raw, RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true))
            scenario.onActivity { it.setSnapshotText("[STEP 4] after fill (again)\n\n${render2.text}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 5)
            stepDelay()

            val selectRef2 = snapshot2.refs.values.firstOrNull { it.tag == "select" }?.ref
            assertNotNull("select ref missing", selectRef2)
            val selectRaw = evalJs(webView, AgentBrowser.actionJs(selectRef2!!, ActionKind.SELECT, SelectPayload(values = listOf("beta"))))
            val selectResult = AgentBrowser.parseAction(selectRaw)
            assertTrue(selectResult.ok)

            val snapshot3Raw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val snapshot3 = AgentBrowser.parseSnapshot(snapshot3Raw)
            assertTrue(snapshot3.ok)

            val render3 = AgentBrowser.renderSnapshot(snapshot3Raw, RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true))
            scenario.onActivity { it.setSnapshotText("[STEP 5] after select beta\n\n${render3.text}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 6)
            stepDelay()

            assertTrue("snapshot should reflect mode change", render3.text.contains("mode: beta"))

            val addButtonRef3 = snapshot3.refs.values.firstOrNull { it.tag == "button" && it.name == "Add Item" }?.ref
            assertNotNull("Add Item button ref missing", addButtonRef3)
            val clickAddRaw = evalJs(webView, AgentBrowser.actionJs(addButtonRef3!!, ActionKind.CLICK))
            val clickAddResult = AgentBrowser.parseAction(clickAddRaw)
            assertTrue(clickAddResult.ok)

            val snapshot4Raw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val snapshot4 = AgentBrowser.parseSnapshot(snapshot4Raw)
            assertTrue(snapshot4.ok)
            val render4 = AgentBrowser.renderSnapshot(snapshot4Raw, RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true))
            scenario.onActivity { it.setSnapshotText("[STEP 6] after Add Item\n\n${render4.text}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 7)
            stepDelay()
            assertTrue("snapshot should contain the new list item", render4.text.contains("hello"))

            val toggleButtonRef4 = snapshot4.refs.values.firstOrNull { it.tag == "button" && it.name == "Toggle Hidden" }?.ref
            assertNotNull("Toggle Hidden button ref missing", toggleButtonRef4)
            val stylesRaw = evalJs(webView, AgentBrowser.queryJs(toggleButtonRef4!!, QueryKind.COMPUTED_STYLES, QueryPayload(limitChars = 800)))
            val styles = AgentBrowser.parseQuery(stylesRaw)
            assertTrue(styles.ok)
            assertTrue("computed_styles should include display", (styles.value ?: "").contains("display"))
            val attrsRaw = evalJs(webView, AgentBrowser.queryJs(toggleButtonRef4, QueryKind.ATTRS, QueryPayload(limitChars = 1200)))
            val attrs = AgentBrowser.parseQuery(attrsRaw)
            assertTrue(attrs.ok)
            assertTrue("attrs should include id=btnToggle", (attrs.value ?: "").contains("\"id\":\"btnToggle\""))
            scenario.onActivity { it.setSnapshotText("[STEP 7] query(computed_styles) Toggle Hidden\n\n${styles.value}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 8)
            stepDelay()

            val checkboxRef4 =
                snapshot4.refs.values.firstOrNull { it.tag == "input" && (it.attrs["type"] ?: "").lowercase() == "checkbox" }?.ref
            assertNotNull("checkbox ref missing", checkboxRef4)
            val checkRaw = evalJs(webView, AgentBrowser.actionJs(checkboxRef4!!, ActionKind.CHECK))
            val checkResult = AgentBrowser.parseAction(checkRaw)
            assertTrue(checkResult.ok)

            val snapshot5Raw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val snapshot5 = AgentBrowser.parseSnapshot(snapshot5Raw)
            assertTrue(snapshot5.ok)
            val render5 = AgentBrowser.renderSnapshot(snapshot5Raw, RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true))
            scenario.onActivity { it.setSnapshotText("[STEP 8] after CHECK\n\n${render5.text}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 9)
            stepDelay()
            assertTrue("agree text should be true after CHECK", render5.text.contains("agree: true"))

            val checkboxRef5 =
                snapshot5.refs.values.firstOrNull { it.tag == "input" && (it.attrs["type"] ?: "").lowercase() == "checkbox" }?.ref
            assertNotNull("checkbox ref missing (after CHECK)", checkboxRef5)
            val uncheckRaw = evalJs(webView, AgentBrowser.actionJs(checkboxRef5!!, ActionKind.UNCHECK))
            val uncheckResult = AgentBrowser.parseAction(uncheckRaw)
            assertTrue(uncheckResult.ok)

            val snapshot6Raw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val snapshot6 = AgentBrowser.parseSnapshot(snapshot6Raw)
            assertTrue(snapshot6.ok)
            val render6 = AgentBrowser.renderSnapshot(snapshot6Raw, RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true))
            scenario.onActivity { it.setSnapshotText("[STEP 9] after UNCHECK\n\n${render6.text}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 10)
            stepDelay()
            assertTrue("agree text should be false after UNCHECK", render6.text.contains("agree: false"))

            val toggleButtonRef6 = snapshot6.refs.values.firstOrNull { it.tag == "button" && it.name == "Toggle Hidden" }?.ref
            assertNotNull("Toggle Hidden button ref missing (after UNCHECK)", toggleButtonRef6)
            val clickToggleRaw = evalJs(webView, AgentBrowser.actionJs(toggleButtonRef6!!, ActionKind.CLICK))
            val clickToggleResult = AgentBrowser.parseAction(clickToggleRaw)
            assertTrue(clickToggleResult.ok)

            val snapshot7Raw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val snapshot7 = AgentBrowser.parseSnapshot(snapshot7Raw)
            assertTrue(snapshot7.ok)
            val render7 = AgentBrowser.renderSnapshot(snapshot7Raw, RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true))
            scenario.onActivity { it.setSnapshotText("[STEP 10] after Toggle Hidden\n\n${render7.text}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 11)
            stepDelay()
            assertTrue("hidden action should appear after toggling", render7.text.contains("Hidden Action"))

            val hiddenActionRef7 = snapshot7.refs.values.firstOrNull { it.tag == "button" && it.name == "Hidden Action" }?.ref
            assertNotNull("Hidden Action button ref missing", hiddenActionRef7)
            val clickHiddenRaw = evalJs(webView, AgentBrowser.actionJs(hiddenActionRef7!!, ActionKind.CLICK))
            val clickHiddenResult = AgentBrowser.parseAction(clickHiddenRaw)
            assertTrue(clickHiddenResult.ok)

            val snapshot8Raw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val snapshot8 = AgentBrowser.parseSnapshot(snapshot8Raw)
            assertTrue(snapshot8.ok)
            val render8 = AgentBrowser.renderSnapshot(snapshot8Raw, RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true))
            scenario.onActivity { it.setSnapshotText("[STEP 11] after Hidden Action\n\n${render8.text}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 12)
            stepDelay()
            assertTrue("status should reflect hidden click", render8.text.contains("hidden-clicked"))

            loadUrlAndWait(scenario, "file:///android_asset/e2e/stress.html")
            stepDelay()

            evalJs(webView, AgentBrowser.getScript())
            stepDelay()

            val info0Raw = evalJs(webView, AgentBrowser.pageJs(PageKind.INFO))
            val info0 = AgentBrowser.parsePage(info0Raw)
            assertTrue(info0.ok)
            scenario.onActivity { it.setSnapshotText("[PAGE] info before scroll: scrollY=${info0.scrollY} viewport=${info0.viewport}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 14)
            stepDelay()

            val scrollRaw = evalJs(webView, AgentBrowser.pageJs(PageKind.SCROLL, PagePayload(deltaY = 900)))
            val scrollRes = AgentBrowser.parsePage(scrollRaw)
            assertTrue(scrollRes.ok)
            val info1Raw = evalJs(webView, AgentBrowser.pageJs(PageKind.INFO))
            val info1 = AgentBrowser.parsePage(info1Raw)
            assertTrue(info1.ok)
            assertTrue("scrollY should increase after scroll", (info1.scrollY ?: 0) > (info0.scrollY ?: 0))
            scenario.onActivity { it.setSnapshotText("[PAGE] info after scroll: scrollY=${info1.scrollY}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 15)
            stepDelay()

            val stressRaw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = true)))
            val normalized = AgentBrowser.normalizeJsEvalResult(stressRaw)
            val jsonBytes = normalized.toByteArray(Charsets.UTF_8).size
            val stress = AgentBrowser.parseSnapshot(stressRaw)
            assertTrue(stress.ok)
            assertTrue("stress snapshot jsonBytes should be < 100KB (actual=$jsonBytes)", jsonBytes < 100 * 1024)

            val jsTime = stress.stats?.jsTimeMs
            val perfLine = "[STEP 16] stress snapshot jsonBytes=$jsonBytes jsTimeMs=$jsTime"
            Log.i("WebViewE2E", perfLine)
            scenario.onActivity { it.setSnapshotText(perfLine) }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 16)
            stepDelay()
        }
    }

    private fun loadUrlAndWait(scenario: ActivityScenario<WebViewHarnessActivity>, url: String) {
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

    private fun clearOldFrames(instrumentation: android.app.Instrumentation) {
        if (Build.VERSION.SDK_INT < 29) return
        val resolver = instrumentation.targetContext.contentResolver
        val projection = arrayOf(MediaStore.Downloads._ID)
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
            arrayOf("%agent-browser-kotlin/e2e/frames%", "%step-%.png"),
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
        runPrefix: String,
        step: Int,
    ) {
        val stepStr = if (step < 10) "0$step" else step.toString()
        val displayName = "$runPrefix-step-$stepStr.png"

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
