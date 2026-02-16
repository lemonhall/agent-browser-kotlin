package com.lsl.agentbrowser.openai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Single source of truth for the OpenAI tool schema used by the WebView agent tools.
 *
 * This schema must stay in sync with:
 * - docs/tools/web-tools.openai.json
 * - the Android instrumentation tool executor (OpenAgenticWebTools)
 */
object WebToolsOpenAiSchema {
    data class ToolSpec(
        val name: String,
        val description: String,
        val openAiSchema: JsonObject,
    )

    val WEB_OPEN: ToolSpec =
        ToolSpec(
            name = "web_open",
            description = "打开/跳转到指定 URL（navigate）。",
            openAiSchema =
                schemaFunction(
                    name = "web_open",
                    description = "打开/跳转到指定 URL（navigate）。",
                    properties =
                        buildJsonObject {
                            put("url", schemaString())
                        },
                    required = listOf("url"),
                ),
        )

    val WEB_BACK: ToolSpec =
        ToolSpec(
            name = "web_back",
            description = "后退（history.back）。",
            openAiSchema = schemaFunction("web_back", "后退（history.back）。", buildJsonObject { }, emptyList()),
        )

    val WEB_FORWARD: ToolSpec =
        ToolSpec(
            name = "web_forward",
            description = "前进（history.forward）。",
            openAiSchema = schemaFunction("web_forward", "前进（history.forward）。", buildJsonObject { }, emptyList()),
        )

    val WEB_RELOAD: ToolSpec =
        ToolSpec(
            name = "web_reload",
            description = "刷新（location.reload）。",
            openAiSchema = schemaFunction("web_reload", "刷新（location.reload）。", buildJsonObject { }, emptyList()),
        )

    val WEB_SNAPSHOT: ToolSpec =
        ToolSpec(
            name = "web_snapshot",
            description = "获取当前页面的元素快照。返回带 [ref=eN] 标注的元素树；后续操作使用 ref 指定目标。",
            openAiSchema =
                schemaFunction(
                    name = "web_snapshot",
                    description = "获取当前页面的元素快照。返回带 [ref=eN] 标注的元素树；后续操作使用 ref 指定目标。",
                    properties =
                        buildJsonObject {
                            put(
                                "interactive_only",
                                schemaBool(
                                    description = "true=只返回可交互元素；false=同时返回标题/列表项/图片等内容元素。",
                                    default = true,
                                ),
                            )
                            put(
                                "cursor_interactive",
                                schemaBool(
                                    description = "true=额外把 cursor:pointer/onclick/tabindex 等“非标准交互元素”纳入 refs（对齐 PRD-V4 cursorInteractive gate）。",
                                    default = false,
                                ),
                            )
                            put(
                                "scope",
                                schemaString(description = "可选：CSS selector，将 snapshot 限定在该元素子树内（默认 document.body）。"),
                            )
                        },
                    required = emptyList(),
                ),
        )

    val WEB_CLICK: ToolSpec =
        ToolSpec(
            name = "web_click",
            description = "点击指定 ref 的元素。",
            openAiSchema =
                schemaFunction(
                    name = "web_click",
                    description = "点击指定 ref 的元素。",
                    properties = buildJsonObject { put("ref", schemaString()) },
                    required = listOf("ref"),
                ),
        )

    val WEB_DBLCLICK: ToolSpec =
        ToolSpec(
            name = "web_dblclick",
            description = "双击指定 ref 的元素。",
            openAiSchema =
                schemaFunction(
                    name = "web_dblclick",
                    description = "双击指定 ref 的元素。",
                    properties = buildJsonObject { put("ref", schemaString()) },
                    required = listOf("ref"),
                ),
        )

    val WEB_FILL: ToolSpec =
        ToolSpec(
            name = "web_fill",
            description = "在指定 ref 的输入框中填入文本（会先清空）。",
            openAiSchema =
                schemaFunction(
                    name = "web_fill",
                    description = "在指定 ref 的输入框中填入文本（会先清空）。",
                    properties =
                        buildJsonObject {
                            put("ref", schemaString())
                            put("value", schemaString())
                        },
                    required = listOf("ref", "value"),
                ),
        )

    val WEB_TYPE: ToolSpec =
        ToolSpec(
            name = "web_type",
            description = "在指定 ref 的输入框中逐字符输入文本（更接近真实键入）。",
            openAiSchema =
                schemaFunction(
                    name = "web_type",
                    description = "在指定 ref 的输入框中逐字符输入文本（更接近真实键入）。",
                    properties =
                        buildJsonObject {
                            put("ref", schemaString())
                            put("text", schemaString())
                        },
                    required = listOf("ref", "text"),
                ),
        )

    val WEB_SELECT: ToolSpec =
        ToolSpec(
            name = "web_select",
            description = "在指定 ref 的下拉框中选择选项（按 option.value 或显示文本匹配）。",
            openAiSchema =
                schemaFunction(
                    name = "web_select",
                    description = "在指定 ref 的下拉框中选择选项（按 option.value 或显示文本匹配）。",
                    properties =
                        buildJsonObject {
                            put("ref", schemaString())
                            put("values", schemaArrayString())
                        },
                    required = listOf("ref", "values"),
                ),
        )

    val WEB_CHECK: ToolSpec =
        ToolSpec(
            name = "web_check",
            description = "勾选指定 ref 的 checkbox 或 radio。",
            openAiSchema =
                schemaFunction(
                    name = "web_check",
                    description = "勾选指定 ref 的 checkbox 或 radio。",
                    properties = buildJsonObject { put("ref", schemaString()) },
                    required = listOf("ref"),
                ),
        )

    val WEB_UNCHECK: ToolSpec =
        ToolSpec(
            name = "web_uncheck",
            description = "取消勾选指定 ref 的 checkbox。",
            openAiSchema =
                schemaFunction(
                    name = "web_uncheck",
                    description = "取消勾选指定 ref 的 checkbox。",
                    properties = buildJsonObject { put("ref", schemaString()) },
                    required = listOf("ref"),
                ),
        )

    val WEB_SCROLL: ToolSpec =
        ToolSpec(
            name = "web_scroll",
            description = "页面滚动。direction: up/down/left/right。",
            openAiSchema =
                schemaFunction(
                    name = "web_scroll",
                    description = "页面滚动。direction: up/down/left/right。",
                    properties =
                        buildJsonObject {
                            put("direction", schemaEnumStrings(listOf("up", "down", "left", "right")))
                            put("amount", schemaInt(default = 300))
                        },
                    required = listOf("direction"),
                ),
        )

    val WEB_PRESS_KEY: ToolSpec =
        ToolSpec(
            name = "web_press_key",
            description = "在当前焦点元素上按键（如 Enter/Tab/Escape/Backspace/ArrowDown）。",
            openAiSchema =
                schemaFunction(
                    name = "web_press_key",
                    description = "在当前焦点元素上按键（如 Enter/Tab/Escape/Backspace/ArrowDown）。",
                    properties = buildJsonObject { put("key", schemaString()) },
                    required = listOf("key"),
                ),
        )

    val WEB_HOVER: ToolSpec =
        ToolSpec(
            name = "web_hover",
            description = "触发指定 ref 元素的 hover（mouseenter/mouseover）。",
            openAiSchema =
                schemaFunction(
                    name = "web_hover",
                    description = "触发指定 ref 元素的 hover（mouseenter/mouseover）。",
                    properties = buildJsonObject { put("ref", schemaString()) },
                    required = listOf("ref"),
                ),
        )

    val WEB_SCROLL_INTO_VIEW: ToolSpec =
        ToolSpec(
            name = "web_scroll_into_view",
            description = "把指定 ref 元素滚动到可见区域。",
            openAiSchema =
                schemaFunction(
                    name = "web_scroll_into_view",
                    description = "把指定 ref 元素滚动到可见区域。",
                    properties = buildJsonObject { put("ref", schemaString()) },
                    required = listOf("ref"),
                ),
        )

    val WEB_WAIT: ToolSpec =
        ToolSpec(
            name = "web_wait",
            description = "等待页面条件（selector/text/url 或纯等待 ms）。只需提供其中一种条件。",
            openAiSchema =
                schemaFunction(
                    name = "web_wait",
                    description = "等待页面条件（selector/text/url 或纯等待 ms）。只需提供其中一种条件。",
                    properties =
                        buildJsonObject {
                            put("selector", schemaString(description = "等待该 CSS selector 出现（可选）。"))
                            put("text", schemaString(description = "等待页面文本包含该子串（可选）。"))
                            put("url", schemaString(description = "等待 location.href 包含该子串（可选）。"))
                            put("ms", schemaInt(description = "纯等待时间（毫秒，优先级最高）。", default = 0))
                            put("timeout_ms", schemaInt(description = "总超时时间（毫秒）。", default = 5000))
                            put("poll_ms", schemaInt(description = "轮询间隔（毫秒）。", default = 100))
                        },
                    required = emptyList(),
                ),
        )

    val WEB_QUERY: ToolSpec =
        ToolSpec(
            name = "web_query",
            description = "查询指定 ref 的信息（text/html/outerHTML/value/attrs/computed_styles/isvisible/isenabled/ischecked）。",
            openAiSchema =
                schemaFunction(
                    name = "web_query",
                    description = "查询指定 ref 的信息（text/html/outerHTML/value/attrs/computed_styles/isvisible/isenabled/ischecked）。",
                    properties =
                        buildJsonObject {
                            put("ref", schemaString())
                            put("kind", schemaEnumStrings(listOf("text", "html", "outerHTML", "value", "attrs", "computed_styles", "isvisible", "isenabled", "ischecked")))
                            put("max_length", schemaInt(default = 4000))
                        },
                    required = listOf("ref", "kind"),
                ),
        )

    val WEB_SCREENSHOT: ToolSpec =
        ToolSpec(
            name = "web_screenshot",
            description = "截取当前屏幕截图（真机证据用）。",
            openAiSchema =
                schemaFunction(
                    name = "web_screenshot",
                    description = "截取当前屏幕截图（真机证据用）。",
                    properties =
                        buildJsonObject {
                            put("label", schemaString(description = "可选标签，用于人类查看。"))
                        },
                    required = emptyList(),
                ),
        )

    val WEB_EVAL: ToolSpec =
        ToolSpec(
            name = "web_eval",
            description = "执行一段 JavaScript（默认禁用，仅调试时开启）。",
            openAiSchema =
                schemaFunction(
                    name = "web_eval",
                    description = "执行一段 JavaScript（默认禁用，仅调试时开启）。",
                    properties =
                        buildJsonObject {
                            put("js", schemaString())
                            put("max_length", schemaInt(default = 2000))
                        },
                    required = listOf("js"),
                ),
        )

    val WEB_CLOSE: ToolSpec =
        ToolSpec(
            name = "web_close",
            description = "关闭当前页面（WebView 场景会跳到 about:blank）。",
            openAiSchema = schemaFunction("web_close", "关闭当前页面（WebView 场景会跳到 about:blank）。", buildJsonObject { }, emptyList()),
        )

    val ALL: List<ToolSpec> =
        listOf(
            WEB_OPEN,
            WEB_BACK,
            WEB_FORWARD,
            WEB_RELOAD,
            WEB_SNAPSHOT,
            WEB_CLICK,
            WEB_DBLCLICK,
            WEB_FILL,
            WEB_TYPE,
            WEB_SELECT,
            WEB_CHECK,
            WEB_UNCHECK,
            WEB_SCROLL,
            WEB_PRESS_KEY,
            WEB_HOVER,
            WEB_SCROLL_INTO_VIEW,
            WEB_WAIT,
            WEB_QUERY,
            WEB_SCREENSHOT,
            WEB_EVAL,
            WEB_CLOSE,
        )

    private val byName: Map<String, ToolSpec> = ALL.associateBy { it.name }

    fun spec(name: String): ToolSpec =
        byName[name] ?: error("unknown tool: $name")

    fun toolsJsonArray(): JsonArray =
        JsonArray(ALL.map { it.openAiSchema })
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

private fun schemaArrayString(): JsonObject =
    buildJsonObject {
        put("type", JsonPrimitive("array"))
        put("items", buildJsonObject { put("type", JsonPrimitive("string")) })
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

private fun schemaInt(
    description: String,
    default: Int,
): JsonObject =
    buildJsonObject {
        put("type", JsonPrimitive("integer"))
        put("description", JsonPrimitive(description))
        put("default", JsonPrimitive(default))
    }

private fun schemaEnumStrings(values: List<String>): JsonObject =
    buildJsonObject {
        put("type", JsonPrimitive("string"))
        put("enum", JsonArray(values.map { JsonPrimitive(it) }))
    }
