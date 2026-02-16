package com.lsl.agent_browser_kotlin.agent

import com.lsl.agentbrowser.ActionKind
import com.lsl.agentbrowser.AgentBrowser
import com.lsl.agentbrowser.FillPayload
import com.lsl.agentbrowser.TypePayload
import com.lsl.agentbrowser.PageKind
import com.lsl.agentbrowser.PagePayload
import com.lsl.agentbrowser.QueryKind
import com.lsl.agentbrowser.QueryPayload
import com.lsl.agentbrowser.RenderOptions
import com.lsl.agentbrowser.SelectPayload
import com.lsl.agentbrowser.SnapshotJsOptions
import com.lsl.agentbrowser.openai.WebToolsOpenAiSchema
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import me.lemonhall.openagentic.sdk.tools.OpenAiSchemaTool
import me.lemonhall.openagentic.sdk.tools.Tool
import me.lemonhall.openagentic.sdk.tools.ToolContext
import me.lemonhall.openagentic.sdk.tools.ToolInput
import me.lemonhall.openagentic.sdk.tools.ToolOutput
import me.lemonhall.openagentic.sdk.tools.ToolRegistry
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class WebToolRuntime(
    val webView: WebView,
    private val artifacts: E2eArtifacts,
    private val allowEval: Boolean,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun eval(script: String): String = evalJs(webView, script)

    fun isEvalEnabled(): Boolean = allowEval

    fun runPrefix(): String = artifacts.runPrefix

    fun setUiText(text: String) = artifacts.setUiText(text)

    fun captureFrame(): Int = artifacts.captureFrame()

    fun dumpSnapshotArtifacts(
        snapshotRaw: String,
        snapshotText: String,
        step: Int,
    ) = artifacts.dumpSnapshotArtifacts(snapshotRaw = snapshotRaw, snapshotText = snapshotText, step = step)

    fun writeSessionId(sessionId: String) {
        artifacts.writeTextArtifact(displayName = "${artifacts.runPrefix}-session_id.txt", text = sessionId, minBytes = 16)
    }

    fun parseJsonElementFromJsEval(raw: String): JsonElement {
        val normalized = AgentBrowser.normalizeJsEvalResult(raw)
        return json.parseToJsonElement(normalized)
    }

    suspend fun ensureInjected(): Boolean {
        val check = "(function(){ try { return (typeof window !== 'undefined' && !!window.__agentBrowser); } catch(e){ return false; } })()"
        val ok0 = eval(check).trim() == "true"
        if (ok0) return true
        runCatching { eval(AgentBrowser.getScript()) }
        val ok1 = eval(check).trim() == "true"
        if (ok1) return true
        return false
    }

    suspend fun afterToolDelay() {
        delay(3500)
    }
}

internal object OpenAgenticWebTools {
    fun all(runtime: WebToolRuntime): List<Tool> =
        listOf(
            WebOpenTool(runtime),
            WebBackTool(runtime),
            WebForwardTool(runtime),
            WebReloadTool(runtime),
            WebSnapshotTool(runtime),
            WebClickTool(runtime),
            WebDoubleClickTool(runtime),
            WebFillTool(runtime),
            WebTypeTool(runtime),
            WebSelectTool(runtime),
            WebCheckTool(runtime),
            WebUncheckTool(runtime),
            WebScrollTool(runtime),
            WebPressKeyTool(runtime),
            WebHoverTool(runtime),
            WebScrollIntoViewTool(runtime),
            WebWaitTool(runtime),
            WebQueryTool(runtime),
            WebScreenshotTool(runtime),
            WebEvalTool(runtime),
            WebCloseTool(runtime),
        )
}

private abstract class BaseWebTool(
    protected val runtime: WebToolRuntime,
    private val spec: WebToolsOpenAiSchema.ToolSpec,
) : Tool, OpenAiSchemaTool {
    override val name: String = spec.name
    override val description: String = spec.description

    override fun openAiSchema(ctx: ToolContext, registry: ToolRegistry?): JsonObject = spec.openAiSchema

    protected fun requiredString(input: ToolInput, key: String): String? =
        input[key]?.asString()?.trim()?.takeIf { it.isNotEmpty() }

    protected fun optionalString(input: ToolInput, key: String): String? =
        input[key]?.asString()

    protected fun optionalBool(input: ToolInput, key: String): Boolean? =
        input[key]?.asBoolean()

    protected fun optionalInt(input: ToolInput, key: String): Int? =
        input[key]?.asInt()

    protected fun error(
        code: String,
        message: String,
    ): ToolOutput.Json {
        runtime.setUiText("[TOOL] $name (error)\n\n$code: $message")
        runtime.captureFrame()
        return ToolOutput.Json(
            value =
                buildJsonObject {
                    put("ok", JsonPrimitive(false))
                    put("error", buildJsonObject {
                        put("code", JsonPrimitive(code))
                        put("message", JsonPrimitive(message))
                    })
                },
        )
    }
}

private fun formatStep(step: Int): String = if (step < 10) "0$step" else step.toString()

private fun isBlockedUrlScheme(url: String): Boolean {
    val u = url.trim()
    if (u.isEmpty()) return true
    val lower = u.lowercase()
    if (lower.startsWith("javascript:")) return true
    if (lower.startsWith("data:")) return true
    if (lower.startsWith("vbscript:")) return true
    return false
}

private fun isAllowedOpenUrl(url: String): Boolean {
    if (isBlockedUrlScheme(url)) return false
    val u = url.trim()
    if (u == "about:blank") return true
    return u.startsWith("http://") || u.startsWith("https://") || u.startsWith("file://")
}

private fun webViewNavigateAndWait(
    webView: WebView,
    action: (WebView) -> Unit,
    timeoutMs: Long,
): Boolean {
    val latch = CountDownLatch(1)
    webView.post {
        webView.webViewClient =
            object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    latch.countDown()
                }
            }
        action(webView)
    }
    return latch.await(timeoutMs, TimeUnit.MILLISECONDS)
}

private class WebOpenTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_OPEN,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        val url = requiredString(input, "url") ?: return error("missing_url", "web_open requires url")
        if (!isAllowedOpenUrl(url)) return error("invalid_url", "blocked url scheme")

        runtime.setUiText("[TOOL] web_open(url=${url.take(160)})")
        runtime.captureFrame()

        val ok = webViewNavigateAndWait(webView = runtime.webView, action = { it.loadUrl(url) }, timeoutMs = 12_000)
        if (!ok) return error("timeout", "web_open timed out")

        if (!runtime.ensureInjected()) return error("not_injected", "agent-browser.js not injected")
        val infoRaw = runtime.eval(AgentBrowser.pageJs(PageKind.INFO, PagePayload()))
        val info = AgentBrowser.parsePageSafe(infoRaw)
        runtime.setUiText("[TOOL] web_open done\n\nurl=${info.url}\ntitle=${info.title}")
        runtime.captureFrame()
        runtime.afterToolDelay()

        return ToolOutput.Json(
            value =
                buildJsonObject {
                    put("ok", JsonPrimitive(info.ok))
                    put("url", JsonPrimitive(info.url ?: url))
                    put("title", JsonPrimitive(info.title ?: ""))
                },
        )
    }
}

private class WebBackTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_BACK,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        runtime.setUiText("[TOOL] web_back()")
        runtime.captureFrame()

        webViewNavigateAndWait(webView = runtime.webView, action = { it.goBack() }, timeoutMs = 10_000)

        if (!runtime.ensureInjected()) return error("not_injected", "agent-browser.js not injected")
        val infoRaw = runtime.eval(AgentBrowser.pageJs(PageKind.INFO, PagePayload()))
        val info = AgentBrowser.parsePageSafe(infoRaw)
        runtime.setUiText("[TOOL] web_back done\n\nurl=${info.url}\ntitle=${info.title}")
        runtime.captureFrame()
        runtime.afterToolDelay()

        return ToolOutput.Json(
            value =
                buildJsonObject {
                    put("ok", JsonPrimitive(info.ok))
                    put("url", JsonPrimitive(info.url ?: ""))
                    put("title", JsonPrimitive(info.title ?: ""))
                },
        )
    }
}

private class WebForwardTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_FORWARD,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        runtime.setUiText("[TOOL] web_forward()")
        runtime.captureFrame()

        webViewNavigateAndWait(webView = runtime.webView, action = { it.goForward() }, timeoutMs = 10_000)

        if (!runtime.ensureInjected()) return error("not_injected", "agent-browser.js not injected")
        val infoRaw = runtime.eval(AgentBrowser.pageJs(PageKind.INFO, PagePayload()))
        val info = AgentBrowser.parsePageSafe(infoRaw)
        runtime.setUiText("[TOOL] web_forward done\n\nurl=${info.url}\ntitle=${info.title}")
        runtime.captureFrame()
        runtime.afterToolDelay()

        return ToolOutput.Json(
            value =
                buildJsonObject {
                    put("ok", JsonPrimitive(info.ok))
                    put("url", JsonPrimitive(info.url ?: ""))
                    put("title", JsonPrimitive(info.title ?: ""))
                },
        )
    }
}

private class WebReloadTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_RELOAD,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        runtime.setUiText("[TOOL] web_reload()")
        runtime.captureFrame()

        webViewNavigateAndWait(webView = runtime.webView, action = { it.reload() }, timeoutMs = 12_000)

        if (!runtime.ensureInjected()) return error("not_injected", "agent-browser.js not injected")
        val infoRaw = runtime.eval(AgentBrowser.pageJs(PageKind.INFO, PagePayload()))
        val info = AgentBrowser.parsePageSafe(infoRaw)
        runtime.setUiText("[TOOL] web_reload done\n\nurl=${info.url}\ntitle=${info.title}")
        runtime.captureFrame()
        runtime.afterToolDelay()

        return ToolOutput.Json(
            value =
                buildJsonObject {
                    put("ok", JsonPrimitive(info.ok))
                    put("url", JsonPrimitive(info.url ?: ""))
                    put("title", JsonPrimitive(info.title ?: ""))
                },
        )
    }
}

private class WebSnapshotTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_SNAPSHOT,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        if (!runtime.ensureInjected()) return error("not_injected", "agent-browser.js not injected")
        val interactiveOnly = optionalBool(input, "interactive_only") ?: true
        val cursorInteractive = optionalBool(input, "cursor_interactive") ?: false
        val scopeRaw = optionalString(input, "scope")?.trim()
        val scope =
            when {
                scopeRaw.isNullOrBlank() -> null
                scopeRaw == "document.body" -> null
                else -> scopeRaw
            }

        val raw =
            runtime.eval(
                AgentBrowser.snapshotJs(
                    SnapshotJsOptions(
                        interactiveOnly = interactiveOnly,
                        cursorInteractive = cursorInteractive,
                        scope = scope,
                    ),
                ),
            )
        val parsed = AgentBrowser.parseSnapshotSafe(raw)
        if (!parsed.ok) {
            return error(code = parsed.error?.code ?: "snapshot_failed", message = parsed.error?.message ?: "snapshot failed")
        }

        val rendered =
            AgentBrowser.renderSnapshot(
                raw,
                RenderOptions(maxCharsTotal = 8000, maxNodes = 260, maxDepth = 14, compact = true),
            )

        runtime.setUiText(
            "[TOOL] web_snapshot(interactive_only=$interactiveOnly,cursor_interactive=$cursorInteractive)\n\n" +
                (parsed.meta?.url ?: "") +
                "\n\n" +
                rendered.text,
        )
        val step = runtime.captureFrame()
        runtime.dumpSnapshotArtifacts(snapshotRaw = raw, snapshotText = rendered.text, step = step)
        runtime.afterToolDelay()

        return ToolOutput.Json(
            value =
                buildJsonObject {
                    put("ok", JsonPrimitive(true))
                    put("url", JsonPrimitive(parsed.meta?.url ?: ""))
                    put("snapshot_text", JsonPrimitive(rendered.text))
                    put("truncated", JsonPrimitive(rendered.stats.truncated))
                    put("truncate_reasons", JsonArray(rendered.stats.truncateReasons.map { JsonPrimitive(it) }))
                    put("refs_count", JsonPrimitive(parsed.refs.size))
                },
        )
    }
}

private class WebClickTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_CLICK,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        if (!runtime.ensureInjected()) return error("not_injected", "agent-browser.js not injected")
        val ref = requiredString(input, "ref") ?: return error("missing_ref", "web_click requires ref")
        val raw = runtime.eval(AgentBrowser.actionJs(ref, ActionKind.CLICK))
        runtime.setUiText("[TOOL] web_click(ref=$ref)")
        runtime.captureFrame()
        runtime.afterToolDelay()
        return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(raw))
    }
}

private class WebDoubleClickTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_DBLCLICK,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        if (!runtime.ensureInjected()) return error("not_injected", "agent-browser.js not injected")
        val ref = requiredString(input, "ref") ?: return error("missing_ref", "web_dblclick requires ref")
        runtime.setUiText("[TOOL] web_dblclick(ref=$ref)")
        runtime.captureFrame()

        val firstRaw = runtime.eval(AgentBrowser.actionJs(ref, ActionKind.CLICK))
        val first = AgentBrowser.parseActionSafe(firstRaw)
        if (!first.ok) {
            runtime.afterToolDelay()
            return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(firstRaw))
        }
        delay(80)
        val secondRaw = runtime.eval(AgentBrowser.actionJs(ref, ActionKind.CLICK))
        runtime.afterToolDelay()
        return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(secondRaw))
    }
}

private class WebFillTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_FILL,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        if (!runtime.ensureInjected()) return error("not_injected", "agent-browser.js not injected")
        val ref = requiredString(input, "ref") ?: return error("missing_ref", "web_fill requires ref")
        val value = requiredString(input, "value") ?: return error("missing_value", "web_fill requires value")
        val raw = runtime.eval(AgentBrowser.actionJs(ref, ActionKind.FILL, FillPayload(value)))
        runtime.setUiText("[TOOL] web_fill(ref=$ref)\n\nlen=${value.length}")
        runtime.captureFrame()
        runtime.afterToolDelay()
        return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(raw))
    }
}

private class WebTypeTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_TYPE,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        if (!runtime.ensureInjected()) return error("not_injected", "agent-browser.js not injected")
        val ref = requiredString(input, "ref") ?: return error("missing_ref", "web_type requires ref")
        val text = requiredString(input, "text") ?: return error("missing_text", "web_type requires text")
        val raw = runtime.eval(AgentBrowser.actionJs(ref, ActionKind.TYPE, TypePayload(text)))
        runtime.setUiText("[TOOL] web_type(ref=$ref)\n\nlen=${text.length}")
        runtime.captureFrame()
        runtime.afterToolDelay()
        return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(raw))
    }
}

private class WebSelectTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_SELECT,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        if (!runtime.ensureInjected()) return error("not_injected", "agent-browser.js not injected")
        val ref = requiredString(input, "ref") ?: return error("missing_ref", "web_select requires ref")
        val values = (input["values"] as? JsonArray)?.mapNotNull { it.asString()?.trim()?.takeIf { s -> s.isNotEmpty() } } ?: emptyList()
        if (values.isEmpty()) return error("missing_values", "web_select requires non-empty values[]")
        val raw = runtime.eval(AgentBrowser.actionJs(ref, ActionKind.SELECT, SelectPayload(values)))
        runtime.setUiText("[TOOL] web_select(ref=$ref)\n\nvalues=${values.joinToString(",")}")
        runtime.captureFrame()
        runtime.afterToolDelay()
        return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(raw))
    }
}

private class WebCheckTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_CHECK,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        if (!runtime.ensureInjected()) return error("not_injected", "agent-browser.js not injected")
        val ref = requiredString(input, "ref") ?: return error("missing_ref", "web_check requires ref")
        val raw = runtime.eval(AgentBrowser.actionJs(ref, ActionKind.CHECK))
        runtime.setUiText("[TOOL] web_check(ref=$ref)")
        runtime.captureFrame()
        runtime.afterToolDelay()
        return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(raw))
    }
}

private class WebUncheckTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_UNCHECK,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        if (!runtime.ensureInjected()) return error("not_injected", "agent-browser.js not injected")
        val ref = requiredString(input, "ref") ?: return error("missing_ref", "web_uncheck requires ref")
        val raw = runtime.eval(AgentBrowser.actionJs(ref, ActionKind.UNCHECK))
        runtime.setUiText("[TOOL] web_uncheck(ref=$ref)")
        runtime.captureFrame()
        runtime.afterToolDelay()
        return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(raw))
    }
}

private class WebScrollTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_SCROLL,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        if (!runtime.ensureInjected()) return error("not_injected", "agent-browser.js not injected")
        val direction = requiredString(input, "direction") ?: return error("missing_direction", "web_scroll requires direction")
        val amount = optionalInt(input, "amount") ?: 300
        val raw = runtime.eval(AgentBrowser.pageJs(PageKind.SCROLL, PagePayload(deltaX = if (direction == "left") -amount else if (direction == "right") amount else 0, deltaY = if (direction == "up") -amount else if (direction == "down") amount else 0)))
        runtime.setUiText("[TOOL] web_scroll(direction=$direction,amount=$amount)")
        runtime.captureFrame()
        runtime.afterToolDelay()
        return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(raw))
    }
}

private class WebPressKeyTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_PRESS_KEY,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        if (!runtime.ensureInjected()) return error("not_injected", "agent-browser.js not injected")
        val key = requiredString(input, "key") ?: return error("missing_key", "web_press_key requires key")
        val raw = runtime.eval(AgentBrowser.pageJs(PageKind.PRESS_KEY, PagePayload(key = key)))
        runtime.setUiText("[TOOL] web_press_key(key=$key)")
        runtime.captureFrame()
        runtime.afterToolDelay()
        return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(raw))
    }
}

private class WebWaitTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_WAIT,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        val ms = (optionalInt(input, "ms") ?: 0).coerceAtLeast(0)
        val timeoutMs = (optionalInt(input, "timeout_ms") ?: 5000).coerceAtLeast(0)
        val pollMs = (optionalInt(input, "poll_ms") ?: 100).coerceAtLeast(25)
        val selector = optionalString(input, "selector")?.trim().orEmpty()
        val text = optionalString(input, "text")?.trim().orEmpty()
        val url = optionalString(input, "url")?.trim().orEmpty()

        runtime.setUiText("[TOOL] web_wait(ms=$ms,timeout_ms=$timeoutMs)\n\nselector=${selector.take(120)}\ntext=${text.take(120)}\nurl=${url.take(120)}")
        runtime.captureFrame()

        if (ms > 0) {
            delay(ms.toLong())
            return ToolOutput.Json(value = buildJsonObject { put("ok", JsonPrimitive(true)); put("waited_ms", JsonPrimitive(ms)) })
        }

        if (selector.isEmpty() && text.isEmpty() && url.isEmpty()) {
            return error("invalid_wait", "web_wait requires one of: ms/selector/text/url")
        }

        val started = System.currentTimeMillis()
        while (System.currentTimeMillis() - started < timeoutMs) {
            val selectorOk =
                if (selector.isNotEmpty()) {
                    val selEsc = selector.replace("\\", "\\\\").replace("'", "\\'")
                    val script =
                        "(function(){ try { return !!document.querySelector('$selEsc'); } catch(e){ return false; } })()"
                    runtime.eval(script).trim() == "true"
                } else {
                    true
                }
            val textOk =
                if (text.isNotEmpty()) {
                    val textEsc = text.replace("\\", "\\\\").replace("'", "\\'")
                    val script =
                        "(function(){ try { var b=document.body; var t=b?(b.innerText||b.textContent||''):''; return t.indexOf('$textEsc')>=0; } catch(e){ return false; } })()"
                    runtime.eval(script).trim() == "true"
                } else {
                    true
                }
            val urlOk =
                if (url.isNotEmpty()) {
                    val urlEsc = url.replace("\\", "\\\\").replace("'", "\\'")
                    val script =
                        "(function(){ try { return (location && location.href ? String(location.href) : '').indexOf('$urlEsc')>=0; } catch(e){ return false; } })()"
                    runtime.eval(script).trim() == "true"
                } else {
                    true
                }
            if (selectorOk && textOk && urlOk) {
                val waited = (System.currentTimeMillis() - started).toInt().coerceAtLeast(0)
                return ToolOutput.Json(value = buildJsonObject { put("ok", JsonPrimitive(true)); put("waited_ms", JsonPrimitive(waited)) })
            }
            delay(pollMs.toLong())
        }

        return error("timeout", "web_wait timed out")
    }
}

private class WebHoverTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_HOVER,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        if (!runtime.ensureInjected()) return error("not_injected", "agent-browser.js not injected")
        val ref = requiredString(input, "ref") ?: return error("missing_ref", "web_hover requires ref")
        val raw = runtime.eval(AgentBrowser.actionJs(ref, ActionKind.HOVER))
        runtime.setUiText("[TOOL] web_hover(ref=$ref)")
        runtime.captureFrame()
        runtime.afterToolDelay()
        return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(raw))
    }
}

private class WebScrollIntoViewTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_SCROLL_INTO_VIEW,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        if (!runtime.ensureInjected()) return error("not_injected", "agent-browser.js not injected")
        val ref = requiredString(input, "ref") ?: return error("missing_ref", "web_scroll_into_view requires ref")
        val raw = runtime.eval(AgentBrowser.actionJs(ref, ActionKind.SCROLL_INTO_VIEW))
        runtime.setUiText("[TOOL] web_scroll_into_view(ref=$ref)")
        runtime.captureFrame()
        runtime.afterToolDelay()
        return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(raw))
    }
}

private class WebQueryTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_QUERY,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        if (!runtime.ensureInjected()) return error("not_injected", "agent-browser.js not injected")
        val ref = requiredString(input, "ref") ?: return error("missing_ref", "web_query requires ref")
        val kind = requiredString(input, "kind") ?: return error("missing_kind", "web_query requires kind")
        val maxLength = optionalInt(input, "max_length") ?: 4000
        val queryKind =
            when (kind.trim()) {
                "text" -> QueryKind.TEXT
                "html" -> QueryKind.HTML
                "outerHTML" -> QueryKind.OUTER_HTML
                "value" -> QueryKind.VALUE
                "attrs" -> QueryKind.ATTRS
                "computed_styles" -> QueryKind.COMPUTED_STYLES
                "isvisible" -> QueryKind.IS_VISIBLE
                "isenabled" -> QueryKind.IS_ENABLED
                "ischecked" -> QueryKind.IS_CHECKED
                else -> null
            } ?: return error("invalid_kind", "web_query kind must be one of: text/html/outerHTML/value/attrs/computed_styles/isvisible/isenabled/ischecked")
        val raw = runtime.eval(AgentBrowser.queryJs(ref, queryKind, QueryPayload(limitChars = maxLength)))
        runtime.setUiText("[TOOL] web_query(ref=$ref,kind=$kind)")
        runtime.captureFrame()
        runtime.afterToolDelay()
        return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(raw))
    }
}

private class WebScreenshotTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_SCREENSHOT,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        val label = optionalString(input, "label")?.trim().orEmpty()
        runtime.setUiText("[TOOL] web_screenshot()\n\n$label")
        val step = runtime.captureFrame()
        runtime.afterToolDelay()
        val displayName = "${runtime.runPrefix()}-step-${formatStep(step)}.png"
        return ToolOutput.Json(
            value =
                buildJsonObject {
                    put("ok", JsonPrimitive(true))
                    put("step", JsonPrimitive(step))
                    put("display_name", JsonPrimitive(displayName))
                },
        )
    }
}

private class WebEvalTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_EVAL,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        if (!runtime.isEvalEnabled()) return error("disabled", "web_eval is disabled (set ENABLE_WEB_EVAL=1)")
        val js = requiredString(input, "js") ?: return error("missing_js", "web_eval requires js")
        val maxLen = (optionalInt(input, "max_length") ?: 2000).coerceAtLeast(64)

        val script =
            "(function(){ try { return ($js); } catch(e){ return 'ERROR: ' + String(e); } })()"
        val raw = runtime.eval(script)
        val normalized = AgentBrowser.normalizeJsEvalResult(raw)
        val truncated = normalized.length > maxLen
        val value = if (truncated) normalized.take(maxLen) else normalized

        runtime.setUiText("[TOOL] web_eval(len=${js.length})\n\n" + value.take(600))
        runtime.captureFrame()
        runtime.afterToolDelay()

        return ToolOutput.Json(
            value =
                buildJsonObject {
                    put("ok", JsonPrimitive(true))
                    put("value", JsonPrimitive(value))
                    put("truncated", JsonPrimitive(truncated))
                },
        )
    }
}

private class WebCloseTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        spec = WebToolsOpenAiSchema.WEB_CLOSE,
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        runtime.setUiText("[TOOL] web_close()")
        runtime.captureFrame()
        webViewNavigateAndWait(webView = runtime.webView, action = { it.loadUrl("about:blank") }, timeoutMs = 10_000)
        runtime.afterToolDelay()
        return ToolOutput.Json(value = buildJsonObject { put("ok", JsonPrimitive(true)) })
    }
}

private fun JsonElement.asString(): String? =
    (this as? JsonPrimitive)?.content

private fun JsonElement.asBoolean(): Boolean? =
    (this as? JsonPrimitive)?.booleanOrNull

private fun JsonElement.asInt(): Int? =
    (this as? JsonPrimitive)?.intOrNull
