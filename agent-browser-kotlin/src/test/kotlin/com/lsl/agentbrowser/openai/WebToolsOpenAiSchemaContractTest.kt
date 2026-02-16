package com.lsl.agentbrowser.openai

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebToolsOpenAiSchemaContractTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun tool_count_is_capped() {
        assertTrue(
            WebToolsOpenAiSchema.ALL.size <= 25,
            "web tools must be <= 25, actual=${WebToolsOpenAiSchema.ALL.size}",
        )
    }

    @Test
    fun schema_matches_docs_contract_file() {
        val contract = readToolsContractJson()
        val expected = canonicalize(contract)
        val actual = canonicalize(WebToolsOpenAiSchema.toolsJsonArray())
        assertEquals(expected.toString(), actual.toString())
    }

    private fun readToolsContractJson(): JsonArray {
        val file = findToolsContractFile()
        val raw = file.readText(Charsets.UTF_8)
        val el = json.parseToJsonElement(raw)
        return el as? JsonArray ?: error("expected JSON array in ${file.absolutePath}")
    }

    private fun findToolsContractFile(): File {
        val candidates =
            listOf(
                File("docs/tools/web-tools.openai.json"),
                File("../docs/tools/web-tools.openai.json"),
                File("../../docs/tools/web-tools.openai.json"),
            )
        return candidates.firstOrNull { it.exists() && it.isFile }
            ?: error("cannot find docs/tools/web-tools.openai.json from user.dir=${System.getProperty("user.dir")}")
    }
}

private fun canonicalize(el: JsonElement): JsonElement {
    return when (el) {
        is JsonObject -> {
            val sorted = el.entries.sortedBy { it.key }
            JsonObject(sorted.associate { (k, v) -> k to canonicalize(v) })
        }
        is JsonArray -> JsonArray(el.map { canonicalize(it) })
        is JsonPrimitive -> el
        else -> el
    }
}

