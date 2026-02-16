package com.lsl.agent_browser_kotlin.agent

import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.lemonhall.openagentic.sdk.providers.LegacyProvider
import me.lemonhall.openagentic.sdk.providers.LegacyRequest
import me.lemonhall.openagentic.sdk.providers.ModelOutput
import me.lemonhall.openagentic.sdk.providers.ProviderHttpException
import me.lemonhall.openagentic.sdk.providers.ProviderInvalidResponseException
import me.lemonhall.openagentic.sdk.providers.ProviderRateLimitException
import me.lemonhall.openagentic.sdk.providers.ProviderTimeoutException
import me.lemonhall.openagentic.sdk.providers.ToolCall

internal class OpenAIChatCompletionsHttpProvider(
    override val name: String = "openai-chat-completions",
    private val baseUrl: String,
    private val apiKeyHeader: String = "authorization",
    private val timeoutMs: Int = 60_000,
) : LegacyProvider {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override suspend fun complete(request: LegacyRequest): ModelOutput {
        val apiKey = request.apiKey?.trim().orEmpty()
        require(apiKey.isNotEmpty()) { "OpenAIChatCompletionsHttpProvider: apiKey is required" }

        val url = "${baseUrl.trimEnd('/')}/chat/completions"
        val headers = buildHeaders(apiKey)
        val payload =
            buildJsonObject {
                put("model", JsonPrimitive(request.model))
                put("messages", JsonArray(request.messages))
                if (request.tools.isNotEmpty()) {
                    put("tools", JsonArray(request.tools))
                    put("tool_choice", JsonPrimitive("auto"))
                }
            }
        val body = json.encodeToString(JsonObject.serializer(), payload)
        val raw = postJson(url = url, headers = headers, body = body)
        val root =
            try {
                json.parseToJsonElement(raw).jsonObject
            } catch (t: Throwable) {
                throw ProviderInvalidResponseException("OpenAIChatCompletionsHttpProvider: invalid JSON response", raw = raw.take(2_000), cause = t)
            }

        val choices = root["choices"] as? JsonArray ?: JsonArray(emptyList())
        val first = choices.firstOrNull() as? JsonObject
        val message = first?.get("message") as? JsonObject

        val assistantText = message?.get("content")?.let { el ->
            if (el is JsonNull) null else (el as? JsonPrimitive)?.content
        }

        val toolCalls = parseToolCalls(message, json = json)
        val usage = root["usage"] as? JsonObject
        val responseId = root["id"]?.jsonPrimitive?.contentOrNull()

        return ModelOutput(
            assistantText = assistantText,
            toolCalls = toolCalls,
            usage = usage,
            responseId = responseId,
            providerMetadata = null,
        )
    }

    private fun parseToolCalls(
        message: JsonObject?,
        json: Json,
    ): List<ToolCall> {
        val out = mutableListOf<ToolCall>()
        val calls = message?.get("tool_calls") as? JsonArray ?: return out
        for (el in calls) {
            val obj = el as? JsonObject ?: continue
            val id = obj["id"]?.jsonPrimitive?.contentOrNull()?.takeIf { it.isNotBlank() } ?: continue
            val fn = obj["function"] as? JsonObject ?: continue
            val name = fn["name"]?.jsonPrimitive?.contentOrNull()?.takeIf { it.isNotBlank() } ?: continue
            val argsRaw = fn["arguments"]?.jsonPrimitive?.contentOrNull().orEmpty()
            val argsObj =
                try {
                    val parsed = json.parseToJsonElement(argsRaw)
                    parsed as? JsonObject ?: buildJsonObject { put("_raw", JsonPrimitive(argsRaw)) }
                } catch (_: Throwable) {
                    buildJsonObject { put("_raw", JsonPrimitive(argsRaw)) }
                }
            out.add(ToolCall(toolUseId = id, name = name, arguments = argsObj))
        }
        return out
    }

    private fun buildHeaders(apiKey: String): Map<String, String> {
        val h = linkedMapOf<String, String>()
        h["content-type"] = "application/json"
        if (apiKeyHeader.lowercase() == "authorization") {
            h["authorization"] = "Bearer $apiKey"
        } else {
            h[apiKeyHeader] = apiKey
        }
        return h
    }

    private suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        body: String,
    ): String {
        return withContext(Dispatchers.IO) {
            val conn = (URI(url).toURL().openConnection() as java.net.HttpURLConnection)
            conn.requestMethod = "POST"
            conn.instanceFollowRedirects = false
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val status =
                try {
                    conn.responseCode
                } catch (t: java.net.SocketTimeoutException) {
                    throw ProviderTimeoutException("OpenAIChatCompletionsHttpProvider: timeout", t)
                }
            val stream =
                try {
                    if (status >= 400) conn.errorStream else conn.inputStream
                } catch (_: Throwable) {
                    null
                }
            val raw = stream?.readBytes()?.toString(Charsets.UTF_8).orEmpty()
            if (status >= 400) {
                if (status == 429) {
                    throw ProviderRateLimitException("HTTP 429 from $url: $raw".trim(), retryAfterMs = null)
                }
                throw ProviderHttpException(status = status, message = "HTTP $status from $url: $raw".trim(), body = raw)
            }
            raw
        }
    }
}

private fun JsonPrimitive.contentOrNull(): String? =
    try {
        this.content
    } catch (_: Throwable) {
        null
    }
