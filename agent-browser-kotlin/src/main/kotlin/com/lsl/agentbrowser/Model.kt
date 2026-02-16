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
    val path: String? = null,
)

@Serializable
data class SnapshotNode(
    val ref: String? = null,
    val tag: String? = null,
    val role: String? = null,
    val name: String? = null,
    val text: String? = null,
    val attrs: Map<String, String> = emptyMap(),
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

data class RenderOptions(
    val maxCharsTotal: Int = 12_000,
    val maxNodes: Int = 200,
    val maxDepth: Int = 12,
    val compact: Boolean = true,
)

data class RenderResult(
    val text: String,
    val truncated: Boolean,
    val truncateReasons: List<String>,
    val nodesRendered: Int,
)
