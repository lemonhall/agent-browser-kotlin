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
                maxTextPerNode = 77,
                maxAttrValueLen = 88,
                interactiveOnly = false,
                cursorInteractive = true,
                scope = "#main",
            ),
        )
        assertContains(js, "window.__agentBrowser.snapshot(")
        assertContains(js, "\"maxNodes\":123")
        assertContains(js, "\"maxTextPerNode\":77")
        assertContains(js, "\"maxAttrValueLen\":88")
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

        val result = AgentBrowser.renderSnapshot(
            snapshotJson,
            RenderOptions(maxCharsTotal = 60, maxNodes = 200, maxDepth = 12, compact = true, format = OutputFormat.PLAIN_TEXT_TREE),
        )
        assertTrue(result.text.length <= 60)
        assertTrue(result.stats.truncated)
        assertTrue(result.stats.truncateReasons.isNotEmpty())
    }

    @Test
    fun renderSnapshot_includesKeyAttrs_forRefLeaves() {
        val snapshotJson =
            """
            {
              "ok": true,
              "type": "snapshot",
              "meta": { "url": "https://example.com", "title": "Example", "ts": 1 },
              "stats": { "nodesVisited": 3, "nodesEmitted": 3, "truncated": false, "truncateReasons": [] },
              "refs": {
                "e1": { "ref":"e1", "tag":"a", "role":"link", "name":"Docs", "attrs": { "href": "/docs" } },
                "e2": { "ref":"e2", "tag":"input", "role":"textbox", "name":"Search Input", "attrs": { "type":"text", "placeholder":"Search", "value":"hello" } }
              },
              "tree": {
                "tag":"body",
                "role":"document",
                "children": [
                  { "ref":"e1", "tag":"a", "role":"link", "name":"Docs", "attrs": { "href": "/docs" }, "children":[] },
                  { "ref":"e2", "tag":"input", "role":"textbox", "name":"Search Input", "attrs": { "type":"text", "placeholder":"Search", "value":"hello" }, "children":[] }
                ]
              }
            }
            """.trimIndent()

        val rendered = AgentBrowser.renderSnapshot(
            snapshotJson,
            RenderOptions(maxCharsTotal = 4000, maxNodes = 200, maxDepth = 12, compact = true, format = OutputFormat.PLAIN_TEXT_TREE),
        )
        assertContains(rendered.text, "href=\"/docs\"")
        assertContains(rendered.text, "placeholder=\"Search\"")
        assertContains(rendered.text, "value=\"hello\"")
    }

    @Test
    fun renderSnapshot_formatJson_returnsStructuredResult_andEmptyText() {
        val snapshotJson =
            """
            {
              "ok": true,
              "type": "snapshot",
              "meta": { "url": "https://example.com", "title": "Example", "ts": 1 },
              "stats": { "nodesVisited": 3, "nodesEmitted": 2, "truncated": false, "truncateReasons": [] },
              "refs": {
                "e1": { "ref":"e1", "tag":"button", "role":"button", "name":"Search", "attrs":{} }
              },
              "tree": { "tag":"body", "role":"document", "children": [] }
            }
            """.trimIndent()

        val rendered = AgentBrowser.renderSnapshot(snapshotJson, RenderOptions(format = OutputFormat.JSON))
        assertEquals(OutputFormat.JSON, rendered.format)
        assertEquals("", rendered.text)
        assertTrue(rendered.snapshot.ok)
        assertEquals("Search", rendered.refs["e1"]?.name)
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

    @Test
    fun actionJs_supportsSelectPayload() {
        val js = AgentBrowser.actionJs("e1", ActionKind.SELECT, SelectPayload(values = listOf("beta")))
        assertContains(js, "window.__agentBrowser.action(")
        assertContains(js, "'select'")
        assertContains(js, "\"values\":[\"beta\"]")
    }

    @Test
    fun actionJs_supportsClearAndCheckKinds() {
        val clear = AgentBrowser.actionJs("e1", ActionKind.CLEAR)
        assertContains(clear, "'clear'")

        val check = AgentBrowser.actionJs("e2", ActionKind.CHECK)
        assertContains(check, "'check'")
    }

    @Test
    fun queryJs_encodesRefKindAndLimit() {
        val js = AgentBrowser.queryJs("e9", QueryKind.VALUE, QueryPayload(limitChars = 123))
        assertContains(js, "window.__agentBrowser.query(")
        assertContains(js, "'value'")
        assertContains(js, "\"limitChars\":123")
    }

    @Test
    fun pageJs_encodesKindAndPayload() {
        val js = AgentBrowser.pageJs(PageKind.SCROLL, PagePayload(deltaY = 200))
        assertContains(js, "window.__agentBrowser.page(")
        assertContains(js, "'scroll'")
        assertContains(js, "\"deltaY\":200")
    }

}
