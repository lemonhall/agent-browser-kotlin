package com.lsl.agent_browser_kotlin

import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.lsl.agent_browser_kotlin.agent.E2eArtifacts
import com.lsl.agent_browser_kotlin.agent.OpenAgenticWebTools
import com.lsl.agent_browser_kotlin.agent.OpenAIChatCompletionsHttpProvider
import com.lsl.agent_browser_kotlin.agent.OpenAIResponsesSseHttpProvider
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
import me.lemonhall.openagentic.sdk.events.ToolResult
import me.lemonhall.openagentic.sdk.permissions.PermissionGate
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
        val protocol = args.getString("OPENAI_PROTOCOL").orEmpty().trim().lowercase().ifEmpty { "responses" }
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

            val allowEval =
                args.getString("ENABLE_WEB_EVAL")
                    .orEmpty()
                    .trim()
                    .lowercase() in setOf("1", "true", "yes", "y")
            val runtime = WebToolRuntime(webView = webView, artifacts = artifacts, allowEval = allowEval)
            val tools = ToolRegistry(OpenAgenticWebTools.all(runtime))
            val provider =
                when (protocol) {
                    "legacy", "chat", "chat-completions" -> OpenAIChatCompletionsHttpProvider(baseUrl = baseUrl)
                    "responses", "sse" -> OpenAIResponsesSseHttpProvider(baseUrl = baseUrl)
                    else -> OpenAIResponsesSseHttpProvider(baseUrl = baseUrl)
                }

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
                3) web_wait(ms=500)
                4) 找到输入框并 web_fill value="hello"
                5) 再对同一个输入框 web_type text="!"
                6) web_click “Apply”
                7) web_snapshot(interactive_only=true)，确认输入框已填入（应为 hello!）
                8) web_click “Toggle Hidden”
                9) 找到 checkbox 并 web_check
                10) 对该 checkbox 调用 web_query(kind="ischecked")，确认返回 true
                11) web_scroll_into_view 到 “Add Item” 按钮（如果它在快照中出现 ref）
                12) 再 web_snapshot(interactive_only=false)，确认页面文本出现 "agree: true"
                
                完成后仅回复：OK
                """.trimIndent()

            val events = runBlocking { OpenAgenticSdk.query(prompt = prompt, options = options).toList() }
            val init = events.filterIsInstance<SystemInit>().firstOrNull()
            val result = events.filterIsInstance<Result>().lastOrNull()
            val toolUses = events.filterIsInstance<ToolUse>()
            val assistantMessages = events.filterIsInstance<AssistantMessage>()

            assertNotNull("missing SystemInit", init)
            assertNotNull("missing Result", result)
            val init0 = init ?: throw AssertionError("missing SystemInit")
            val result0 = result ?: throw AssertionError("missing Result")
            runtime.writeSessionId(init0.sessionId)
            // Persist sessions/events for human review even when assertions fail.
            runCatching {
                val sessionDir = File(storeRoot, "sessions/${init0.sessionId}")
                val eventsPath = File(sessionDir, "events.jsonl")
                val metaPath = File(sessionDir, "meta.json")
                if (eventsPath.exists()) artifacts.writeTextArtifact("${runPrefix}-events.jsonl", eventsPath.readText(Charsets.UTF_8), minBytes = 16)
                if (metaPath.exists()) artifacts.writeTextArtifact("${runPrefix}-meta.json", metaPath.readText(Charsets.UTF_8), minBytes = 16)
            }
            artifacts.setUiText(
                "[AGENT] finished\n\nprovider=${provider.name}\nprotocol=$protocol\n\nsession_id=${init0.sessionId}\n" +
                    "tool_uses=${toolUses.size}\n" +
                    "assistant_msgs=${assistantMessages.size}\n\n" +
                    "finalText=${result0.finalText.take(400)}",
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
            assertTrue("expected finalText to start with OK", result0.finalText.trim().startsWith("OK"))
        }
    }

    @Test
    fun agent_tool_loop_can_use_navigation_tools_and_leave_readable_evidence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)

        val args = InstrumentationRegistry.getArguments()
        val apiKey = args.getString("OPENAI_API_KEY").orEmpty().trim()
        val baseUrl = args.getString("OPENAI_BASE_URL").orEmpty().trim().ifEmpty { "https://api.openai.com/v1" }
        val model = args.getString("MODEL").orEmpty().trim().ifEmpty { "gpt-5.2" }
        val protocol = args.getString("OPENAI_PROTOCOL").orEmpty().trim().lowercase().ifEmpty { "responses" }
        assumeTrue("OPENAI_API_KEY missing; skipping real-agent E2E", apiKey.isNotEmpty())

        val downloadFramesRelativePath = "Download/agent-browser-kotlin/e2e/frames/"
        val downloadSnapshotsRelativePath = "Download/agent-browser-kotlin/e2e/snapshots/"
        val runPrefix = "run-${System.currentTimeMillis()}-agent-nav"

        ActivityScenario.launch(WebViewHarnessActivity::class.java).use { scenario ->
            lateinit var webView: WebView
            scenario.onActivity { webView = it.webView }

            loadUrlAndWait(scenario, "about:blank")

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

            val allowEval =
                args.getString("ENABLE_WEB_EVAL")
                    .orEmpty()
                    .trim()
                    .lowercase() in setOf("1", "true", "yes", "y")
            val runtime = WebToolRuntime(webView = webView, artifacts = artifacts, allowEval = allowEval)
            val tools = ToolRegistry(OpenAgenticWebTools.all(runtime))
            val provider =
                when (protocol) {
                    "legacy", "chat", "chat-completions" -> OpenAIChatCompletionsHttpProvider(baseUrl = baseUrl)
                    "responses", "sse" -> OpenAIResponsesSseHttpProvider(baseUrl = baseUrl)
                    else -> OpenAIResponsesSseHttpProvider(baseUrl = baseUrl)
                }

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
                    maxSteps = 22,
                )

            val prompt =
                """
                你在一个离线网页（Android WebView）里执行任务，只能使用提供的 web_* tools。

                规则：
                - 任何元素操作都必须来自“最新一次 web_snapshot”的 ref；页面变化后 ref 可能失效，必要时先重新 snapshot。
                - 不要尝试获取整页 outerHTML；优先用 snapshot 文本定位，再用 query 读取必要信息。

                目标（按顺序执行）：
                1) web_open(url="file:///android_asset/e2e/nav1.html")
                2) web_snapshot(interactive_only=false)，确认标题包含 "Navigation Fixture: Page 1"
                3) web_click 名称为 "Primary Action" 的按钮
                4) web_snapshot(interactive_only=false)，确认页面文本包含 "status: clicked on nav1"

                5) web_open(url="file:///android_asset/e2e/nav2.html")
                6) web_snapshot(interactive_only=false)，确认页面文本包含 "NAV2: You are on page 2" 且包含 "loads: 1"
                7) web_reload()
                8) web_wait(ms=800)
                9) web_snapshot(interactive_only=false)，确认页面文本包含 "loads: 2"

                10) web_back()
                11) web_snapshot(interactive_only=false)，确认又回到 "Navigation Fixture: Page 1"
                12) web_forward()
                13) web_snapshot(interactive_only=false)，确认回到 "NAV2: You are on page 2"

                完成后仅回复：OK
                """.trimIndent()

            val events = runBlocking { OpenAgenticSdk.query(prompt = prompt, options = options).toList() }
            val init = events.filterIsInstance<SystemInit>().firstOrNull()
            val result = events.filterIsInstance<Result>().lastOrNull()
            val toolUses = events.filterIsInstance<ToolUse>()
            val assistantMessages = events.filterIsInstance<AssistantMessage>()

            assertNotNull("missing SystemInit", init)
            assertNotNull("missing Result", result)
            val init0 = init ?: throw AssertionError("missing SystemInit")
            val result0 = result ?: throw AssertionError("missing Result")
            runtime.writeSessionId(init0.sessionId)
            // Persist sessions/events for human review even when assertions fail.
            runCatching {
                val sessionDir = File(storeRoot, "sessions/${init0.sessionId}")
                val eventsPath = File(sessionDir, "events.jsonl")
                val metaPath = File(sessionDir, "meta.json")
                if (eventsPath.exists()) artifacts.writeTextArtifact("${runPrefix}-events.jsonl", eventsPath.readText(Charsets.UTF_8), minBytes = 16)
                if (metaPath.exists()) artifacts.writeTextArtifact("${runPrefix}-meta.json", metaPath.readText(Charsets.UTF_8), minBytes = 16)
            }
            artifacts.setUiText(
                "[AGENT] finished\n\nprovider=${provider.name}\nprotocol=$protocol\n\nsession_id=${init0.sessionId}\n" +
                    "tool_uses=${toolUses.size}\n" +
                    "assistant_msgs=${assistantMessages.size}\n\n" +
                    "finalText=${result0.finalText.take(400)}",
            )
            artifacts.captureFrame()

            assertTrue("expected tool uses, got 0", toolUses.isNotEmpty())
            val usedTools = toolUses.map { it.name }.toSet()
            assertTrue("expected web_open to be used", usedTools.contains("web_open"))
            assertTrue("expected web_back to be used", usedTools.contains("web_back"))
            assertTrue("expected web_forward to be used", usedTools.contains("web_forward"))
            assertTrue("expected web_reload to be used", usedTools.contains("web_reload"))

            val finalRaw = runBlocking { evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false))) }
            val finalRender = AgentBrowser.renderSnapshot(finalRaw, RenderOptions(maxCharsTotal = 8000, maxNodes = 220, maxDepth = 14, compact = true))
            artifacts.setUiText(
                "[FINAL] agent completed\n\nsession_id=${init.sessionId}\n" +
                    "tool_uses=${toolUses.size}\n\n" +
                    finalRender.text,
            )
            val finalStep = artifacts.captureFrame()
            artifacts.dumpSnapshotArtifacts(snapshotRaw = finalRaw, snapshotText = finalRender.text, step = finalStep)

            assertTrue("expected NAV2 banner in final snapshot", finalRender.text.contains("NAV2: You are on page 2"))
            assertTrue("expected finalText to start with OK", result0.finalText.trim().startsWith("OK"))
        }
    }

    @Test
    fun agent_tool_loop_can_recover_from_element_blocked_and_ref_not_found() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)

        val args = InstrumentationRegistry.getArguments()
        val apiKey = args.getString("OPENAI_API_KEY").orEmpty().trim()
        val baseUrl = args.getString("OPENAI_BASE_URL").orEmpty().trim().ifEmpty { "https://api.openai.com/v1" }
        val model = args.getString("MODEL").orEmpty().trim().ifEmpty { "gpt-5.2" }
        val protocol = args.getString("OPENAI_PROTOCOL").orEmpty().trim().lowercase().ifEmpty { "responses" }
        assumeTrue("OPENAI_API_KEY missing; skipping real-agent E2E", apiKey.isNotEmpty())

        val downloadFramesRelativePath = "Download/agent-browser-kotlin/e2e/frames/"
        val downloadSnapshotsRelativePath = "Download/agent-browser-kotlin/e2e/snapshots/"
        val runPrefix = "run-${System.currentTimeMillis()}-agent-recover"

        ActivityScenario.launch(WebViewHarnessActivity::class.java).use { scenario ->
            lateinit var webView: WebView
            scenario.onActivity { webView = it.webView }

            loadUrlAndWait(scenario, "about:blank")

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

            val allowEval =
                args.getString("ENABLE_WEB_EVAL")
                    .orEmpty()
                    .trim()
                    .lowercase() in setOf("1", "true", "yes", "y")
            val runtime = WebToolRuntime(webView = webView, artifacts = artifacts, allowEval = allowEval)
            val tools = ToolRegistry(OpenAgenticWebTools.all(runtime))
            val provider =
                when (protocol) {
                    "legacy", "chat", "chat-completions" -> OpenAIChatCompletionsHttpProvider(baseUrl = baseUrl)
                    "responses", "sse" -> OpenAIResponsesSseHttpProvider(baseUrl = baseUrl)
                    else -> OpenAIResponsesSseHttpProvider(baseUrl = baseUrl)
                }

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
                    maxSteps = 26,
                )

            val prompt =
                """
                你在一个离线网页（Android WebView）里执行任务，只能使用提供的 web_* tools。

                规则：
                - 任何元素操作都必须来自“最新一次 web_snapshot”的 ref；注意：每次 web_snapshot 都会清除旧 ref 并重分配。
                - 不要尝试获取整页 outerHTML；只用 snapshot + query 读取必要信息。
                - 遇到 element_blocked（overlay/cookie）要先处理遮挡，再重新 snapshot 重试。
                - 遇到 ref_not_found（stale ref）要重新 snapshot 后再重试。
                - 为了留下证据：你必须先触发到 element_blocked，再触发到 ref_not_found（都要在 tool.result 里出现 error.code）。

                目标：在 complex.html 上演示两种失败恢复（必须真的触发到 error.code）：

                A) element_blocked（cookie overlay）
                1) web_open(url="file:///android_asset/e2e/complex.html")
                2) web_snapshot(interactive_only=false)
                3) 【不要先点 Accept cookies】先找到 “Apply” 按钮的 ref，直接 web_click 该 ref（此时 cookie overlay 仍在，预期返回 element_blocked）
                4) 如果返回 element_blocked：找到 “Accept cookies” 按钮 ref 并 web_click；web_wait(ms=800)
                5) web_snapshot(interactive_only=false)
                6) web_fill 把 Query 输入框填入 "hello"
                7) web_click “Apply” 按钮（用最新快照里的 ref）
                8) web_wait(ms=800)
                9) web_snapshot(interactive_only=false)，确认页面出现 "Applied: hello"

                B) ref_not_found（stale ref）
                10) 现在为了演示 stale ref，你必须 **强制** 调用一次：web_click(ref="e404")（这个 ref 在本页一定不存在，预期返回 ref_not_found）
                11) 如果返回 ref_not_found：web_snapshot(interactive_only=false) 并用最新 ref 点击 “Toggle Hidden”
                12) web_wait(ms=800)
                13) web_snapshot(interactive_only=false)，确认出现 "Hidden Action"

                完成后仅回复：OK
                """.trimIndent()

            val events = runBlocking { OpenAgenticSdk.query(prompt = prompt, options = options).toList() }
            val init = events.filterIsInstance<SystemInit>().firstOrNull()
            val result = events.filterIsInstance<Result>().lastOrNull()
            val toolUses = events.filterIsInstance<ToolUse>()
            val toolResults = events.filterIsInstance<ToolResult>()
            val assistantMessages = events.filterIsInstance<AssistantMessage>()

            assertNotNull("missing SystemInit", init)
            assertNotNull("missing Result", result)
            val init0 = init ?: throw AssertionError("missing SystemInit")
            val result0 = result ?: throw AssertionError("missing Result")
            runtime.writeSessionId(init0.sessionId)
            // Persist sessions/events for human review even when assertions fail.
            runCatching {
                val sessionDir = File(storeRoot, "sessions/${init0.sessionId}")
                val eventsPath = File(sessionDir, "events.jsonl")
                val metaPath = File(sessionDir, "meta.json")
                if (eventsPath.exists()) artifacts.writeTextArtifact("${runPrefix}-events.jsonl", eventsPath.readText(Charsets.UTF_8), minBytes = 16)
                if (metaPath.exists()) artifacts.writeTextArtifact("${runPrefix}-meta.json", metaPath.readText(Charsets.UTF_8), minBytes = 16)
            }
            artifacts.setUiText(
                "[AGENT] finished\n\nprovider=${provider.name}\nprotocol=$protocol\n\nsession_id=${init0.sessionId}\n" +
                    "tool_uses=${toolUses.size}\n" +
                    "tool_results=${toolResults.size}\n" +
                    "assistant_msgs=${assistantMessages.size}\n\n" +
                    "finalText=${result0.finalText.take(400)}",
            )
            artifacts.captureFrame()

            assertTrue("expected tool uses, got 0", toolUses.isNotEmpty())

            fun toolResultHasErrorCode(code: String): Boolean {
                for (tr in toolResults) {
                    val out = tr.output as? kotlinx.serialization.json.JsonObject ?: continue
                    val err = out["error"] as? kotlinx.serialization.json.JsonObject ?: continue
                    val c = (err["code"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: continue
                    if (c == code) return true
                }
                return false
            }

            assertTrue("expected element_blocked to occur at least once", toolResultHasErrorCode("element_blocked"))
            assertTrue("expected ref_not_found to occur at least once", toolResultHasErrorCode("ref_not_found"))

            val finalRaw = runBlocking { evalJs(webView, AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false))) }
            val finalRender = AgentBrowser.renderSnapshot(finalRaw, RenderOptions(maxCharsTotal = 9000, maxNodes = 320, maxDepth = 14, compact = true))
            artifacts.setUiText(
                "[FINAL] agent completed\n\nsession_id=${init0.sessionId}\n" +
                    "tool_uses=${toolUses.size}\n\n" +
                    finalRender.text,
            )
            val finalStep = artifacts.captureFrame()
            artifacts.dumpSnapshotArtifacts(snapshotRaw = finalRaw, snapshotText = finalRender.text, step = finalStep)

            assertTrue("expected Applied: hello in final snapshot", finalRender.text.contains("Applied: hello"))
            assertTrue("expected Hidden Action in final snapshot", finalRender.text.contains("Hidden Action"))
            assertTrue("expected finalText to start with OK", result0.finalText.trim().startsWith("OK"))
        }
    }
}
