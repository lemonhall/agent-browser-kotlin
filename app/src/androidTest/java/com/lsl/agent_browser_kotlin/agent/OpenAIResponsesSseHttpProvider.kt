package com.lsl.agent_browser_kotlin.agent

import java.io.InputStreamReader
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runInterruptible
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
import me.lemonhall.openagentic.sdk.providers.ModelOutput
import me.lemonhall.openagentic.sdk.providers.ProviderHttpException
import me.lemonhall.openagentic.sdk.providers.ProviderInvalidResponseException
import me.lemonhall.openagentic.sdk.providers.ProviderRateLimitException
import me.lemonhall.openagentic.sdk.providers.ProviderTimeoutException
import me.lemonhall.openagentic.sdk.providers.ResponsesRequest
import me.lemonhall.openagentic.sdk.providers.StreamingResponsesProvider
import me.lemonhall.openagentic.sdk.providers.ToolCall
import me.lemonhall.openagentic.sdk.runtime.ProviderStreamEvent

/**
 * Some proxy upstreams always return SSE (text/event-stream) for `/responses`,
 * even when the client expects a single JSON payload.
 *
 * This provider treats SSE as the primary transport:
 * - stream(): parse SSE and emit [ProviderStreamEvent]
 * - complete(): collect stream() until Completed, then return [ModelOutput]
 */
internal class OpenAIResponsesSseHttpProvider(
    override val name: String = "openai-responses-sse",
    private val baseUrl: String,
    private val apiKeyHeader: String = "authorization",
    private val timeoutMs: Int = 90_000,
    private val defaultStore: Boolean = true,
) : StreamingResponsesProvider {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override suspend fun complete(request: ResponsesRequest): ModelOutput {
        var completed: ModelOutput? = null
        var failed: ProviderStreamEvent.Failed? = null
        stream(request).collect { ev ->
            when (ev) {
                is ProviderStreamEvent.Completed -> completed = ev.output
                is ProviderStreamEvent.Failed -> failed = ev
                is ProviderStreamEvent.TextDelta -> Unit
            }
        }
        if (failed != null) {
            throw ProviderInvalidResponseException(
                "OpenAIResponsesSseHttpProvider: SSE failed: ${failed!!.message}".trim(),
                raw = failed!!.raw?.toString()?.take(2_000),
            )
        }
        return completed
            ?: throw ProviderInvalidResponseException("OpenAIResponsesSseHttpProvider: stream ended without completion")
    }

    override fun stream(request: ResponsesRequest): Flow<ProviderStreamEvent> =
        flow {
            val apiKey = request.apiKey?.trim().orEmpty()
            require(apiKey.isNotEmpty()) { "OpenAIResponsesSseHttpProvider: apiKey is required" }

            val url = "${baseUrl.trimEnd('/')}/responses"
            val headers = buildHeaders(apiKey = apiKey, acceptEventStream = true)
            val payload =
                buildJsonObject {
                    put("model", JsonPrimitive(request.model))
                    put("input", JsonArray(request.input))
                    put("stream", JsonPrimitive(true))
                    val storeFlag = request.store ?: defaultStore
                    put("store", JsonPrimitive(storeFlag))
                    if (!request.previousResponseId.isNullOrBlank()) {
                        put("previous_response_id", JsonPrimitive(request.previousResponseId))
                    }
                    if (request.tools.isNotEmpty()) put("tools", JsonArray(request.tools))
                }

            val body = json.encodeToString(JsonObject.serializer(), payload)
            val sseParser = SseParser()
            val decoder = ResponsesSseDecoder(json = json)

            val conn = (URI(url).toURL().openConnection() as java.net.HttpURLConnection)
            try {
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
                        throw ProviderTimeoutException("OpenAIResponsesSseHttpProvider: timeout", t)
                    }

                val stream =
                    try {
                        if (status >= 400) conn.errorStream else conn.inputStream
                    } catch (_: Throwable) {
                        null
                    }
                if (stream == null) throw RuntimeException("no response stream")

                if (status >= 400) {
                    val err = stream.readBytes().toString(Charsets.UTF_8)
                    if (status == 429) {
                        throw ProviderRateLimitException("HTTP 429 from $url: $err".trim(), retryAfterMs = null)
                    }
                    throw ProviderHttpException(status = status, message = "HTTP $status from $url: $err".trim(), body = err)
                }

                val contentType = conn.getHeaderField("content-type")?.lowercase().orEmpty()
                val isEventStream = contentType.contains("text/event-stream")

                if (!isEventStream) {
                    val raw = stream.readBytes().toString(Charsets.UTF_8)
                    val obj =
                        try {
                            json.parseToJsonElement(raw).jsonObject
                        } catch (t: Throwable) {
                            throw ProviderInvalidResponseException(
                                "OpenAIResponsesSseHttpProvider: invalid JSON response",
                                raw = raw.take(2_000),
                                cause = t,
                            )
                        }
                    emit(ProviderStreamEvent.Completed(parseResponsesJson(obj)))
                    return@flow
                }

                InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                    val buf = CharArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = runInterruptible { reader.read(buf) }
                        if (n < 0) break
                        for (ev in sseParser.feed(String(buf, 0, n))) {
                            for (sev in decoder.onEvent(ev)) emit(sev)
                        }
                    }
                    for (ev in sseParser.endOfInput()) {
                        for (sev in decoder.onEvent(ev)) emit(sev)
                    }
                }
                for (sev in decoder.finish()) emit(sev)
            } finally {
                try {
                    conn.disconnect()
                } catch (_: Throwable) {
                }
            }
        }.flowOn(Dispatchers.IO)

    private fun buildHeaders(
        apiKey: String,
        acceptEventStream: Boolean = false,
    ): Map<String, String> {
        val h = linkedMapOf<String, String>()
        h["content-type"] = "application/json"
        if (acceptEventStream) h["accept"] = "text/event-stream"
        if (apiKeyHeader.lowercase() == "authorization") {
            h["authorization"] = "Bearer $apiKey"
        } else {
            h[apiKeyHeader] = apiKey
        }
        return h
    }

    private fun parseResponsesJson(root: JsonObject): ModelOutput {
        val responseId = root["id"]?.jsonPrimitive?.contentOrNull()
        val usage = root["usage"] as? JsonObject
        val outputItems = (root["output"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
        val assistantText = parseAssistantText(outputItems)
        val toolCalls = parseToolCalls(outputItems)
        return ModelOutput(
            assistantText = assistantText,
            toolCalls = toolCalls,
            usage = usage,
            responseId = responseId,
            providerMetadata = null,
        )
    }

    private fun parseAssistantText(outputItems: List<JsonObject>): String? {
        val parts = mutableListOf<String>()
        for (item in outputItems) {
            if (item["type"]?.jsonPrimitive?.contentOrNull() != "message") continue
            val content = item["content"] as? JsonArray ?: continue
            for (partEl in content) {
                val part = partEl as? JsonObject ?: continue
                if (part["type"]?.jsonPrimitive?.contentOrNull() != "output_text") continue
                val text = part["text"]?.jsonPrimitive?.contentOrNull()
                if (!text.isNullOrEmpty()) parts.add(text)
            }
        }
        if (parts.isEmpty()) return null
        return parts.joinToString("")
    }

    private fun parseToolCalls(outputItems: List<JsonObject>): List<ToolCall> {
        val out = mutableListOf<ToolCall>()
        for (item in outputItems) {
            if (item["type"]?.jsonPrimitive?.contentOrNull() != "function_call") continue
            val callId = item["call_id"]?.jsonPrimitive?.contentOrNull()?.takeIf { it.isNotBlank() } ?: continue
            val name = item["name"]?.jsonPrimitive?.contentOrNull()?.takeIf { it.isNotBlank() } ?: continue
            val argsEl = item["arguments"]
            val argsObj =
                when (argsEl) {
                    is JsonObject -> argsEl
                    is JsonPrimitive -> parseArgs(argsEl.contentOrNull().orEmpty())
                    null, JsonNull -> buildJsonObject { }
                    else -> buildJsonObject { put("_raw", JsonPrimitive(argsEl.toString())) }
                }
            out.add(ToolCall(toolUseId = callId, name = name, arguments = argsObj))
        }
        return out
    }

    private fun parseArgs(raw: String): JsonObject {
        val s = raw.trim()
        if (s.isEmpty()) return buildJsonObject { }
        return try {
            val el = json.parseToJsonElement(s)
            el as? JsonObject ?: buildJsonObject { put("_raw", JsonPrimitive(raw)) }
        } catch (_: Throwable) {
            buildJsonObject { put("_raw", JsonPrimitive(raw)) }
        }
    }
}

private fun JsonPrimitive.contentOrNull(): String? =
    try {
        this.content
    } catch (_: Throwable) {
        null
    }

private data class SseEvent(
    val data: String,
)

private class SseParser {
    private val buffer = StringBuilder()
    private val dataLines = mutableListOf<String>()

    fun feed(chunk: CharSequence): List<SseEvent> {
        buffer.append(chunk)
        val out = mutableListOf<SseEvent>()
        while (true) {
            val nl = buffer.indexOf("\n")
            if (nl < 0) break
            var line = buffer.substring(0, nl)
            buffer.delete(0, nl + 1)
            if (line.endsWith("\r")) line = line.dropLast(1)
            processLine(line, out)
        }
        return out
    }

    fun endOfInput(): List<SseEvent> {
        val out = mutableListOf<SseEvent>()
        if (buffer.isNotEmpty()) {
            var line = buffer.toString()
            buffer.setLength(0)
            if (line.endsWith("\r")) line = line.dropLast(1)
            processLine(line, out)
        }
        if (dataLines.isNotEmpty()) {
            out.add(SseEvent(dataLines.joinToString("\n")))
            dataLines.clear()
        }
        return out
    }

    private fun processLine(
        line: String,
        out: MutableList<SseEvent>,
    ) {
        if (line.isEmpty()) {
            if (dataLines.isNotEmpty()) {
                out.add(SseEvent(dataLines.joinToString("\n")))
                dataLines.clear()
            }
            return
        }
        if (line.startsWith(":")) return
        val sep = line.indexOf(':')
        val field = if (sep >= 0) line.substring(0, sep) else line
        var value = if (sep >= 0) line.substring(sep + 1) else ""
        if (value.startsWith(" ")) value = value.drop(1)
        if (field == "data") dataLines.add(value)
    }
}

private class ResponsesSseDecoder(
    private val json: Json,
) {
    private var lastResponse: JsonObject? = null
    private var failed: ProviderStreamEvent.Failed? = null
    private var done: Boolean = false

    fun onEvent(event: SseEvent): List<ProviderStreamEvent> {
        if (failed != null || done) return emptyList()

        val data = event.data.trim()
        if (data.isBlank()) return emptyList()
        if (data == "[DONE]") {
            done = true
            return emptyList()
        }

        val obj =
            try {
                json.parseToJsonElement(data).jsonObject
            } catch (_: Throwable) {
                return emptyList()
            }

        val type = obj["type"]?.jsonPrimitive?.contentOrNull()?.trim().orEmpty()
        when (type) {
            "response.output_text.delta" -> {
                val delta = obj["delta"]?.jsonPrimitive?.contentOrNull()
                if (!delta.isNullOrEmpty()) return listOf(ProviderStreamEvent.TextDelta(delta))
                return emptyList()
            }
            "response.completed" -> {
                lastResponse = (obj["response"] as? JsonObject) ?: obj
                return emptyList()
            }
            "error" -> {
                val ev = ProviderStreamEvent.Failed(message = obj.toString(), raw = obj)
                failed = ev
                done = true
                return listOf(ev)
            }
            else -> return emptyList()
        }
    }

    fun finish(): List<ProviderStreamEvent> {
        if (failed != null) return emptyList()
        val resp = lastResponse ?: return listOf(ProviderStreamEvent.Failed(message = "stream ended without response.completed"))
        val responseId = resp["id"]?.jsonPrimitive?.contentOrNull()
        val usage = resp["usage"] as? JsonObject
        val outputItems = (resp["output"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

        val parts = mutableListOf<String>()
        val calls = mutableListOf<ToolCall>()

        for (item in outputItems) {
            when (item["type"]?.jsonPrimitive?.contentOrNull()) {
                "message" -> {
                    val content = item["content"] as? JsonArray ?: continue
                    for (partEl in content) {
                        val part = partEl as? JsonObject ?: continue
                        if (part["type"]?.jsonPrimitive?.contentOrNull() != "output_text") continue
                        val text = part["text"]?.jsonPrimitive?.contentOrNull()
                        if (!text.isNullOrEmpty()) parts.add(text)
                    }
                }
                "function_call" -> {
                    val callId = item["call_id"]?.jsonPrimitive?.contentOrNull()?.takeIf { it.isNotBlank() } ?: continue
                    val name = item["name"]?.jsonPrimitive?.contentOrNull()?.takeIf { it.isNotBlank() } ?: continue
                    val argsEl = item["arguments"]
                    val argsObj =
                        when (argsEl) {
                            is JsonObject -> argsEl
                            is JsonPrimitive -> {
                                val raw = argsEl.contentOrNull().orEmpty()
                                val s = raw.trim()
                                if (s.isEmpty()) buildJsonObject { }
                                else {
                                    try {
                                        val el = json.parseToJsonElement(s)
                                        el as? JsonObject ?: buildJsonObject { put("_raw", JsonPrimitive(raw)) }
                                    } catch (_: Throwable) {
                                        buildJsonObject { put("_raw", JsonPrimitive(raw)) }
                                    }
                                }
                            }
                            null, JsonNull -> buildJsonObject { }
                            else -> buildJsonObject { put("_raw", JsonPrimitive(argsEl.toString())) }
                        }
                    calls.add(ToolCall(toolUseId = callId, name = name, arguments = argsObj))
                }
            }
        }

        val assistantText = if (parts.isEmpty()) null else parts.joinToString("")
        return listOf(
            ProviderStreamEvent.Completed(
                ModelOutput(
                    assistantText = assistantText,
                    toolCalls = calls,
                    usage = usage,
                    responseId = responseId,
                    providerMetadata = null,
                ),
            ),
        )
    }
}
