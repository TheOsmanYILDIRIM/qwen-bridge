package com.qwenbridge.proxy

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.qwenbridge.data.*
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.concurrent.thread

class EmbeddedProxyServer(
    private val context: Context,
    private val port: Int
) : NanoHTTPD("0.0.0.0", port) {

    private val gson = Gson()
    private val apiClient = QwenApiClient(context)
    private val configManager = ConfigManager.getInstance(context)

    override fun serve(session: IHTTPSession): Response {
        val startNs = System.nanoTime()
        val uri = session.uri
        val method = session.method

        if (method == Method.OPTIONS) {
            return createCorsResponse(Response.Status.OK, "text/plain", "")
        }

        configManager.incrementRequestsCount()

        val map = HashMap<String, String>()
        if (method == Method.POST || method == Method.PUT) {
            try {
                session.parseBody(map)
            } catch (ignored: Exception) {}
        }
        val postData = map["postData"] ?: ""
        var capturedResponseBody = ""

        return try {
            when {
                uri == "/v1/models" && method == Method.GET -> {
                    handleModels().also { capturedResponseBody = "[Model List JSON]" }
                }
                uri == "/v1/chat/completions" && method == Method.POST -> {
                    handleChatCompletions(session, postData).also {
                        capturedResponseBody = if (postData.contains("\"stream\":true")) "[Streaming SSE Chunks]" else "[Chat Completion JSON]"
                    }
                }
                (uri == "/v1/images/generations" || uri == "/v1/images/edits") && method == Method.POST -> {
                    handleImageGeneration(session, postData).also {
                        capturedResponseBody = "[Image Generation JSON]"
                    }
                }
                uri == "/v1/videos/generations" && method == Method.POST -> {
                    handleVideoGeneration(session, postData).also {
                        capturedResponseBody = "[Video Generation JSON]"
                    }
                }
                uri == "/v1/validate" -> {
                    handleValidate(session).also { capturedResponseBody = "[Token Validation JSON]" }
                }
                uri == "/health" || uri == "/status" -> {
                    handleHealth().also { capturedResponseBody = "[Health Status JSON]" }
                }
                else -> {
                    val notFoundMsg = gson.toJson(mapOf("error" to "Endpoint not found: $uri"))
                    capturedResponseBody = notFoundMsg
                    createCorsResponse(Response.Status.NOT_FOUND, "application/json", notFoundMsg)
                }
            }
        } catch (e: Exception) {
            val durationMs = (System.nanoTime() - startNs) / 1_000_000
            val errMsg = gson.toJson(mapOf("error" to (e.message ?: "Internal Server Error")))
            configManager.addLog(
                LogEntry(
                    method = method.name,
                    path = uri,
                    statusCode = 500,
                    durationMs = durationMs,
                    details = e.message ?: "Internal Server Error",
                    requestHeaders = session.headers,
                    requestBody = postData,
                    responseBody = errMsg
                )
            )
            createCorsResponse(Response.Status.INTERNAL_ERROR, "application/json", errMsg)
        }.also { response ->
            val durationMs = (System.nanoTime() - startNs) / 1_000_000
            val code = response.status.requestStatus
            configManager.addLog(
                LogEntry(
                    method = method.name,
                    path = uri,
                    statusCode = code,
                    durationMs = durationMs,
                    details = "Served in ${durationMs}ms",
                    requestHeaders = session.headers,
                    requestBody = postData,
                    responseBody = capturedResponseBody
                )
            )
        }
    }

    private fun handleModels(): Response {
        val models = runBlocking { apiClient.getAvailableModels() }
        val response = OpenAIModelListResponse(data = models)
        return createCorsResponse(Response.Status.OK, "application/json", gson.toJson(response))
    }

    private fun handleHealth(): Response {
        val statusMap = mapOf(
            "status" to "ok",
            "uptime" to System.currentTimeMillis(),
            "port" to port,
            "total_requests" to configManager.config.value.totalRequests,
            "last_challenge_solved" to configManager.config.value.lastChallengeSolvedTime
        )
        return createCorsResponse(Response.Status.OK, "application/json", gson.toJson(statusMap))
    }

    private fun handleValidate(session: IHTTPSession): Response {
        val token = extractToken(session)
        val (isValid, message) = runBlocking { apiClient.validateToken(token) }
        val map = mapOf("valid" to isValid, "message" to message)
        return createCorsResponse(
            if (isValid) Response.Status.OK else Response.Status.UNAUTHORIZED,
            "application/json",
            gson.toJson(map)
        )
    }

    private fun handleImageGeneration(session: IHTTPSession, postData: String): Response {
        val token = extractToken(session)
        if (token.isEmpty()) {
            return createCorsResponse(
                Response.Status.UNAUTHORIZED,
                "application/json",
                gson.toJson(mapOf("error" to "No Qwen Access Token provided."))
            )
        }

        val imgRequest = gson.fromJson(postData, OpenAIImageGenerationRequest::class.java)
        val result = runBlocking { apiClient.generateImage(imgRequest, token) }
        return createCorsResponse(Response.Status.OK, "application/json", gson.toJson(result))
    }

    private fun handleVideoGeneration(session: IHTTPSession, postData: String): Response {
        val token = extractToken(session)
        if (token.isEmpty()) {
            return createCorsResponse(
                Response.Status.UNAUTHORIZED,
                "application/json",
                gson.toJson(mapOf("error" to "No Qwen Access Token provided."))
            )
        }

        val videoRequest = gson.fromJson(postData, OpenAIVideoGenerationRequest::class.java)
        val result = runBlocking { apiClient.generateVideo(videoRequest, token) }
        return createCorsResponse(Response.Status.OK, "application/json", gson.toJson(result))
    }

    private fun handleChatCompletions(session: IHTTPSession, postData: String): Response {
        val chatRequest = gson.fromJson(postData, OpenAIChatRequest::class.java)
        val token = extractToken(session)
        if (token.isEmpty()) {
            return createCorsResponse(
                Response.Status.UNAUTHORIZED,
                "application/json",
                gson.toJson(mapOf("error" to "No Qwen Access Token provided in request or config."))
            )
        }

        if (chatRequest.stream) {
            val pipedInputStream = PipedInputStream()
            val pipedOutputStream = PipedOutputStream(pipedInputStream)

            thread {
                try {
                    // Send an immediate SSE comment to flush headers and prevent client read timeout
                    pipedOutputStream.write(": keep-alive\n\n".toByteArray(Charsets.UTF_8))
                    pipedOutputStream.flush()

                    runBlocking {
                        apiClient.streamChatCompletion(chatRequest, token, pipedOutputStream)
                    }
                } catch (e: Exception) {
                    val errJson = gson.toJson(mapOf("error" to (e.message ?: "Stream error")))
                    pipedOutputStream.write("data: $errJson\n\n".toByteArray(Charsets.UTF_8))
                    pipedOutputStream.write("data: [DONE]\n\n".toByteArray(Charsets.UTF_8))
                    pipedOutputStream.flush()
                } finally {
                    try {
                        pipedOutputStream.close()
                    } catch (ignored: Exception) {}
                }
            }

            val response = NanoHTTPD.newChunkedResponse(
                Response.Status.OK,
                "text/event-stream; charset=utf-8",
                pipedInputStream
            )
            addCorsHeaders(response)
            response.addHeader("Cache-Control", "no-cache, no-transform")
            response.addHeader("Connection", "keep-alive")
            response.addHeader("X-Accel-Buffering", "no")
            return response
        } else {
            val byteStream = ByteArrayOutputStream()
            runBlocking {
                apiClient.streamChatCompletion(chatRequest, token, byteStream)
            }
            val rawSse = byteStream.toString(Charsets.UTF_8.name())

            var fullContent = ""
            var finalModel = chatRequest.model

            rawSse.lines().forEach { line ->
                if (line.startsWith("data:") && !line.contains("[DONE]")) {
                    try {
                        val json = gson.fromJson(line.removePrefix("data:").trim(), JsonObject::class.java)
                        val choices = json.getAsJsonArray("choices")
                        if (choices != null && choices.size() > 0) {
                            val delta = choices[0].asJsonObject.getAsJsonObject("delta")
                            delta.get("content")?.asString?.let { fullContent += it }
                        }
                    } catch (ignored: Exception) {}
                }
            }

            val (cleaned, toolCalls) = ToolCallParser.extractToolCalls(fullContent)
            val nonStreamResponse = OpenAIChatResponse(
                id = "chatcmpl-" + java.util.UUID.randomUUID().toString().take(16),
                model = finalModel,
                choices = listOf(
                    OpenAIChoice(
                        message = OpenAIMessage(
                            role = "assistant",
                            content = cleaned,
                            toolCalls = toolCalls
                        ),
                        finishReason = if (toolCalls != null) "tool_calls" else "stop"
                    )
                )
            )

            return createCorsResponse(
                Response.Status.OK,
                "application/json",
                gson.toJson(nonStreamResponse)
            )
        }
    }

    private fun extractToken(session: IHTTPSession): String {
        val authHeader = session.headers["authorization"] ?: session.headers["Authorization"]
        if (!authHeader.isNullOrEmpty() && authHeader.startsWith("Bearer ", ignoreCase = true)) {
            val extracted = authHeader.substring(7).trim()
            val dummyTokens = setOf(
                "your_qwen_access_token",
                "qwen-local-token",
                "test",
                "default",
                "dummy",
                "none",
                "null",
                "undefined"
            )
            if (extracted.isNotEmpty() && !dummyTokens.contains(extracted.lowercase())) {
                return extracted
            }
        }
        return configManager.config.value.token
    }

    private fun createCorsResponse(status: Response.IStatus, mimeType: String, txt: String): Response {
        val res = NanoHTTPD.newFixedLengthResponse(status, mimeType, txt)
        addCorsHeaders(res)
        return res
    }

    private fun addCorsHeaders(response: Response) {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, Accept, Origin")
        response.addHeader("Access-Control-Max-Age", "86400")
    }
}
