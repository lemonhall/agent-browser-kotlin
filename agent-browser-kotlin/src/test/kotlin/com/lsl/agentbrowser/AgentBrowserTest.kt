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
    fun normalizeJsEvalResult_unquotesEvaluateJavascriptStringResult() {
        val raw = "\"{\\\"ok\\\":true,\\\"type\\\":\\\"snapshot\\\"}\""
        assertEquals("{\"ok\":true,\"type\":\"snapshot\"}", AgentBrowser.normalizeJsEvalResult(raw))
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
    fun parseAction_refNotFound_isStructuredError() {
        val actionJson =
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

        val result = AgentBrowser.parseAction(actionJson)
        assertTrue(!result.ok)
        assertEquals("ref_not_found", result.error?.code)
    }
}

