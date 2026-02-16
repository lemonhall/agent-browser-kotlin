package com.lsl.agent_browser_kotlin

import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.lsl.agent_browser_kotlin.agent.E2eArtifacts
import com.lsl.agent_browser_kotlin.agent.OpenAgenticWebTools
import com.lsl.agent_browser_kotlin.agent.WebToolRuntime
import com.lsl.agent_browser_kotlin.agent.clearOldFrames
import com.lsl.agent_browser_kotlin.agent.clearOldSnapshots
import com.lsl.agent_browser_kotlin.agent.evalJs
import com.lsl.agent_browser_kotlin.agent.loadUrlAndWait
import com.lsl.agent_browser_kotlin.agent.stepDelay
import com.lsl.agentbrowser.AgentBrowser
import com.lsl.agentbrowser.RenderOptions
import com.lsl.agentbrowser.SnapshotJsOptions
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.lemonhall.openagentic.sdk.events.Result
import me.lemonhall.openagentic.sdk.events.AssistantMessage
import me.lemonhall.openagentic.sdk.events.SystemInit
import me.lemonhall.openagentic.sdk.events.ToolUse
import me.lemonhall.openagentic.sdk.permissions.PermissionGate
import me.lemonhall.openagentic.sdk.providers.OpenAIResponsesHttpProvider
import me.lemonhall.openagentic.sdk.runtime.OpenAgenticOptions
import me.lemonhall.openagentic.sdk.runtime.OpenAgenticSdk
import me.lemonhall.openagentic.sdk.sessions.FileSessionStore
import me.lemonhall.openagentic.sdk.tools.ToolRegistry
import okio.Path.Companion.toPath
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WebViewAgentE2eTest {
    @Test
    fun agent_tool_loop_can_drive_webview_and_leave_human_visible_evidence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)

        val args = InstrumentationRegistry.getArguments()
        val apiKey = args.getString("OPENAI_API_KEY").orEmpty().trim()
        val baseUrl = args.getString("OPENAI_BASE_URL").orEmpty().trim().ifEmpty { "https://api.openai.com/v1" }
        val model = args.getString("MODEL").orEmpty().trim().ifEmpty { "gpt-5.2" }
        assumeTrue("OPENAI_API_KEY missing; skipping real-agent E2E", apiKey.isNotEmpty())

        val downloadFramesRelativePath = "Download/agent-browser-kotlin/e2e/frames/"
        val downloadSnapshotsRelativePath = "Download/agent-browser-kotlin/e2e/snapshots/"
        val runPrefix = "run-${System.currentTimeMillis()}-agent"

        ActivityScenario.launch(WebViewHarnessActivity::class.java).use { scenario ->
            lateinit var webView: WebView
            scenario.onActivity { webView = it.webView }

            loadUrlAndWait(scenario, "file:///android_asset/e2e/complex.html")

            clearOldFrames(instrumentation)
            clearOldSnapshots(instrumentation)
            stepDelay()

            runBlocking { evalJs(webView, AgentBrowser.getScript()) }
            scenario.onActivity { it.setSnapshotText("[BOOT] injected agent-browser.js\n\nrunPrefix=$runPrefix") }

            val artifacts =
                E2eArtifacts(
                    instrumentation = instrumentation,
                    device = device,
                    scenario = scenario,
                    runPrefix = runPrefix,
                    framesRelativePath = downloadFramesRelativePath,
                    snapshotsRelativePath = downloadSnapshotsRelativePath,
                    nextStep = 1,
                )
            artifacts.captureFrame()
            stepDelay()

            val storeRoot = File(instrumentation.targetContext.filesDir, ".agents").absolutePath
            val sessionStore = FileSessionStore.system(storeRoot)

            val runtime = WebToolRuntime(webView = webView, artifacts = artifacts)
            val tools = ToolRegistry(OpenAgenticWebTools.all(runtime))
            val provider = OpenAIResponsesHttpProvider(baseUrl = baseUrl)

            val options =
                OpenAgenticOptions(
                    provider = provider,
                    model = model,
                    apiKey = apiKey,
                    cwd = instrumentation.targetContext.filesDir.absolutePath.toPath(),
                    projectDir = instrumentation.targetContext.filesDir.absolutePath.toPath(),
                    tools = tools,
                    allowedTools = tools.names().toSet(),
                    permissionGate = PermissionGate.bypass(),
                    sessionStore = sessionStore,
                    maxSteps = 18,
                )

            val prompt =
                """
                你在一个离线网页（Android WebView）里执行任务，只能使用提供的 web_* tools。
                
                规则：
                - 所有元素操作必须使用最新 snapshot 中的 ref（ref 短生命周期，页面变化后就重新 snapshot）。
                - 不要尝试获取整页 outerHTML；只要用 snapshot_text / query 读取必要信息。
                
                目标步骤（按顺序）：
                1) web_snapshot(interactive_only=false)
                2) 找到并 web_click 名称为 “Accept cookies” 的按钮
                3) 找到输入框并 web_fill value="hello"
                4) web_snapshot(interactive_only=true)，确认输入框已填入
                5) web_click “Toggle Hidden”
                6) 找到 checkbox 并 web_check
                7) 再 web_snapshot(interactive_only=false)，确认页面文本出现 "agree: true"
                
                完成后仅回复：OK
                """.trimIndent()

            val events = runBlocking { OpenAgenticSdk.query(prompt = prompt, options = options).toList() }
            val init = events.filterIsInstance<SystemInit>().firstOrNull()
            val result = events.filterIsInstance<Result>().lastOrNull()
            val toolUses = events.filterIsInstance<ToolUse>()
            val assistantMessages = events.filterIsInstance<AssistantMessage>()

            assertNotNull("missing SystemInit", init)
            assertNotNull("missing Result", result)
            runtime.writeSessionId(init!!.sessionId)
            // Persist sessions/events for human review even when assertions fail.
            runCatching {
                val sessionDir = File(storeRoot, "sessions/${init.sessionId}")
                val eventsPath = File(sessionDir, "events.jsonl")
                val metaPath = File(sessionDir, "meta.json")
                if (eventsPath.exists()) artifacts.writeTextArtifact("${runPrefix}-events.jsonl", eventsPath.readText(Charsets.UTF_8), minBytes = 16)
                if (metaPath.exists()) artifacts.writeTextArtifact("${runPrefix}-meta.json", metaPath.readText(Charsets.UTF_8), minBytes = 16)
            }
            artifacts.setUiText(
                "[AGENT] finished\n\nsession_id=${init.sessionId}\n" +
                    "tool_uses=${toolUses.size}\n" +
                    "assistant_msgs=${assistantMessages.size}\n\n" +
                    "finalText=${result!!.finalText.take(400)}",
            )
            artifacts.captureFrame()

            assertTrue("expected tool uses, got 0", toolUses.isNotEmpty())
            assertTrue("expected web_snapshot to be used", toolUses.any { it.name == "web_snapshot" })

            // Deterministic final assertion: agree should be true after CHECK.
            val finalRaw = runBlocking { evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false))) }
            val finalRender = AgentBrowser.renderSnapshot(finalRaw, RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true))
            artifacts.setUiText(
                "[FINAL] agent completed\n\nsession_id=${init.sessionId}\n" +
                    "tool_uses=${toolUses.size}\n\n" +
                    finalRender.text,
            )
            val finalStep = artifacts.captureFrame()
            artifacts.dumpSnapshotArtifacts(snapshotRaw = finalRaw, snapshotText = finalRender.text, step = finalStep)

            assertTrue("expected agree: true in final snapshot", finalRender.text.contains("agree: true"))
            assertTrue("expected finalText to start with OK", (result!!.finalText ?: "").trim().startsWith("OK"))
        }
    }
}
