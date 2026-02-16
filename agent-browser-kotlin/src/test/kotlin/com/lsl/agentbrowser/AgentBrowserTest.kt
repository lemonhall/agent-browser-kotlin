package com.lsl.agentbrowser

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentBrowserTest {
    @Test
    fun getScript_containsNamespace() {
        val script = AgentBrowser.getScript()
        assertContains(script, "window.__agentBrowser")
    }

    @Test
    fun snapshotJs_encodesOptionsIntoExecutableExpression() {
        val js = AgentBrowser.snapshotJs(
            SnapshotJsOptions(
                maxNodes = 123,
                interactiveOnly = false,
                cursorInteractive = true,
                scope = "#main",
            ),
        )
        assertContains(js, "window.__agentBrowser.snapshot(")
        assertContains(js, "\"maxNodes\":123")
        assertContains(js, "\"interactiveOnly\":false")
        assertContains(js, "\"cursorInteractive\":true")
        assertContains(js, "\"scope\":\"#main\"")
        assertTrue(js.startsWith("JSON.stringify("))
        assertTrue(js.endsWith("))"))
    }

    @Test
    fun normalizeJsEvalResult_unquotesEvaluateJavascriptStringResult() {
        val raw = "\"{\\\"ok\\\":true,\\\"type\\\":\\\"snapshot\\\"}\""
        assertEquals("{\"ok\":true,\"type\":\"snapshot\"}", AgentBrowser.normalizeJsEvalResult(raw))
    }

    @Test
    fun parseSnapshot_acceptsMinimalSchema_andIgnoresUnknownKeys() {
        val snapshotJson =
            """
            {
              "ok": true,
              "type": "snapshot",
              "meta": { "url": "https://example.com", "title": "Example", "ts": 1 },
              "stats": { "nodesVisited": 3, "nodesEmitted": 2, "truncated": false, "truncateReasons": [] },
              "refs": {
                "e1": { "ref":"e1", "tag":"button", "role":"button", "name":"Search", "attrs":{}, "unknownRefField": 123 }
              },
              "tree": {
                "tag":"body",
                "role":"document",
                "unknownNodeField": "x",
                "children": [
                  { "ref":"e1", "tag":"button", "role":"button", "name":"Search", "children":[] }
                ]
              },
              "unknownTopField": { "a": 1 }
            }
            """.trimIndent()

        val result = AgentBrowser.parseSnapshot(snapshotJson)
        assertTrue(result.ok)
        assertEquals("snapshot", result.type)
        assertEquals("https://example.com", result.meta?.url)
        assertEquals("Example", result.meta?.title)
        assertEquals("button", result.refs["e1"]?.tag)
        assertEquals("Search", result.refs["e1"]?.name)
        assertEquals("e1", result.tree?.children?.firstOrNull()?.ref)
    }

    @Test
    fun renderSnapshot_respectsMaxCharsTotal() {
        val snapshotJson =
            """
            {
              "ok": true,
              "type": "snapshot",
              "meta": { "url": "https://example.com", "title": "Example", "ts": 1 },
              "stats": { "nodesVisited": 3, "nodesEmitted": 3, "truncated": false, "truncateReasons": [] },
              "refs": {
                "e1": { "ref":"e1", "tag":"button", "role":"button", "name":"Search", "attrs":{} }
              },
              "tree": {
                "tag":"body",
                "role":"document",
                "children": [
                  { "tag":"main", "role":"main", "children": [
                    { "ref":"e1", "tag":"button", "role":"button", "name":"Search", "text":"Search", "children":[] }
                  ]}
                ]
              }
            }
            """.trimIndent()

        val result = AgentBrowser.renderSnapshot(snapshotJson, RenderOptions(maxCharsTotal = 60, maxNodes = 200, maxDepth = 12, compact = true))
        assertTrue(result.text.length <= 60)
        assertTrue(result.truncated)
        assertTrue(result.truncateReasons.isNotEmpty())
    }

    @Test
    fun parseAction_successAndRefNotFound_areBothStructured() {
        val okJson =
            """
            { "ok": true, "type": "action", "ref": "e1", "action": "click", "meta": { "ts": 1 } }
            """.trimIndent()
        val ok = AgentBrowser.parseAction(okJson)
        assertTrue(ok.ok)
        assertEquals("e1", ok.ref)
        assertEquals("click", ok.action)

        val notFoundJson =
            """
            {
              "ok": false,
              "type": "action",
              "ref": "e404",
              "action": "click",
              "meta": { "ts": 1 },
              "error": { "code": "ref_not_found", "message": "ref e404 not found" }
            }
            """.trimIndent()
        val notFound = AgentBrowser.parseAction(notFoundJson)
        assertTrue(!notFound.ok)
        assertEquals("ref_not_found", notFound.error?.code)
    }

}
