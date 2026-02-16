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
        val downloadSnapshotsRelativePath = "Download/agent-browser-kotlin/e2e/snapshots/"
        val runPrefix = "run-${System.currentTimeMillis()}"

        ActivityScenario.launch(WebViewHarnessActivity::class.java).use { scenario ->
            lateinit var webView: WebView
            scenario.onActivity { activity -> webView = activity.webView }

            loadUrlAndWait(scenario, "file:///android_asset/e2e/complex.html")

            clearOldFrames(instrumentation)
            clearOldSnapshots(instrumentation)
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
            dumpSnapshotArtifacts(
                instrumentation = instrumentation,
                relativePath = downloadSnapshotsRelativePath,
                runPrefix = runPrefix,
                step = 2,
                snapshotRaw = snapshot1Raw,
                snapshotText = render1.text,
            )
            stepDelay()

            assertTrue("aria-hidden section should not appear", !render1.text.contains("Should Not Appear"))
            assertTrue("hidden action should not appear before toggling", !render1.text.contains("Hidden Action"))

            val cookieAcceptRef = snapshot1.refs.values.firstOrNull { it.tag == "button" && it.name == "Accept cookies" }?.ref
            assertNotNull("cookie accept button ref missing", cookieAcceptRef)
            val cookieAcceptRaw = evalJs(webView, AgentBrowser.actionJs(cookieAcceptRef!!, ActionKind.CLICK))
            val cookieAccept = AgentBrowser.parseAction(cookieAcceptRaw)
            assertTrue(cookieAccept.ok)
            stepDelay()

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
            dumpSnapshotArtifacts(
                instrumentation = instrumentation,
                relativePath = downloadSnapshotsRelativePath,
                runPrefix = runPrefix,
                step = 5,
                snapshotRaw = snapshot2Raw,
                snapshotText = render2.text,
            )
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
            dumpSnapshotArtifacts(
                instrumentation = instrumentation,
                relativePath = downloadSnapshotsRelativePath,
                runPrefix = runPrefix,
                step = 6,
                snapshotRaw = snapshot3Raw,
                snapshotText = render3.text,
            )
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
            dumpSnapshotArtifacts(
                instrumentation = instrumentation,
                relativePath = downloadSnapshotsRelativePath,
                runPrefix = runPrefix,
                step = 7,
                snapshotRaw = snapshot4Raw,
                snapshotText = render4.text,
            )
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
            dumpSnapshotArtifacts(
                instrumentation = instrumentation,
                relativePath = downloadSnapshotsRelativePath,
                runPrefix = runPrefix,
                step = 9,
                snapshotRaw = snapshot5Raw,
                snapshotText = render5.text,
            )
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
            dumpSnapshotArtifacts(
                instrumentation = instrumentation,
                relativePath = downloadSnapshotsRelativePath,
                runPrefix = runPrefix,
                step = 10,
                snapshotRaw = snapshot6Raw,
                snapshotText = render6.text,
            )
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
            dumpSnapshotArtifacts(
                instrumentation = instrumentation,
                relativePath = downloadSnapshotsRelativePath,
                runPrefix = runPrefix,
                step = 11,
                snapshotRaw = snapshot7Raw,
                snapshotText = render7.text,
            )
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
            dumpSnapshotArtifacts(
                instrumentation = instrumentation,
                relativePath = downloadSnapshotsRelativePath,
                runPrefix = runPrefix,
                step = 12,
                snapshotRaw = snapshot8Raw,
                snapshotText = render8.text,
            )
            stepDelay()
            assertTrue("status should reflect hidden click", render8.text.contains("hidden-clicked"))

            // v5: PRD-V4 alignment checks (roles/refs + cursor-interactive gate + name via aria-labelledby + stats naming)
            val v5SnapInteractiveOnlyRaw = evalJs(
                webView,
                AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = true, cursorInteractive = false)),
            )
            val v5SnapInteractiveOnly = AgentBrowser.parseSnapshot(v5SnapInteractiveOnlyRaw)
            assertTrue(v5SnapInteractiveOnly.ok)
            assertTrue(
                "interactiveOnly=true should still include CONTENT refs when name exists (expected h1 ref)",
                v5SnapInteractiveOnly.refs.values.any { it.tag == "h1" },
            )
            assertTrue(
                "aria-labelledby should contribute to name (expected h2 name from #lblH2)",
                v5SnapInteractiveOnly.refs.values.any { it.tag == "h2" && it.name == "Labelled Heading Text" },
            )
            assertTrue(
                "cursorInteractive=false should not include Cursor Interactive Box",
                v5SnapInteractiveOnly.refs.values.none { (it.name ?: "").contains("Cursor Interactive Box") },
            )

            val v5SnapCursorRaw = evalJs(
                webView,
                AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = true, cursorInteractive = true)),
            )
            val v5SnapCursor = AgentBrowser.parseSnapshot(v5SnapCursorRaw)
            assertTrue(v5SnapCursor.ok)
            val cursorBoxRef = v5SnapCursor.refs.values.firstOrNull {
                it.role == "focusable" && (it.name ?: "").contains("Cursor Interactive Box")
            }?.ref
            assertNotNull("cursorInteractive=true should include Cursor Interactive Box ref", cursorBoxRef)
            val clickCursorBoxRaw = evalJs(webView, AgentBrowser.actionJs(cursorBoxRef!!, ActionKind.CLICK))
            val clickCursorBox = AgentBrowser.parseAction(clickCursorBoxRaw)
            assertTrue(clickCursorBox.ok)

            val v5AfterCursorRaw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val v5AfterCursor = AgentBrowser.parseSnapshot(v5AfterCursorRaw)
            assertTrue(v5AfterCursor.ok)
            assertNotNull("stats.visitedNodes should be present (PRD naming)", v5AfterCursor.stats?.visitedNodes)
            assertNotNull("stats.emittedNodes should be present (PRD naming)", v5AfterCursor.stats?.emittedNodes)
            val v5AfterCursorRender = AgentBrowser.renderSnapshot(
                v5AfterCursorRaw,
                RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true),
            )
            assertTrue("after cursorInteractive click, h1 should change", v5AfterCursorRender.text.contains("ci-clicked"))
            scenario.onActivity { it.setSnapshotText("[STEP v5] cursorInteractive + content refs OK\n\n${v5AfterCursorRender.text}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 13)
            dumpSnapshotArtifacts(
                instrumentation = instrumentation,
                relativePath = downloadSnapshotsRelativePath,
                runPrefix = runPrefix,
                step = 13,
                snapshotRaw = v5AfterCursorRaw,
                snapshotText = v5AfterCursorRender.text,
            )
            stepDelay()

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

            val stressRender = AgentBrowser.renderSnapshot(
                stressRaw,
                RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true),
            )

            val jsTime = stress.stats?.jsTimeMs
            val perfLine = "[STEP 16] stress snapshot jsonBytes=$jsonBytes jsTimeMs=$jsTime"
            Log.i("WebViewE2E", perfLine)
            scenario.onActivity { it.setSnapshotText(perfLine) }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 16)
            dumpSnapshotArtifacts(
                instrumentation = instrumentation,
                relativePath = downloadSnapshotsRelativePath,
                runPrefix = runPrefix,
                step = 16,
                snapshotRaw = stressRaw,
                snapshotText = "[stress]\n$perfLine\n\n${stressRender.text}",
            )
            stepDelay()
        }
    }

    @Test
    fun ref_lifecycle_navigation_makes_old_ref_invalid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val downloadRelativePath = "Download/agent-browser-kotlin/e2e/frames/"
        val downloadSnapshotsRelativePath = "Download/agent-browser-kotlin/e2e/snapshots/"
        val runPrefix = "run-${System.currentTimeMillis()}-nav"

        ActivityScenario.launch(WebViewHarnessActivity::class.java).use { scenario ->
            lateinit var webView: WebView
            scenario.onActivity { activity -> webView = activity.webView }

            loadUrlAndWait(scenario, "file:///android_asset/e2e/nav1.html")

            clearOldFrames(instrumentation)
            clearOldSnapshots(instrumentation)
            stepDelay()

            evalJs(webView, AgentBrowser.getScript())
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 1)
            stepDelay()

            val nav1SnapRaw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val nav1Snap = AgentBrowser.parseSnapshot(nav1SnapRaw)
            assertTrue(nav1Snap.ok)
            val oldButtonRef = nav1Snap.refs.values.firstOrNull { it.tag == "button" && it.name == "Primary Action" }?.ref
            assertNotNull("nav1 Primary Action button ref missing", oldButtonRef)
            val linkRef = nav1Snap.refs.values.firstOrNull {
                it.tag == "a" && (it.attrs["href"] ?: "").contains("nav2.html")
            }?.ref
            assertNotNull("nav1 Go Next link ref missing", linkRef)

            val nav1Render = AgentBrowser.renderSnapshot(
                nav1SnapRaw,
                RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true),
            )
            scenario.onActivity { it.setSnapshotText("[NAV1] snapshot\n\n${nav1Render.text}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 2)
            dumpSnapshotArtifacts(
                instrumentation = instrumentation,
                relativePath = downloadSnapshotsRelativePath,
                runPrefix = runPrefix,
                step = 2,
                snapshotRaw = nav1SnapRaw,
                snapshotText = nav1Render.text,
            )
            stepDelay()

            val navLoaded = CountDownLatch(1)
            scenario.onActivity { activity ->
                activity.webView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                        navLoaded.countDown()
                    }
                }
            }
            val clickNavRaw = evalJs(webView, AgentBrowser.actionJs(linkRef!!, ActionKind.CLICK))
            val clickNav = AgentBrowser.parseAction(clickNavRaw)
            assertTrue(clickNav.ok)
            assertTrue("navigation timed out: nav1 -> nav2", navLoaded.await(10, TimeUnit.SECONDS))
            stepDelay()

            scenario.onActivity {
                assertTrue("expected nav2.html url after navigation, actual=${it.webView.url}", (it.webView.url ?: "").contains("nav2.html"))
            }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 3)
            stepDelay()

            evalJs(webView, AgentBrowser.getScript())
            stepDelay()

            val staleClickRaw = evalJs(webView, AgentBrowser.actionJs(oldButtonRef!!, ActionKind.CLICK))
            val staleClick = AgentBrowser.parseAction(staleClickRaw)
            assertTrue(!staleClick.ok)
            assertEquals("ref_not_found", staleClick.error?.code)
            scenario.onActivity { it.setSnapshotText("[NAV2] stale ref action -> ${staleClick.error?.code}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 4)
            stepDelay()

            val nav2SnapRaw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val nav2Snap = AgentBrowser.parseSnapshot(nav2SnapRaw)
            assertTrue(nav2Snap.ok)
            val nav2Render = AgentBrowser.renderSnapshot(
                nav2SnapRaw,
                RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true),
            )
            assertTrue("nav2 snapshot should include NAV2 banner text", nav2Render.text.contains("NAV2: You are on page 2"))
            scenario.onActivity { it.setSnapshotText("[NAV2] snapshot\n\n${nav2Render.text}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 5)
            dumpSnapshotArtifacts(
                instrumentation = instrumentation,
                relativePath = downloadSnapshotsRelativePath,
                runPrefix = runPrefix,
                step = 5,
                snapshotRaw = nav2SnapRaw,
                snapshotText = nav2Render.text,
            )
            stepDelay()
        }
    }

    @Test
    fun click_blocked_by_cookie_banner_is_ai_friendly_then_dismiss_allows_click() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val downloadRelativePath = "Download/agent-browser-kotlin/e2e/frames/"
        val downloadSnapshotsRelativePath = "Download/agent-browser-kotlin/e2e/snapshots/"
        val runPrefix = "run-${System.currentTimeMillis()}-cookie"

        ActivityScenario.launch(WebViewHarnessActivity::class.java).use { scenario ->
            lateinit var webView: WebView
            scenario.onActivity { activity -> webView = activity.webView }

            loadUrlAndWait(scenario, "file:///android_asset/e2e/complex.html")

            clearOldFrames(instrumentation)
            clearOldSnapshots(instrumentation)
            stepDelay()

            evalJs(webView, AgentBrowser.getScript())
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 1)
            stepDelay()

            val snapRaw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val snap = AgentBrowser.parseSnapshot(snapRaw)
            assertTrue(snap.ok)

            val applyRef = snap.refs.values.firstOrNull { it.tag == "button" && it.name == "Apply" }?.ref
            assertNotNull("Apply button ref missing", applyRef)
            val acceptRef = snap.refs.values.firstOrNull { it.tag == "button" && it.name == "Accept cookies" }?.ref
            assertNotNull("cookie accept button ref missing", acceptRef)

            val blockedRaw = evalJs(webView, AgentBrowser.actionJs(applyRef!!, ActionKind.CLICK))
            val blocked = AgentBrowser.parseAction(blockedRaw)
            assertTrue(!blocked.ok)
            val msg = blocked.error?.message ?: ""
            assertTrue("expected 'blocked by another element' in message, actual=$msg", msg.contains("blocked by another element"))
            assertTrue("expected 'modal or overlay' in message, actual=$msg", msg.contains("modal or overlay"))
            assertTrue("expected 'cookie banners' in message, actual=$msg", msg.contains("cookie banners"))

            scenario.onActivity { it.setSnapshotText("[COOKIE] blocked click message:\n$msg") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 2)
            dumpSnapshotArtifacts(
                instrumentation = instrumentation,
                relativePath = downloadSnapshotsRelativePath,
                runPrefix = runPrefix,
                step = 2,
                snapshotRaw = snapRaw,
                snapshotText = "[COOKIE]\nblocked=$msg\n\n${AgentBrowser.renderSnapshot(snapRaw, RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true)).text}",
            )
            stepDelay()

            val acceptRaw = evalJs(webView, AgentBrowser.actionJs(acceptRef!!, ActionKind.CLICK))
            val accept = AgentBrowser.parseAction(acceptRaw)
            assertTrue(accept.ok)
            stepDelay()

            val okRaw = evalJs(webView, AgentBrowser.actionJs(applyRef, ActionKind.CLICK))
            val ok = AgentBrowser.parseAction(okRaw)
            assertTrue(ok.ok)
            stepDelay()

            val afterRaw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val after = AgentBrowser.renderSnapshot(afterRaw, RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true))
            assertTrue("expected status applied after click", after.text.contains("applied"))
            scenario.onActivity { it.setSnapshotText("[COOKIE] after dismiss + apply\n\n${after.text}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 3)
            dumpSnapshotArtifacts(
                instrumentation = instrumentation,
                relativePath = downloadSnapshotsRelativePath,
                runPrefix = runPrefix,
                step = 3,
                snapshotRaw = afterRaw,
                snapshotText = after.text,
            )
            stepDelay()
        }
    }

    @Test
    fun scroll_into_view_moves_page_and_allows_click() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val downloadRelativePath = "Download/agent-browser-kotlin/e2e/frames/"
        val downloadSnapshotsRelativePath = "Download/agent-browser-kotlin/e2e/snapshots/"
        val runPrefix = "run-${System.currentTimeMillis()}-scrollIntoView"

        ActivityScenario.launch(WebViewHarnessActivity::class.java).use { scenario ->
            lateinit var webView: WebView
            scenario.onActivity { activity -> webView = activity.webView }

            loadUrlAndWait(scenario, "file:///android_asset/e2e/long.html")

            clearOldFrames(instrumentation)
            clearOldSnapshots(instrumentation)
            stepDelay()

            evalJs(webView, AgentBrowser.getScript())
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 1)
            stepDelay()

            val info0Raw = evalJs(webView, AgentBrowser.pageJs(PageKind.INFO))
            val info0 = AgentBrowser.parsePage(info0Raw)
            assertTrue(info0.ok)

            val snapRaw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = true)))
            val snap = AgentBrowser.parseSnapshot(snapRaw)
            assertTrue(snap.ok)
            val bottomRef = snap.refs.values.firstOrNull { it.tag == "button" && it.name == "Bottom Action" }?.ref
            assertNotNull("Bottom Action ref missing", bottomRef)

            val sivRaw = evalJs(webView, AgentBrowser.actionJs(bottomRef!!, ActionKind.SCROLL_INTO_VIEW))
            val siv = AgentBrowser.parseAction(sivRaw)
            assertTrue(siv.ok)
            stepDelay()

            val info1Raw = evalJs(webView, AgentBrowser.pageJs(PageKind.INFO))
            val info1 = AgentBrowser.parsePage(info1Raw)
            assertTrue(info1.ok)
            assertTrue("expected scrollY to increase after scroll_into_view", (info1.scrollY ?: 0) > (info0.scrollY ?: 0))

            val clickRaw = evalJs(webView, AgentBrowser.actionJs(bottomRef, ActionKind.CLICK))
            val click = AgentBrowser.parseAction(clickRaw)
            assertTrue(click.ok)
            stepDelay()

            val afterRaw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val after = AgentBrowser.renderSnapshot(afterRaw, RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true))
            assertTrue("expected bottom-clicked in pill", after.text.contains("bottom-clicked"))
            scenario.onActivity { it.setSnapshotText("[SCROLL_INTO_VIEW] scrollY ${info0.scrollY} -> ${info1.scrollY}\n\n${after.text}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 2)
            dumpSnapshotArtifacts(
                instrumentation = instrumentation,
                relativePath = downloadSnapshotsRelativePath,
                runPrefix = runPrefix,
                step = 2,
                snapshotRaw = afterRaw,
                snapshotText = after.text,
            )
            stepDelay()
        }
    }

    @Test
    fun repeated_structure_refs_are_unique_and_click_hits_correct_card() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val downloadRelativePath = "Download/agent-browser-kotlin/e2e/frames/"
        val downloadSnapshotsRelativePath = "Download/agent-browser-kotlin/e2e/snapshots/"
        val runPrefix = "run-${System.currentTimeMillis()}-repeat"

        ActivityScenario.launch(WebViewHarnessActivity::class.java).use { scenario ->
            lateinit var webView: WebView
            scenario.onActivity { activity -> webView = activity.webView }

            loadUrlAndWait(scenario, "file:///android_asset/e2e/repeat.html")

            clearOldFrames(instrumentation)
            clearOldSnapshots(instrumentation)
            stepDelay()

            evalJs(webView, AgentBrowser.getScript())
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 1)
            stepDelay()

            val snapRaw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val snap = AgentBrowser.parseSnapshot(snapRaw)
            assertTrue(snap.ok)

            val openRefs = snap.refs.values.filter { it.tag == "button" && it.name == "Open" }.mapNotNull { it.ref }
            assertTrue("expected multiple Open buttons, got=${openRefs.size}", openRefs.size >= 6)
            assertEquals("Open refs should all be unique", openRefs.toSet().size, openRefs.size)

            val targetRef = openRefs[2]
            val clickRaw = evalJs(webView, AgentBrowser.actionJs(targetRef, ActionKind.CLICK))
            val click = AgentBrowser.parseAction(clickRaw)
            assertTrue(click.ok)
            stepDelay()

            val afterRaw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val after = AgentBrowser.renderSnapshot(afterRaw, RenderOptions(maxCharsTotal = 8000, maxNodes = 320, maxDepth = 14, compact = true))
            assertTrue("expected card 3 opened marker", after.text.contains("opened:3"))
            assertTrue("expected other cards not opened:1", !after.text.contains("opened:1"))
            assertTrue("expected other cards not opened:2", !after.text.contains("opened:2"))

            scenario.onActivity { it.setSnapshotText("[REPEAT] clicked 3rd Open ref=$targetRef\n\n${after.text}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 2)
            dumpSnapshotArtifacts(
                instrumentation = instrumentation,
                relativePath = downloadSnapshotsRelativePath,
                runPrefix = runPrefix,
                step = 2,
                snapshotRaw = afterRaw,
                snapshotText = after.text,
            )
            stepDelay()
        }
    }

    @Test
    fun keyboard_and_state_queries_keyDown_keyUp_char_isvisible_isenabled_ischecked() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val downloadRelativePath = "Download/agent-browser-kotlin/e2e/frames/"
        val downloadSnapshotsRelativePath = "Download/agent-browser-kotlin/e2e/snapshots/"
        val runPrefix = "run-${System.currentTimeMillis()}-kbd"

        ActivityScenario.launch(WebViewHarnessActivity::class.java).use { scenario ->
            lateinit var webView: WebView
            scenario.onActivity { activity -> webView = activity.webView }

            loadUrlAndWait(scenario, "file:///android_asset/e2e/keyboard_state.html")

            clearOldFrames(instrumentation)
            clearOldSnapshots(instrumentation)
            stepDelay()

            evalJs(webView, AgentBrowser.getScript())
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 1)
            stepDelay()

            val snapRaw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val snap = AgentBrowser.parseSnapshot(snapRaw)
            assertTrue(snap.ok)

            val inputRef = snap.refs.values.firstOrNull { it.tag == "input" && it.name == "Typing Field" }?.ref
            assertNotNull("Typing Field input ref missing", inputRef)
            val logRef = snap.refs.values.firstOrNull { it.tag == "textarea" && it.name == "Event Log" }?.ref
            assertNotNull("Event Log textarea ref missing", logRef)
            val checkboxRef = snap.refs.values.firstOrNull { it.tag == "input" && it.role == "checkbox" }?.ref
            assertNotNull("checkbox ref missing", checkboxRef)
            val toggleTargetRef = snap.refs.values.firstOrNull { it.tag == "button" && it.name == "Toggleable Action" }?.ref
            assertNotNull("Toggleable Action ref missing", toggleTargetRef)
            val toggleDisabledRef = snap.refs.values.firstOrNull { it.tag == "button" && (it.name ?: "").startsWith("Toggle Disabled") }?.ref
            assertNotNull("Toggle Disabled button ref missing", toggleDisabledRef)
            val toggleVisibleRef = snap.refs.values.firstOrNull { it.tag == "button" && (it.name ?: "").startsWith("Toggle Visible") }?.ref
            assertNotNull("Toggle Visible button ref missing", toggleVisibleRef)

            val render0 = AgentBrowser.renderSnapshot(
                snapRaw,
                RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true),
            )
            scenario.onActivity { it.setSnapshotText("[KBD] initial snapshot\n\n${render0.text}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 2)
            dumpSnapshotArtifacts(
                instrumentation = instrumentation,
                relativePath = downloadSnapshotsRelativePath,
                runPrefix = runPrefix,
                step = 2,
                snapshotRaw = snapRaw,
                snapshotText = render0.text,
            )
            stepDelay()

            val enabled0Raw = evalJs(webView, AgentBrowser.queryJs(inputRef!!, QueryKind.IS_ENABLED, QueryPayload(limitChars = 20)))
            val enabled0 = AgentBrowser.parseQuery(enabled0Raw)
            assertTrue(enabled0.ok)
            assertEquals("true", enabled0.value)

            val vis0Raw = evalJs(webView, AgentBrowser.queryJs(toggleTargetRef!!, QueryKind.IS_VISIBLE, QueryPayload(limitChars = 20)))
            val vis0 = AgentBrowser.parseQuery(vis0Raw)
            assertTrue(vis0.ok)
            assertEquals("true", vis0.value)

            val checked0Raw = evalJs(webView, AgentBrowser.queryJs(checkboxRef!!, QueryKind.IS_CHECKED, QueryPayload(limitChars = 20)))
            val checked0 = AgentBrowser.parseQuery(checked0Raw)
            assertTrue(checked0.ok)
            assertEquals("false", checked0.value)

            val focusRaw = evalJs(webView, AgentBrowser.actionJs(inputRef, ActionKind.FOCUS))
            val focus = AgentBrowser.parseAction(focusRaw)
            assertTrue(focus.ok)
            stepDelay()

            val clickFieldRaw = evalJs(webView, AgentBrowser.actionJs(inputRef, ActionKind.CLICK))
            val clickField = AgentBrowser.parseAction(clickFieldRaw)
            assertTrue(clickField.ok)
            stepDelay()

            val kdRaw = evalJs(webView, AgentBrowser.pageJs(PageKind.KEY_DOWN, PagePayload(key = "A")))
            val kd = AgentBrowser.parsePage(kdRaw)
            assertTrue(kd.ok)
            stepDelay()

            val chRaw = evalJs(webView, AgentBrowser.pageJs(PageKind.CHAR, PagePayload(text = "a")))
            val ch = AgentBrowser.parsePage(chRaw)
            assertTrue(ch.ok)
            stepDelay()

            val kuRaw = evalJs(webView, AgentBrowser.pageJs(PageKind.KEY_UP, PagePayload(key = "A")))
            val ku = AgentBrowser.parsePage(kuRaw)
            assertTrue(ku.ok)
            stepDelay()

            val fieldValueRaw = evalJs(webView, AgentBrowser.queryJs(inputRef, QueryKind.VALUE, QueryPayload(limitChars = 200)))
            val fieldValue = AgentBrowser.parseQuery(fieldValueRaw)
            assertTrue(fieldValue.ok)
            assertEquals("a", fieldValue.value)

            val logValueRaw = evalJs(webView, AgentBrowser.queryJs(logRef!!, QueryKind.VALUE, QueryPayload(limitChars = 2000)))
            val logValue = AgentBrowser.parseQuery(logValueRaw)
            assertTrue(logValue.ok)
            val logText = logValue.value ?: ""
            assertTrue("expected down:A in log, got=$logText", logText.contains("down:A"))
            assertTrue("expected up:A in log, got=$logText", logText.contains("up:A"))
            assertTrue("expected input:a in log, got=$logText", logText.contains("input:a"))

            scenario.onActivity { it.setSnapshotText("[KBD] fieldValue=${fieldValue.value}\n\nlog:\n$logText") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 3)
            stepDelay()

            val disableRaw = evalJs(webView, AgentBrowser.actionJs(toggleDisabledRef!!, ActionKind.CLICK))
            val disable = AgentBrowser.parseAction(disableRaw)
            assertTrue(disable.ok)
            stepDelay()

            val enabled1Raw = evalJs(webView, AgentBrowser.queryJs(inputRef, QueryKind.IS_ENABLED, QueryPayload(limitChars = 20)))
            val enabled1 = AgentBrowser.parseQuery(enabled1Raw)
            assertTrue(enabled1.ok)
            assertEquals("false", enabled1.value)

            val checkRaw = evalJs(webView, AgentBrowser.actionJs(checkboxRef, ActionKind.CHECK))
            val check = AgentBrowser.parseAction(checkRaw)
            assertTrue(check.ok)
            val checked1Raw = evalJs(webView, AgentBrowser.queryJs(checkboxRef, QueryKind.IS_CHECKED, QueryPayload(limitChars = 20)))
            val checked1 = AgentBrowser.parseQuery(checked1Raw)
            assertTrue(checked1.ok)
            assertEquals("true", checked1.value)

            val uncheckRaw = evalJs(webView, AgentBrowser.actionJs(checkboxRef, ActionKind.UNCHECK))
            val uncheck = AgentBrowser.parseAction(uncheckRaw)
            assertTrue(uncheck.ok)
            val checked2Raw = evalJs(webView, AgentBrowser.queryJs(checkboxRef, QueryKind.IS_CHECKED, QueryPayload(limitChars = 20)))
            val checked2 = AgentBrowser.parseQuery(checked2Raw)
            assertTrue(checked2.ok)
            assertEquals("false", checked2.value)

            val hideRaw = evalJs(webView, AgentBrowser.actionJs(toggleVisibleRef!!, ActionKind.CLICK))
            val hide = AgentBrowser.parseAction(hideRaw)
            assertTrue(hide.ok)
            stepDelay()

            val vis1Raw = evalJs(webView, AgentBrowser.queryJs(toggleTargetRef, QueryKind.IS_VISIBLE, QueryPayload(limitChars = 20)))
            val vis1 = AgentBrowser.parseQuery(vis1Raw)
            assertTrue(vis1.ok)
            assertEquals("false", vis1.value)

            val showRaw = evalJs(webView, AgentBrowser.actionJs(toggleVisibleRef, ActionKind.CLICK))
            val show = AgentBrowser.parseAction(showRaw)
            assertTrue(show.ok)
            stepDelay()

            val vis2Raw = evalJs(webView, AgentBrowser.queryJs(toggleTargetRef, QueryKind.IS_VISIBLE, QueryPayload(limitChars = 20)))
            val vis2 = AgentBrowser.parseQuery(vis2Raw)
            assertTrue(vis2.ok)
            assertEquals("true", vis2.value)

            val afterRaw = evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false)))
            val afterRender = AgentBrowser.renderSnapshot(
                afterRaw,
                RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true),
            )
            scenario.onActivity { it.setSnapshotText("[KBD] after state changes\n\n${afterRender.text}") }
            captureStep(instrumentation, device, downloadRelativePath, runPrefix, 4)
            dumpSnapshotArtifacts(
                instrumentation = instrumentation,
                relativePath = downloadSnapshotsRelativePath,
                runPrefix = runPrefix,
                step = 4,
                snapshotRaw = afterRaw,
                snapshotText = afterRender.text,
            )
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
        val cutoffSeconds = (System.currentTimeMillis() / 1000L) - (6 * 60 * 60)
        val resolver = instrumentation.targetContext.contentResolver
        val projection = arrayOf(MediaStore.Downloads._ID)
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ? AND ${MediaStore.Downloads.DATE_ADDED} < ?",
            arrayOf("%agent-browser-kotlin/e2e/frames%", "%step-%.png", cutoffSeconds.toString()),
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

    private fun clearOldSnapshots(instrumentation: android.app.Instrumentation) {
        if (Build.VERSION.SDK_INT < 29) return
        val cutoffSeconds = (System.currentTimeMillis() / 1000L) - (6 * 60 * 60)
        val resolver = instrumentation.targetContext.contentResolver
        val projection = arrayOf(MediaStore.Downloads._ID)
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ? AND ${MediaStore.Downloads.DATE_ADDED} < ?",
            arrayOf("%agent-browser-kotlin/e2e/snapshots%", "%-snapshot.%", cutoffSeconds.toString()),
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

    private fun dumpSnapshotArtifacts(
        instrumentation: android.app.Instrumentation,
        relativePath: String,
        runPrefix: String,
        step: Int,
        snapshotRaw: String,
        snapshotText: String,
    ) {
        val stepStr = if (step < 10) "0$step" else step.toString()
        val normalizedJson = AgentBrowser.normalizeJsEvalResult(snapshotRaw)

        val textName = "$runPrefix-step-$stepStr-snapshot.txt"
        val textUri = saveBytesToDownloads(
            instrumentation = instrumentation,
            relativePath = relativePath,
            displayName = textName,
            mimeType = "text/plain",
            bytes = snapshotText.toByteArray(Charsets.UTF_8),
        )
        assertBytesReadable(instrumentation, textUri, minBytes = 16)

        val jsonName = "$runPrefix-step-$stepStr-snapshot.json"
        val jsonUri = saveBytesToDownloads(
            instrumentation = instrumentation,
            relativePath = relativePath,
            displayName = jsonName,
            mimeType = "application/json",
            bytes = normalizedJson.toByteArray(Charsets.UTF_8),
        )
        assertBytesReadable(instrumentation, jsonUri, minBytes = 16)
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

    private fun saveBytesToDownloads(
        instrumentation: android.app.Instrumentation,
        relativePath: String,
        displayName: String,
        mimeType: String,
        bytes: ByteArray,
    ): android.net.Uri {
        val resolver = instrumentation.targetContext.contentResolver

        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed for $displayName")
            resolver.openOutputStream(uri, "w")!!.use { out -> out.write(bytes) }
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }.also {
                resolver.update(uri, it, null, null)
            }
            return uri
        }

        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloads, relativePath.removePrefix("Download/"))
        dir.mkdirs()
        val target = File(dir, displayName)
        target.writeBytes(bytes)
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

    private fun assertBytesReadable(
        instrumentation: android.app.Instrumentation,
        uri: android.net.Uri,
        minBytes: Int,
    ) {
        val resolver = instrumentation.targetContext.contentResolver
        val buf = ByteArray(64)
        var total = 0
        resolver.openInputStream(uri)!!.use { input ->
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                total += read
                if (total >= minBytes) break
            }
        }
        assertTrue("artifact too small: $uri bytes=$total", total >= minBytes)
    }
}
