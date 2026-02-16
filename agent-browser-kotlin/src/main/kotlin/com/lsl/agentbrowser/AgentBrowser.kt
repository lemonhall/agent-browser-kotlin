package com.lsl.agentbrowser

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

object AgentBrowser {
    internal val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun normalizeRefInput(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("[ref=") && s.endsWith("]") && s.length > 6) {
            s = s.substring(5, s.length - 1).trim()
        }
        if (s.startsWith("@")) s = s.substring(1).trim()
        if (s.length >= 4 && s.substring(0, 4).lowercase() == "ref=") s = s.substring(4).trim()
        return s
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
        val normalizedRef = normalizeRefInput(ref)
        val refEscaped = normalizedRef.replace("\\", "\\\\").replace("'", "\\'")
        return "JSON.stringify(window.__agentBrowser.action('$refEscaped','$kindString',$payloadJson))"
    }

    fun parseSnapshot(json: String): SnapshotResult {
        val normalized = normalizeJsEvalResult(json)
        return AgentBrowser.json.decodeFromString<SnapshotResult>(normalized)
    }

    fun renderSnapshot(snapshotJson: String, options: RenderOptions = RenderOptions()): SnapshotRenderResult {
        val snapshot = parseSnapshot(snapshotJson)
        val refs = snapshot.refs
        val render = when (options.format) {
            OutputFormat.JSON -> {
                val normalized = normalizeJsEvalResult(snapshotJson)
                RenderResult(
                    text = normalized,
                    truncated = snapshot.stats?.truncated == true,
                    truncateReasons = snapshot.stats?.truncateReasons ?: emptyList(),
                    nodesRendered = snapshot.stats?.nodesEmitted ?: 0,
                )
            }
            OutputFormat.PLAIN_TEXT_TREE -> {
                val renderer = SnapshotRenderer(options)
                renderer.render(snapshot)
            }
        }

        val stats = SnapshotRenderStats(
            js = snapshot.stats,
            charsEmitted = render.text.length,
            nodesRendered = render.nodesRendered,
            truncated = render.truncated,
            truncateReasons = render.truncateReasons,
        )

        return SnapshotRenderResult(
            format = options.format,
            text = render.text,
            refs = refs,
            stats = stats,
            snapshot = snapshot,
        )
    }

    fun renderSnapshotText(snapshotJson: String, options: RenderOptions = RenderOptions()): RenderResult {
        val forced = options.copy(format = OutputFormat.PLAIN_TEXT_TREE)
        val snapshot = parseSnapshot(snapshotJson)
        val renderer = SnapshotRenderer(forced)
        return renderer.render(snapshot)
    }

    fun parseAction(json: String): ActionResult {
        val normalized = normalizeJsEvalResult(json)
        return AgentBrowser.json.decodeFromString<ActionResult>(normalized)
    }

    fun queryJs(ref: String, kind: QueryKind, payload: QueryPayload = QueryPayload()): String {
        val payloadJson = json.encodeToString(payload)
        val normalizedRef = normalizeRefInput(ref)
        val refEscaped = normalizedRef.replace("\\", "\\\\").replace("'", "\\'")
        return "JSON.stringify(window.__agentBrowser.query('$refEscaped','${kind.wire}',$payloadJson))"
    }

    fun parseQuery(json: String): QueryResult {
        val normalized = normalizeJsEvalResult(json)
        return AgentBrowser.json.decodeFromString<QueryResult>(normalized)
    }

    fun parseQueryResult(json: String): QueryResult = parseQuery(json)

    fun pageJs(kind: PageKind, payload: PagePayload = PagePayload()): String {
        val payloadJson = json.encodeToString(payload)
        return "JSON.stringify(window.__agentBrowser.page('${kind.wire}',$payloadJson))"
    }

    fun parsePage(json: String): PageResult {
        val normalized = normalizeJsEvalResult(json)
        return AgentBrowser.json.decodeFromString<PageResult>(normalized)
    }

    // --- PRD-V4 6.2 helpers (ergonomic sugar) ---

    fun parseActionResult(json: String): ActionResult = parseAction(json)

    fun queryJs(ref: String, kind: QueryKind, limitChars: Int): String =
        queryJs(ref, kind, QueryPayload(limitChars = limitChars))

    fun scrollJs(direction: String, amount: Int = 300): String {
        val dir = direction.trim().lowercase()
        val dx = when (dir) {
            "left" -> -amount
            "right" -> amount
            else -> 0
        }
        val dy = when (dir) {
            "up" -> -amount
            "down" -> amount
            else -> 0
        }
        return pageJs(PageKind.SCROLL, PagePayload(deltaX = dx, deltaY = dy))
    }

    fun pressKeyJs(key: String): String =
        pageJs(PageKind.PRESS_KEY, PagePayload(key = key))

    fun keyDownJs(key: String): String =
        pageJs(PageKind.KEY_DOWN, PagePayload(key = key))

    fun keyUpJs(key: String): String =
        pageJs(PageKind.KEY_UP, PagePayload(key = key))

    fun charJs(text: String): String =
        pageJs(PageKind.CHAR, PagePayload(text = text))

    fun getUrlJs(): String =
        "JSON.stringify((window.__agentBrowser && window.__agentBrowser.page && window.__agentBrowser.page.getUrl) ? window.__agentBrowser.page.getUrl() : (location && location.href ? location.href : ''))"

    fun getTitleJs(): String =
        "JSON.stringify((window.__agentBrowser && window.__agentBrowser.page && window.__agentBrowser.page.getTitle) ? window.__agentBrowser.page.getTitle() : (document && document.title ? document.title : ''))"
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
        val jsTruncated = snapshot.stats?.truncated == true
        val jsReasons = snapshot.stats?.truncateReasons ?: emptyList()
        val finalReasonsSet = linkedSetOf<String>().also {
            it.addAll(reasons)
            it.addAll(jsReasons)
        }
        var finalTruncated = truncated || jsTruncated
        val header = fitHeader(
            url = url,
            title = title,
            nodes = nodesRendered,
            truncated = finalTruncated,
            truncateReasons = finalReasonsSet.toList(),
        )

        val headerLineEnd = finalText.indexOf('\n')
        val body = if (headerLineEnd >= 0) finalText.substring(headerLineEnd + 1) else ""
        fun rebuildWithHeader(h: String): String = if (body.isNotEmpty()) "$h\n$body" else h

        var rebuilt = rebuildWithHeader(header)
        if (rebuilt.length > options.maxCharsTotal) {
            finalTruncated = true
            finalReasonsSet.add("maxCharsTotal")
            val header2 = fitHeader(
                url = url,
                title = title,
                nodes = nodesRendered,
                truncated = finalTruncated,
                truncateReasons = finalReasonsSet.toList(),
            )
            rebuilt = rebuildWithHeader(header2)
        }

        val bounded = if (rebuilt.length <= options.maxCharsTotal) rebuilt else rebuilt.take(options.maxCharsTotal)

        return RenderResult(
            text = bounded,
            truncated = finalTruncated,
            truncateReasons = finalReasonsSet.toList(),
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
