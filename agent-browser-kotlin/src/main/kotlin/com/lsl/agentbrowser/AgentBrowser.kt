package com.lsl.agentbrowser

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

object AgentBrowser {
    internal val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun getScript(): String {
        val stream = AgentBrowser::class.java.classLoader.getResourceAsStream("agent-browser.js")
            ?: error("Missing resource: agent-browser.js")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun normalizeJsEvalResult(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith('"') || !trimmed.endsWith('"')) return trimmed
        return try {
            json.decodeFromString<String>(trimmed)
        } catch (_: Throwable) {
            trimmed
        }
    }

    fun snapshotJs(options: SnapshotJsOptions = SnapshotJsOptions()): String {
        val optionsJson = json.encodeToString(options)
        return "JSON.stringify(window.__agentBrowser.snapshot($optionsJson))"
    }

    fun actionJs(ref: String, kind: ActionKind, payload: ActionPayload? = null): String {
        val kindString = when (kind) {
            ActionKind.CLICK -> "click"
            ActionKind.FILL -> "fill"
            ActionKind.SELECT -> "select"
            ActionKind.CHECK -> "check"
            ActionKind.UNCHECK -> "uncheck"
            ActionKind.FOCUS -> "focus"
            ActionKind.HOVER -> "hover"
            ActionKind.SCROLL_INTO_VIEW -> "scroll_into_view"
            ActionKind.CLEAR -> "clear"
        }

        val payloadJson = when (payload) {
            null -> "{}"
            is FillPayload -> json.encodeToString(payload)
            is SelectPayload -> json.encodeToString(payload)
        }
        val refEscaped = ref.replace("\\", "\\\\").replace("'", "\\'")
        return "JSON.stringify(window.__agentBrowser.action('$refEscaped','$kindString',$payloadJson))"
    }

    fun parseSnapshot(json: String): SnapshotResult {
        val normalized = normalizeJsEvalResult(json)
        return AgentBrowser.json.decodeFromString<SnapshotResult>(normalized)
    }

    fun renderSnapshot(snapshotJson: String, options: RenderOptions = RenderOptions()): RenderResult {
        val snapshot = parseSnapshot(snapshotJson)
        val renderer = SnapshotRenderer(options)
        return renderer.render(snapshot)
    }

    fun parseAction(json: String): ActionResult {
        val normalized = normalizeJsEvalResult(json)
        return AgentBrowser.json.decodeFromString<ActionResult>(normalized)
    }

    fun queryJs(ref: String, kind: QueryKind, payload: QueryPayload = QueryPayload()): String {
        val payloadJson = json.encodeToString(payload)
        val refEscaped = ref.replace("\\", "\\\\").replace("'", "\\'")
        return "JSON.stringify(window.__agentBrowser.query('$refEscaped','${kind.wire}',$payloadJson))"
    }

    fun parseQuery(json: String): QueryResult {
        val normalized = normalizeJsEvalResult(json)
        return AgentBrowser.json.decodeFromString<QueryResult>(normalized)
    }
}

private class SnapshotRenderer(
    private val options: RenderOptions,
) {
    private val reasons = linkedSetOf<String>()
    private val out = StringBuilder()
    private var truncated = false
    private var nodesRendered = 0

    fun render(snapshot: SnapshotResult): RenderResult {
        val url = snapshot.meta?.url
        val title = snapshot.meta?.title

        appendLine(
            fitHeader(
                url = url,
                title = title,
                nodes = 0,
                truncated = false,
                truncateReasons = emptyList(),
            ),
        )

        val tree = snapshot.tree
        if (tree != null) {
            renderNode(tree, depth = 0, indent = "")
        }

        val finalText = out.toString().trimEnd()
        val finalReasons = reasons.toList()
        val header = fitHeader(
            url = url,
            title = title,
            nodes = nodesRendered,
            truncated = truncated,
            truncateReasons = finalReasons,
        )

        val headerLineEnd = finalText.indexOf('\n')
        val rebuilt = if (headerLineEnd >= 0) {
            header + "\n" + finalText.substring(headerLineEnd + 1)
        } else {
            header
        }

        val bounded = if (rebuilt.length <= options.maxCharsTotal) rebuilt else rebuilt.take(options.maxCharsTotal).also {
            truncated = true
            reasons.add("maxCharsTotal")
        }

        return RenderResult(
            text = bounded,
            truncated = truncated,
            truncateReasons = reasons.toList(),
            nodesRendered = nodesRendered,
        )
    }

    private fun renderNode(node: SnapshotNode, depth: Int, indent: String): Boolean {
        if (truncated) return false
        if (depth > options.maxDepth) {
            truncated = true
            reasons.add("maxDepth")
            return false
        }
        if (nodesRendered >= options.maxNodes) {
            truncated = true
            reasons.add("maxNodes")
            return false
        }

        val hasChildren = node.children.isNotEmpty()
        val roleOrTag = (node.role ?: node.tag ?: "node").lowercase()
        val ref = node.ref
        val displayText = (node.name ?: node.text)?.trim()?.takeIf { it.isNotEmpty() }

        val isLeafLike = !hasChildren
        val isPrintableLeaf = ref != null || displayText != null
        val structuralLabel = "$indent- $roleOrTag:"

        val before = out.length
        var printed = false

        if (!options.compact || hasChildren) {
            // Tentatively print structural nodes; roll back later if compact decides it was empty.
            if (hasChildren) {
                if (appendLine(structuralLabel)) {
                    nodesRendered++
                    printed = true
                }
            } else if (isPrintableLeaf) {
                val leafLine = buildLeafLine(indent, roleOrTag, displayText, ref, node.attrs)
                if (appendLine(leafLine)) {
                    nodesRendered++
                    printed = true
                }
            }
        } else if (isLeafLike && isPrintableLeaf) {
            val leafLine = buildLeafLine(indent, roleOrTag, displayText, ref, node.attrs)
            if (appendLine(leafLine)) {
                nodesRendered++
                printed = true
            }
        }

        val childIndent = "$indent  "
        var anyChildPrinted = false
        for (child in node.children) {
            val childPrinted = renderNode(child, depth + 1, childIndent)
            anyChildPrinted = anyChildPrinted || childPrinted
            if (truncated) break
        }

        if (options.compact && hasChildren) {
            val selfHasContent = ref != null || displayText != null
            val shouldKeepStructural = selfHasContent || anyChildPrinted
            if (!shouldKeepStructural) {
                out.setLength(before)
                if (printed) nodesRendered--
                printed = false
            }
        }

        return printed || anyChildPrinted
    }

    private fun buildLeafLine(
        indent: String,
        roleOrTag: String,
        displayText: String?,
        ref: String?,
        attrs: Map<String, String>,
    ): String {
        val textPart = displayText?.let { " \"${sanitizeQuotes(it)}\"" } ?: ""
        val attrsPart = buildAttrsPart(attrs)
        val refPart = ref?.let { " [ref=$it]" } ?: ""
        return "$indent- $roleOrTag$textPart$attrsPart$refPart"
    }

    private fun sanitizeQuotes(text: String): String = text.replace("\"", "\\\"")

    private fun buildAttrsPart(attrs: Map<String, String>): String {
        if (attrs.isEmpty()) return ""
        val keys = listOf("href", "type", "placeholder", "value", "name", "aria-label")
        val parts = keys.mapNotNull { key ->
            val value = attrs[key]?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val shortened = shorten(value, 24)
            "$key=\"${sanitizeQuotes(shortened)}\""
        }
        if (parts.isEmpty()) return ""
        return " (" + parts.joinToString(separator = " ") + ")"
    }

    private fun appendLine(line: String): Boolean {
        if (truncated) return false
        if (out.isNotEmpty()) {
            if (out.length + 1 >= options.maxCharsTotal) {
                truncated = true
                reasons.add("maxCharsTotal")
                return false
            }
            out.append('\n')
        }

        val remaining = options.maxCharsTotal - out.length
        if (line.length <= remaining) {
            out.append(line)
            return true
        }

        // Partial append to maximize useful output while staying within the budget.
        out.append(line.take(remaining.coerceAtLeast(0)))
        truncated = true
        reasons.add("maxCharsTotal")
        return false
    }

    private fun fitHeader(
        url: String?,
        title: String?,
        nodes: Int,
        truncated: Boolean,
        truncateReasons: List<String>,
    ): String {
        val reasonsPart = if (truncateReasons.isEmpty()) "[]" else truncateReasons.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        val urlPart = url?.let { " url=${shorten(it, 32)}" } ?: ""
        val titlePart = title?.let { " title=\"${shorten(it, 24)}\"" } ?: ""
        val line = "[snapshot]$urlPart$titlePart nodes=$nodes truncated=$truncated truncateReasons=$reasonsPart"
        return truncateToBudget(line)
    }

    private fun shorten(value: String, maxLen: Int): String {
        val normalized = value.trim().replace(Regex("\\s+"), " ")
        if (normalized.length <= maxLen) return normalized
        if (maxLen <= 1) return normalized.take(maxLen)
        return normalized.take(maxLen - 1) + "…"
    }

    private fun truncateToBudget(line: String): String {
        if (line.length <= options.maxCharsTotal) return line
        truncated = true
        reasons.add("maxCharsTotal")
        return line.take(options.maxCharsTotal.coerceAtLeast(0))
    }
}
