package com.lsl.agentbrowser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JsError(
    val code: String,
    val message: String? = null,
)

@Serializable
data class Meta(
    val url: String? = null,
    val title: String? = null,
    val ts: Long? = null,
)

@Serializable
data class SnapshotStats(
    val nodesVisited: Int? = null,
    val nodesEmitted: Int? = null,
    @SerialName("visitedNodes")
    val visitedNodes: Int? = null,
    @SerialName("emittedNodes")
    val emittedNodes: Int? = null,
    val domNodes: Int? = null,
    val skippedHidden: Int? = null,
    val truncated: Boolean = false,
    val truncateReasons: List<String> = emptyList(),
    val jsTimeMs: Long? = null,
)

@Serializable
data class SnapshotRef(
    val ref: String,
    val tag: String? = null,
    val role: String? = null,
    val name: String? = null,
    val attrs: Map<String, String> = emptyMap(),
    val interactive: Boolean? = null,
    val cursorInteractive: Boolean? = null,
    val level: Int? = null,
    val path: String? = null,
)

@Serializable
data class SnapshotNode(
    val ref: String? = null,
    val tag: String? = null,
    val role: String? = null,
    val name: String? = null,
    val text: String? = null,
    val level: Int? = null,
    val attrs: Map<String, String> = emptyMap(),
    val interactive: Boolean? = null,
    val cursorInteractive: Boolean? = null,
    val children: List<SnapshotNode> = emptyList(),
)

@Serializable
data class SnapshotResult(
    val ok: Boolean,
    val type: String,
    val meta: Meta? = null,
    val stats: SnapshotStats? = null,
    val refs: Map<String, SnapshotRef> = emptyMap(),
    val tree: SnapshotNode? = null,
    val error: JsError? = null,
)

@Serializable
data class SnapshotJsOptions(
    val maxNodes: Int = 500,
    val maxTextPerNode: Int = 200,
    val maxAttrValueLen: Int = 150,
    val interactiveOnly: Boolean = true,
    val cursorInteractive: Boolean = false,
    val scope: String? = null,
)

enum class ActionKind {
    CLICK,
    FILL,
    SELECT,
    CHECK,
    UNCHECK,
    FOCUS,
    HOVER,
    SCROLL_INTO_VIEW,
    CLEAR,
}

@Serializable
sealed interface ActionPayload

@Serializable
@SerialName("fill")
data class FillPayload(
    val value: String,
) : ActionPayload

@Serializable
@SerialName("select")
data class SelectPayload(
    val values: List<String>,
) : ActionPayload

@Serializable
data class ActionResult(
    val ok: Boolean,
    val type: String,
    val ref: String? = null,
    val action: String? = null,
    val meta: Meta? = null,
    val error: JsError? = null,
)

enum class QueryKind(val wire: String) {
    TEXT("text"),
    ATTRS("attrs"),
    VALUE("value"),
    HTML("html"),
    OUTER_HTML("outerHTML"),
    COMPUTED_STYLES("computed_styles"),
    IS_VISIBLE("isvisible"),
    IS_ENABLED("isenabled"),
    IS_CHECKED("ischecked"),
}

@Serializable
data class QueryPayload(
    val limitChars: Int = 4000,
)

@Serializable
data class QueryResult(
    val ok: Boolean,
    val type: String,
    val ref: String? = null,
    val kind: String? = null,
    val value: String? = null,
    val truncated: Boolean? = null,
    val meta: Meta? = null,
    val error: JsError? = null,
)

enum class PageKind(val wire: String) {
    INFO("info"),
    SCROLL("scroll"),
    PRESS_KEY("pressKey"),
    KEY_DOWN("keyDown"),
    KEY_UP("keyUp"),
    CHAR("char"),
}

@Serializable
data class PagePayload(
    val x: Int? = null,
    val y: Int? = null,
    val deltaX: Int? = null,
    val deltaY: Int? = null,
    val behavior: String? = null,
    val key: String? = null,
    val text: String? = null,
)

@Serializable
data class PageViewport(
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class PageResult(
    val ok: Boolean,
    val type: String,
    val kind: String? = null,
    val url: String? = null,
    val title: String? = null,
    val scrollX: Int? = null,
    val scrollY: Int? = null,
    val viewport: PageViewport? = null,
    val key: String? = null,
    val text: String? = null,
    val meta: Meta? = null,
    val error: JsError? = null,
)

data class RenderOptions(
    val maxCharsTotal: Int = 12_000,
    val maxNodes: Int = 200,
    val maxDepth: Int = 12,
    val compact: Boolean = true,
    val format: OutputFormat = OutputFormat.PLAIN_TEXT_TREE,
)

enum class OutputFormat {
    PLAIN_TEXT_TREE,
    JSON,
}

data class RenderResult(
    val text: String,
    val truncated: Boolean,
    val truncateReasons: List<String>,
    val nodesRendered: Int,
)

data class SnapshotRenderStats(
    val js: SnapshotStats? = null,
    val charsEmitted: Int,
    val nodesRendered: Int,
    val truncated: Boolean,
    val truncateReasons: List<String>,
)

data class SnapshotRenderResult(
    val format: OutputFormat,
    val text: String,
    val refs: Map<String, SnapshotRef>,
    val stats: SnapshotRenderStats,
    val snapshot: SnapshotResult,
)
