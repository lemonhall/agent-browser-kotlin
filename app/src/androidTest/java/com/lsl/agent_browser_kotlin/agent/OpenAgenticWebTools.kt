package com.lsl.agent_browser_kotlin.agent

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
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import me.lemonhall.openagentic.sdk.tools.OpenAiSchemaTool
import me.lemonhall.openagentic.sdk.tools.Tool
import me.lemonhall.openagentic.sdk.tools.ToolContext
import me.lemonhall.openagentic.sdk.tools.ToolInput
import me.lemonhall.openagentic.sdk.tools.ToolOutput
import me.lemonhall.openagentic.sdk.tools.ToolRegistry
import android.webkit.WebView

internal class WebToolRuntime(
    private val webView: WebView,
    private val artifacts: E2eArtifacts,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun eval(script: String): String = evalJs(webView, script)

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

    suspend fun afterToolDelay() {
        delay(3500)
    }
}

internal object OpenAgenticWebTools {
    fun all(runtime: WebToolRuntime): List<Tool> =
        listOf(
            WebSnapshotTool(runtime),
            WebClickTool(runtime),
            WebFillTool(runtime),
            WebSelectTool(runtime),
            WebCheckTool(runtime),
            WebUncheckTool(runtime),
            WebScrollTool(runtime),
            WebPressKeyTool(runtime),
            WebGetTextTool(runtime),
            WebGetValueTool(runtime),
            WebClearTool(runtime),
            WebFocusTool(runtime),
            WebHoverTool(runtime),
            WebScrollIntoViewTool(runtime),
            WebQueryTool(runtime),
        )
}

private abstract class BaseWebTool(
    protected val runtime: WebToolRuntime,
    override val name: String,
    override val description: String,
    private val schema: JsonObject,
) : Tool, OpenAiSchemaTool {
    override fun openAiSchema(ctx: ToolContext, registry: ToolRegistry?): JsonObject = schema

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

private class WebSnapshotTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        name = "web_snapshot",
        description = "获取当前页面的元素快照。返回带 [ref=eN] 标注的元素树；后续操作使用 ref 指定目标。",
        schema =
            schemaFunction(
                name = "web_snapshot",
                description = "获取当前页面的元素快照。返回带 [ref=eN] 标注的元素树；后续操作使用 ref 指定目标。",
                properties =
                    buildJsonObject {
                        put("interactive_only", schemaBool("true=只返回可交互元素；false=同时返回标题/列表项/图片等内容元素。", default = true))
                        put("cursor_interactive", schemaBool("true=额外把 cursor:pointer/onclick/tabindex 等“非标准交互元素”纳入 refs（对齐 PRD-V4 cursorInteractive gate）。", default = false))
                        put("scope", schemaString("可选：CSS selector，将 snapshot 限定在该元素子树内（默认 document.body）。"))
                    },
                required = emptyList(),
            ),
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        val interactiveOnly = optionalBool(input, "interactive_only") ?: true
        val cursorInteractive = optionalBool(input, "cursor_interactive") ?: false
        val scope = optionalString(input, "scope")?.trim()?.takeIf { it.isNotEmpty() }

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
                    put("truncated", JsonPrimitive(rendered.stats?.truncated == true))
                    put("truncate_reasons", JsonArray((rendered.stats?.truncateReasons ?: emptyList()).map { JsonPrimitive(it) }))
                    put("refs_count", JsonPrimitive(parsed.refs.size))
                },
        )
    }
}

private class WebClickTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        name = "web_click",
        description = "点击指定 ref 的元素。",
        schema = schemaFunction("web_click", "点击指定 ref 的元素。", buildJsonObject { put("ref", schemaString()) }, listOf("ref")),
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        val ref = requiredString(input, "ref") ?: return error("missing_ref", "web_click requires ref")
        val raw = runtime.eval(AgentBrowser.actionJs(ref, ActionKind.CLICK))
        runtime.setUiText("[TOOL] web_click(ref=$ref)")
        runtime.captureFrame()
        runtime.afterToolDelay()
        return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(raw))
    }
}

private class WebFillTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        name = "web_fill",
        description = "在指定 ref 的输入框中填入文本（会先清空）。",
        schema =
            schemaFunction(
                "web_fill",
                "在指定 ref 的输入框中填入文本（会先清空）。",
                buildJsonObject { put("ref", schemaString()); put("value", schemaString()) },
                listOf("ref", "value"),
            ),
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        val ref = requiredString(input, "ref") ?: return error("missing_ref", "web_fill requires ref")
        val value = requiredString(input, "value") ?: return error("missing_value", "web_fill requires value")
        val raw = runtime.eval(AgentBrowser.actionJs(ref, ActionKind.FILL, FillPayload(value)))
        runtime.setUiText("[TOOL] web_fill(ref=$ref)\n\nlen=${value.length}")
        runtime.captureFrame()
        runtime.afterToolDelay()
        return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(raw))
    }
}

private class WebSelectTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        name = "web_select",
        description = "在指定 ref 的下拉框中选择选项（按 option.value 或显示文本匹配）。",
        schema =
            schemaFunction(
                "web_select",
                "在指定 ref 的下拉框中选择选项（按 option.value 或显示文本匹配）。",
                buildJsonObject {
                    put("ref", schemaString())
                    put("values", buildJsonObject { put("type", JsonPrimitive("array")); put("items", buildJsonObject { put("type", JsonPrimitive("string")) }) })
                },
                listOf("ref", "values"),
            ),
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
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
        name = "web_check",
        description = "勾选指定 ref 的 checkbox 或 radio。",
        schema = schemaFunction("web_check", "勾选指定 ref 的 checkbox 或 radio。", buildJsonObject { put("ref", schemaString()) }, listOf("ref")),
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
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
        name = "web_uncheck",
        description = "取消勾选指定 ref 的 checkbox。",
        schema = schemaFunction("web_uncheck", "取消勾选指定 ref 的 checkbox。", buildJsonObject { put("ref", schemaString()) }, listOf("ref")),
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
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
        name = "web_scroll",
        description = "页面滚动。direction: up/down/left/right。",
        schema =
            schemaFunction(
                "web_scroll",
                "页面滚动。direction: up/down/left/right。",
                buildJsonObject {
                    put("direction", buildJsonObject { put("type", JsonPrimitive("string")); put("enum", JsonArray(listOf("up", "down", "left", "right").map { JsonPrimitive(it) })) })
                    put("amount", schemaInt(default = 300))
                },
                listOf("direction"),
            ),
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
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
        name = "web_press_key",
        description = "在当前焦点元素上按键（如 Enter/Tab/Escape/Backspace/ArrowDown）。",
        schema = schemaFunction("web_press_key", "在当前焦点元素上按键（如 Enter/Tab/Escape/Backspace/ArrowDown）。", buildJsonObject { put("key", schemaString()) }, listOf("key")),
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        val key = requiredString(input, "key") ?: return error("missing_key", "web_press_key requires key")
        val raw = runtime.eval(AgentBrowser.pageJs(PageKind.PRESS_KEY, PagePayload(key = key)))
        runtime.setUiText("[TOOL] web_press_key(key=$key)")
        runtime.captureFrame()
        runtime.afterToolDelay()
        return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(raw))
    }
}

private class WebGetTextTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        name = "web_get_text",
        description = "获取指定 ref 元素的文本内容（用于读取详情）。",
        schema =
            schemaFunction(
                "web_get_text",
                "获取指定 ref 元素的文本内容（用于读取详情）。",
                buildJsonObject { put("ref", schemaString()); put("max_length", schemaInt(default = 2000)) },
                listOf("ref"),
            ),
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        val ref = requiredString(input, "ref") ?: return error("missing_ref", "web_get_text requires ref")
        val maxLength = optionalInt(input, "max_length") ?: 2000
        val raw = runtime.eval(AgentBrowser.queryJs(ref, QueryKind.TEXT, QueryPayload(limitChars = maxLength)))
        runtime.setUiText("[TOOL] web_get_text(ref=$ref)")
        runtime.captureFrame()
        runtime.afterToolDelay()
        return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(raw))
    }
}

private class WebGetValueTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        name = "web_get_value",
        description = "获取指定 ref 输入框的当前值。",
        schema = schemaFunction("web_get_value", "获取指定 ref 输入框的当前值。", buildJsonObject { put("ref", schemaString()) }, listOf("ref")),
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        val ref = requiredString(input, "ref") ?: return error("missing_ref", "web_get_value requires ref")
        val raw = runtime.eval(AgentBrowser.queryJs(ref, QueryKind.VALUE, QueryPayload(limitChars = 4000)))
        runtime.setUiText("[TOOL] web_get_value(ref=$ref)")
        runtime.captureFrame()
        runtime.afterToolDelay()
        return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(raw))
    }
}

private class WebClearTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        name = "web_clear",
        description = "清空指定 ref 的输入框值。",
        schema = schemaFunction("web_clear", "清空指定 ref 的输入框值。", buildJsonObject { put("ref", schemaString()) }, listOf("ref")),
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        val ref = requiredString(input, "ref") ?: return error("missing_ref", "web_clear requires ref")
        val raw = runtime.eval(AgentBrowser.actionJs(ref, ActionKind.CLEAR))
        runtime.setUiText("[TOOL] web_clear(ref=$ref)")
        runtime.captureFrame()
        runtime.afterToolDelay()
        return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(raw))
    }
}

private class WebFocusTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        name = "web_focus",
        description = "让指定 ref 元素获取焦点。",
        schema = schemaFunction("web_focus", "让指定 ref 元素获取焦点。", buildJsonObject { put("ref", schemaString()) }, listOf("ref")),
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
        val ref = requiredString(input, "ref") ?: return error("missing_ref", "web_focus requires ref")
        val raw = runtime.eval(AgentBrowser.actionJs(ref, ActionKind.FOCUS))
        runtime.setUiText("[TOOL] web_focus(ref=$ref)")
        runtime.captureFrame()
        runtime.afterToolDelay()
        return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(raw))
    }
}

private class WebHoverTool(runtime: WebToolRuntime) :
    BaseWebTool(
        runtime = runtime,
        name = "web_hover",
        description = "触发指定 ref 元素的 hover（mouseenter/mouseover）。",
        schema = schemaFunction("web_hover", "触发指定 ref 元素的 hover（mouseenter/mouseover）。", buildJsonObject { put("ref", schemaString()) }, listOf("ref")),
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
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
        name = "web_scroll_into_view",
        description = "把指定 ref 元素滚动到可见区域。",
        schema = schemaFunction("web_scroll_into_view", "把指定 ref 元素滚动到可见区域。", buildJsonObject { put("ref", schemaString()) }, listOf("ref")),
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
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
        name = "web_query",
        description = "查询指定 ref 的信息（text/html/outerHTML/value/attrs/computed_styles）。",
        schema =
            schemaFunction(
                "web_query",
                "查询指定 ref 的信息（text/html/outerHTML/value/attrs/computed_styles）。",
                buildJsonObject {
                    put("ref", schemaString())
                    put(
                        "kind",
                        buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("enum", JsonArray(listOf("text", "html", "outerHTML", "value", "attrs", "computed_styles").map { JsonPrimitive(it) }))
                        },
                    )
                    put("max_length", schemaInt(default = 4000))
                },
                listOf("ref", "kind"),
            ),
    ) {
    override suspend fun run(input: ToolInput, ctx: ToolContext): ToolOutput {
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
                else -> null
            } ?: return error("invalid_kind", "web_query kind must be one of: text/html/outerHTML/value/attrs/computed_styles")
        val raw = runtime.eval(AgentBrowser.queryJs(ref, queryKind, QueryPayload(limitChars = maxLength)))
        runtime.setUiText("[TOOL] web_query(ref=$ref,kind=$kind)")
        runtime.captureFrame()
        runtime.afterToolDelay()
        return ToolOutput.Json(value = runtime.parseJsonElementFromJsEval(raw))
    }
}

private fun schemaFunction(
    name: String,
    description: String,
    properties: JsonObject,
    required: List<String>,
): JsonObject {
    return buildJsonObject {
        put("type", JsonPrimitive("function"))
        put(
            "function",
            buildJsonObject {
                put("name", JsonPrimitive(name))
                put("description", JsonPrimitive(description))
                put(
                    "parameters",
                    buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("properties", properties)
                        put("required", JsonArray(required.map { JsonPrimitive(it) }))
                    },
                )
            },
        )
    }
}

private fun schemaString(description: String? = null): JsonObject =
    buildJsonObject {
        put("type", JsonPrimitive("string"))
        if (!description.isNullOrBlank()) put("description", JsonPrimitive(description))
    }

private fun schemaBool(
    description: String,
    default: Boolean,
): JsonObject =
    buildJsonObject {
        put("type", JsonPrimitive("boolean"))
        put("description", JsonPrimitive(description))
        put("default", JsonPrimitive(default))
    }

private fun schemaInt(default: Int): JsonObject =
    buildJsonObject {
        put("type", JsonPrimitive("integer"))
        put("default", JsonPrimitive(default))
    }

private fun JsonElement.asString(): String? =
    (this as? JsonPrimitive)?.content

private fun JsonElement.asBoolean(): Boolean? =
    (this as? JsonPrimitive)?.booleanOrNull

private fun JsonElement.asInt(): Int? =
    (this as? JsonPrimitive)?.intOrNull
