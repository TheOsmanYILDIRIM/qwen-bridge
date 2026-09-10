package com.qwenbridge.proxy

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.qwenbridge.challenge.ChallengeDetector
import com.qwenbridge.challenge.ChallengeOverlayManager
import com.qwenbridge.challenge.CookieSessionManager
import com.qwenbridge.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class QwenApiClient(private val context: Context) {

    private val gson = Gson()
    private val cookieSessionManager = CookieSessionManager.getInstance()
    private val challengeOverlayManager = ChallengeOverlayManager.getInstance(context)
    private val configManager = ConfigManager.getInstance(context)

    private val okHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieSessionManager)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private fun buildStandardHeaders(token: String): Headers {
        val builder = Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
            .add("Accept", "text/event-stream, application/json, text/plain, */*")
            .add("Accept-Language", "en-US,en;q=0.9,tr;q=0.8")
            .add("Origin", "https://chat.qwen.ai")
            .add("Referer", "https://chat.qwen.ai/")
            .add("sec-ch-ua", "\"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"128\"")
            .add("sec-ch-ua-mobile", "?1")
            .add("sec-ch-ua-platform", "\"Android\"")
            .add("Sec-Fetch-Dest", "empty")
            .add("Sec-Fetch-Mode", "cors")
            .add("Sec-Fetch-Site", "same-origin")
            .add("source", "web")
            .add("version", "0.2.83")
            .add("X-Request-Id", UUID.randomUUID().toString())

        if (token.isNotEmpty()) {
            builder.add("Authorization", "Bearer $token")
        }

        val cookieStr = cookieSessionManager.getCookieString()
        if (cookieStr.isNotEmpty()) {
            builder.add("Cookie", cookieStr)
        }

        return builder.build()
    }

    suspend fun validateToken(token: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://chat.qwen.ai/api/v1/auths/")
            .headers(buildStandardHeaders(token))
            .get()
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful && !body.contains("error")) {
                Pair(true, "Token active and authenticated")
            } else {
                Pair(false, "Authentication failed (${response.code}): $body")
            }
        } catch (e: Exception) {
            Pair(false, "Connection error: ${e.localizedMessage}")
        }
    }

    suspend fun getAvailableModels(): List<OpenAIModel> = withContext(Dispatchers.IO) {
        listOf(
            OpenAIModel("qwen3.8-max"),
            OpenAIModel("qwen3.8-max-preview"),
            OpenAIModel("qwen3.7-max"),
            OpenAIModel("qwen3.7-plus"),
            OpenAIModel("qwen3.6-plus"),
            OpenAIModel("qwen3.6-27b"),
            OpenAIModel("qwen3.5-plus"),
            OpenAIModel("qwen3.5-flash"),
            OpenAIModel("qwen3-coder-plus"),
            OpenAIModel("qwen-image"),
            OpenAIModel("qwen-video"),
            OpenAIModel("qwen-deep-research")
        )
    }

    private suspend fun createNewChat(model: String, token: String): String = withContext(Dispatchers.IO) {
        val newChatPayload = QwenNewChatRequest(
            models = listOf(model)
        )
        val body = gson.toJson(newChatPayload).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("https://chat.qwen.ai/api/v2/chats/new")
            .headers(buildStandardHeaders(token))
            .post(body)
            .build()

        val response = okHttpClient.newCall(request).execute()
        val respBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            if (ChallengeDetector.isChallenge(response.code, response.header("Content-Type"), respBody)) {
                challengeOverlayManager.triggerChallengeFlow()
            }
            throw RuntimeException("Failed to create chat (${response.code}): $respBody")
        }

        val json = gson.fromJson(respBody, JsonObject::class.java)
        json.get("id")?.asString ?: json.get("chat_id")?.asString ?: "c_${UUID.randomUUID().toString().take(12)}"
    }

    suspend fun generateImage(
        imageRequest: OpenAIImageGenerationRequest,
        token: String
    ): OpenAIImageGenerationResponse = withContext(Dispatchers.IO) {
        val payload = mapOf(
            "prompt" to imageRequest.prompt,
            "model" to (imageRequest.model ?: "qwen-image"),
            "size" to (imageRequest.size ?: "1024x1024"),
            "n" to imageRequest.n
        )
        val requestBody = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("https://chat.qwen.ai/api/v2/images/generations")
            .headers(buildStandardHeaders(token))
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        val respBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            if (ChallengeDetector.isChallenge(response.code, response.header("Content-Type"), respBody)) {
                challengeOverlayManager.triggerChallengeFlow()
            }
            throw RuntimeException("Image generation failed (${response.code}): $respBody")
        }

        val json = gson.fromJson(respBody, JsonObject::class.java)
        val dataArray = json.getAsJsonArray("data") ?: JsonArray()
        val results = mutableListOf<OpenAIImageData>()

        val targetDir = File("/storage/emulated/0/Documents/Qwen")
        if (!targetDir.exists()) targetDir.mkdirs()

        for (i in 0 until dataArray.size()) {
            val item = dataArray.get(i).asJsonObject
            val url = item.get("url")?.asString
            val b64 = item.get("b64_json")?.asString
            val revised = item.get("revised_prompt")?.asString

            if (url != null) {
                // Download in background to Documents/Qwen
                try {
                    val imgReq = Request.Builder().url(url).build()
                    val imgResp = okHttpClient.newCall(imgReq).execute()
                    if (imgResp.isSuccessful) {
                        val outFile = File(targetDir, "qwen_${System.currentTimeMillis()}_${i + 1}.png")
                        imgResp.body?.byteStream()?.use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                } catch (ignored: Exception) {}
            }

            results.add(OpenAIImageData(url = url, b64Json = b64, revisedPrompt = revised))
        }

        OpenAIImageGenerationResponse(data = results)
    }

    suspend fun generateVideo(
        videoRequest: OpenAIVideoGenerationRequest,
        token: String
    ): OpenAIVideoGenerationResponse = withContext(Dispatchers.IO) {
        val payload = mapOf(
            "prompt" to videoRequest.prompt,
            "model" to (videoRequest.model ?: "qwen-video"),
            "image" to videoRequest.image,
            "duration" to videoRequest.duration
        )
        val requestBody = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("https://chat.qwen.ai/api/v2/videos/generations")
            .headers(buildStandardHeaders(token))
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        val respBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            if (ChallengeDetector.isChallenge(response.code, response.header("Content-Type"), respBody)) {
                challengeOverlayManager.triggerChallengeFlow()
            }
            throw RuntimeException("Video generation failed (${response.code}): $respBody")
        }

        val json = gson.fromJson(respBody, JsonObject::class.java)
        val dataArray = json.getAsJsonArray("data") ?: JsonArray()
        val results = mutableListOf<OpenAIVideoData>()

        for (i in 0 until dataArray.size()) {
            val item = dataArray.get(i).asJsonObject
            val url = item.get("url")?.asString
            results.add(OpenAIVideoData(url = url, status = "completed"))
        }

        OpenAIVideoGenerationResponse(data = results)
    }

    suspend fun streamChatCompletion(
        chatRequest: OpenAIChatRequest,
        token: String,
        outputStream: OutputStream
    ): String = withContext(Dispatchers.IO) {
        val model = chatRequest.model.ifEmpty { configManager.config.value.defaultModel }
        val chatId = createNewChat(model, token)

        var systemPrompt = ""
        val messagesList = mutableListOf<OpenAIMessage>()

        chatRequest.messages.forEach { msg ->
            if (msg.role == "system") {
                systemPrompt = (systemPrompt + "\n" + msg.getTextContent()).trim()
            } else {
                messagesList.add(msg)
            }
        }

        if (chatRequest.tools != null && chatRequest.tools.isNotEmpty()) {
            val toolPrompt = ToolCallParser.buildToolSystemPrompt(chatRequest.tools)
            systemPrompt = (systemPrompt + "\n\n" + toolPrompt).trim()
        }

        val qwenMessages = messagesList.map { msg ->
            val text = msg.getTextContent()
            val imageUrls = msg.getImageUrls()

            val content = if (msg.role == "user" && systemPrompt.isNotEmpty() && msg == messagesList.firstOrNull { it.role == "user" }) {
                "$systemPrompt\n\n$text"
            } else {
                text
            }

            val attachments = if (imageUrls.isNotEmpty()) {
                imageUrls.map { url ->
                    QwenFileAttachment(type = "image", url = url)
                }
            } else null

            QwenMessage(
                fid = UUID.randomUUID().toString(),
                role = msg.role,
                content = content,
                featureConfig = QwenFeatureConfig(
                    thinkingEnabled = chatRequest.enableThinking ?: configManager.config.value.enableThinking
                ),
                files = attachments
            )
        }

        val qwenPayload = QwenChatRequest(
            stream = true,
            version = "2.1",
            incrementalOutput = true,
            chatId = chatId,
            chat_id = chatId,
            model = model,
            messages = qwenMessages
        )

        val requestBody = gson.toJson(qwenPayload).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("https://chat.qwen.ai/api/v2/chat/completions?chat_id=$chatId")
            .headers(buildStandardHeaders(token))
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val errSnippet = response.body?.string() ?: ""
            if (ChallengeDetector.isChallenge(response.code, response.header("Content-Type"), errSnippet)) {
                challengeOverlayManager.triggerChallengeFlow()
                throw RuntimeException("Security verification required. Challenge overlay triggered.")
            }
            throw RuntimeException("Upstream Qwen Error (${response.code}): $errSnippet")
        }

        val responseBody = response.body ?: throw RuntimeException("Empty response body from Qwen")
        val reader = BufferedReader(InputStreamReader(responseBody.byteStream(), Charsets.UTF_8))
        var line: String?

        val completionId = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").take(24)
        var accumulatedRawContent = ""
        var accumulatedReasoningContent = ""

        while (reader.readLine().also { line = it } != null) {
            val currentLine = line?.trim() ?: continue
            if (currentLine.isEmpty() || currentLine.startsWith(":")) continue

            if (currentLine.startsWith("data:")) {
                val dataContent = currentLine.removePrefix("data:").trim()
                if (dataContent == "[DONE]") {
                    val (cleaned, toolCalls) = ToolCallParser.extractToolCalls(accumulatedRawContent)
                    if (toolCalls != null) {
                        val toolChunk = OpenAIChatChunkResponse(
                            id = completionId,
                            model = model,
                            choices = listOf(
                                OpenAIChunkChoice(
                                    delta = OpenAIChatDelta(toolCalls = toolCalls),
                                    finishReason = "tool_calls"
                                )
                            )
                        )
                        val toolChunkData = "data: ${gson.toJson(toolChunk)}\n\n"
                        outputStream.write(toolChunkData.toByteArray(Charsets.UTF_8))
                        outputStream.flush()
                    } else {
                        // Cline requires a final chunk with finish_reason="stop" before [DONE]
                        val finishChunk = OpenAIChatChunkResponse(
                            id = completionId,
                            model = model,
                            choices = listOf(
                                OpenAIChunkChoice(
                                    delta = OpenAIChatDelta(),
                                    finishReason = "stop"
                                )
                            )
                        )
                        outputStream.write("data: ${gson.toJson(finishChunk)}\n\n".toByteArray(Charsets.UTF_8))
                        outputStream.flush()
                    }
                    outputStream.write("data: [DONE]\n\n".toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                    break
                }

                try {
                    val chunkJson = gson.fromJson(dataContent, JsonObject::class.java)

                    // Check for hidden rate limits in 200 responses
                    val textContent = chunkJson.toString()
                    if (ChallengeDetector.isFake200RateLimit(textContent)) {
                        throw RuntimeException("Upstream Rate Limit encountered inside HTTP 200 payload.")
                    }

                    val choices = chunkJson.getAsJsonArray("choices")
                    if (choices != null && choices.size() > 0) {
                        val firstChoice = choices.get(0).asJsonObject
                        val delta = firstChoice.getAsJsonObject("delta")
                        val contentSnippet = delta?.get("content")?.asString
                        val reasoningSnippet = delta?.get("reasoning_content")?.asString

                        if (contentSnippet != null) {
                            accumulatedRawContent += contentSnippet
                        }
                        if (reasoningSnippet != null) {
                            accumulatedReasoningContent += reasoningSnippet
                        }

                        val openaiChunk = OpenAIChatChunkResponse(
                            id = completionId,
                            model = model,
                            choices = listOf(
                                OpenAIChunkChoice(
                                    delta = OpenAIChatDelta(
                                        content = contentSnippet,
                                        reasoningContent = reasoningSnippet
                                    )
                                )
                            )
                        )
                        val sseData = "data: ${gson.toJson(openaiChunk)}\n\n"
                        outputStream.write(sseData.toByteArray(Charsets.UTF_8))
                        outputStream.flush()
                    }
                } catch (e: Exception) {
                    // Ignore transient malformed SSE lines
                }
            }
        }

        accumulatedRawContent
    }
}
