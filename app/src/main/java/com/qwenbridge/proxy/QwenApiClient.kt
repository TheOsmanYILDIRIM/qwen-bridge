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

    private fun buildStandardHeaders(token: String, refererPath: String = "/"): Headers {
        val builder = Headers.Builder()
            // DanyAPI'nin çalışan UA'sı: desktop Chrome (Android UA engelleniyor)
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36")
            .add("Accept", "application/json, text/plain, */*")
            .add("Accept-Language", "en-US,en;q=0.9")
            .add("Origin", "https://chat.qwen.ai")
            .add("Referer", "https://chat.qwen.ai$refererPath")
            .add("sec-ch-ua", "\"Not=A?Brand\";v=\"99\", \"Google Chrome\";v=\"151\", \"Chromium\";v=\"151\"")
            .add("sec-ch-ua-mobile", "?0")
            .add("sec-ch-ua-platform", "\"Windows\"")
            .add("Sec-Fetch-Dest", "empty")
            .add("Sec-Fetch-Mode", "cors")
            .add("Sec-Fetch-Site", "same-origin")
            .add("source", "web")
            .add("version", "0.2.83")
            .add("X-Request-Id", UUID.randomUUID().toString())
            // DanyAPI'nin kritik Timezone header'ı — WAF bu başlığı kontrol ediyor
            .add("Timezone", buildTimezoneHeader())

        if (token.isNotEmpty()) {
            builder.add("Authorization", "Bearer $token")
        }

        val cookieStr = cookieSessionManager.getCookieString()
        if (cookieStr.isNotEmpty()) {
            builder.add("Cookie", cookieStr)
        }

        return builder.build()
    }

    /** DanyAPI timezone_header() Kotlin port: "Tue Sep 10 2026 08:23:42 GMT+0300" */
    private fun buildTimezoneHeader(): String {
        val sdf = java.text.SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss", java.util.Locale.US)
        val now = java.util.Date()
        val tz = java.util.TimeZone.getDefault()
        val offsetMs = tz.getOffset(now.time)
        val h = offsetMs / 3600000
        val m = Math.abs(offsetMs % 3600000) / 60000
        val sign = if (offsetMs >= 0) "+" else "-"
        return "${sdf.format(now)} GMT${sign}%02d%02d".format(Math.abs(h), m)
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
        // DanyAPI'nin create_chat() tam formatı
        val payload = mapOf(
            "chatId" to "",
            "models" to listOf(model),
            "project_id" to "",
            "timestamp" to System.currentTimeMillis(),
            "chat_type" to "t2t",
            "chat_mode" to "normal"
        )
        val body = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)
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
                throw RuntimeException("WAF challenge triggered — overlay açık, lütfen doğrulayın")
            }
            throw RuntimeException("Failed to create chat (${response.code}): $respBody")
        }

        // DanyAPI'nin _biz() parsing mantığı: {success: true, data: {id: "..."}}
        val json = gson.fromJson(respBody, JsonObject::class.java)
        val dataObj = json.getAsJsonObject("data")
        dataObj?.get("id")?.asString
            ?: json.get("id")?.asString
            ?: json.get("chat_id")?.asString
            ?: "c_${UUID.randomUUID().toString().take(12)}"
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

        val thinkingEnabled = chatRequest.enableThinking ?: configManager.config.value.enableThinking
        val ts = System.currentTimeMillis() / 1000L // Unix timestamp (saniye)

        // DanyAPI: her mesaj için user_fid + response_fid çifti
        // Son user mesajı için bu çift kullanılıyor, öncekiler sadece history
        val userFid = UUID.randomUUID().toString()
        val responseFid = UUID.randomUUID().toString()

        // Tüm mesajları DanyAPI formatına çevir
        // Son user mesajı: tam DanyAPI formatı (parentId, childrenIds, user_action, sub_chat_type)
        // Diğer mesajlar: basit history formatı
        val lastUserIndex = messagesList.indexOfLast { it.role == "user" }

        val qwenMessagesRaw = messagesList.mapIndexed { index, msg ->
            val text = msg.getTextContent()
            val imageUrls = msg.getImageUrls()

            val content = if (msg.role == "user" && systemPrompt.isNotEmpty() &&
                msg == messagesList.firstOrNull { it.role == "user" }) {
                "$systemPrompt\n\n$text"
            } else {
                text
            }

            val isLastUser = (index == lastUserIndex && msg.role == "user")
            val fid = if (isLastUser) userFid else UUID.randomUUID().toString()

            val featureConfig = mapOf(
                "thinking_enabled" to thinkingEnabled,
                "output_schema" to "phase",
                "research_mode" to "normal",
                "auto_thinking" to thinkingEnabled,
                "thinking_mode" to (if (thinkingEnabled) "Auto" else "Manual"),
                "thinking_format" to "summary",
                "auto_search" to false
            )

            val attachments = if (imageUrls.isNotEmpty()) {
                imageUrls.map { url -> mapOf("type" to "image", "url" to url) }
            } else emptyList<Map<String, String>>()

            // DanyAPI'nin tam mesaj yapısı
            mapOf(
                "id" to null,
                "fid" to fid,
                "parentId" to null,               // history için null, son mesaj için de null (tek chat)
                "childrenIds" to (if (isLastUser) listOf(responseFid) else emptyList<String>()),
                "role" to msg.role,
                "content" to content,
                "user_action" to (if (msg.role == "user") "chat" else ""),
                "files" to attachments,
                "timestamp" to ts,
                "models" to listOf(model),
                "model" to "",
                "chat_type" to "t2t",
                "feature_config" to featureConfig,
                "extra" to mapOf("meta" to mapOf("subChatType" to "t2t")),
                "sub_chat_type" to "t2t",
                "parent_id" to null
            )
        }

        // DanyAPI'nin completion() tam body formatı
        val qwenPayload = mapOf(
            "stream" to true,
            "version" to "2.1",
            "incremental_output" to true,
            "chatId" to chatId,
            "parentId" to "",
            "chat_id" to chatId,
            "chat_mode" to "normal",
            "model" to model,
            "parent_id" to null,
            "messages" to qwenMessagesRaw,
            "timestamp" to ts
        )

        val requestBody = gson.toJson(qwenPayload).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("https://chat.qwen.ai/api/v2/chat/completions?chat_id=$chatId")
            // DanyAPI: stream isteğinde Referer chat sayfasına işaret ediyor
            .headers(buildStandardHeaders(token, "/c/$chatId"))
            .addHeader("Accept", "text/event-stream")
            .addHeader("X-Accel-Buffering", "no")
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
        var streamFinishedCleanly = false  // [DONE] aldık mı?

        try {
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.isEmpty() || currentLine.startsWith(":")) continue

                if (currentLine.startsWith("data:")) {
                    val dataContent = currentLine.removePrefix("data:").trim()

                    if (dataContent == "[DONE]") {
                        streamFinishedCleanly = true
                        // [DONE] geldi — finish chunk gönder
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
                            outputStream.write("data: ${gson.toJson(toolChunk)}\n\n".toByteArray(Charsets.UTF_8))
                            outputStream.flush()
                        } else {
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

                        // Sahte 200 rate limit kontrolü
                        if (ChallengeDetector.isFake200RateLimit(chunkJson.toString())) {
                            throw RuntimeException("Rate limit inside HTTP 200 payload.")
                        }

                        // --- Qwen SSE format: choices[]  VEYA  output/content (phase format) ---
                        var contentSnippet: String? = null
                        var reasoningSnippet: String? = null

                        val choices = chunkJson.getAsJsonArray("choices")
                        if (choices != null && choices.size() > 0) {
                            // Standart OpenAI-uyumlu format
                            val firstChoice = choices.get(0).asJsonObject
                            val delta = firstChoice.getAsJsonObject("delta")
                            contentSnippet = delta?.get("content")?.asString
                            reasoningSnippet = delta?.get("reasoning_content")?.asString
                        } else {
                            // Qwen phase-based format: {"output": {"text": "..."}, "type": "generation"}
                            val output = chunkJson.getAsJsonObject("output")
                            if (output != null) {
                                contentSnippet = output.get("text")?.asString
                                    ?: output.get("content")?.asString
                                reasoningSnippet = output.get("reasoning_content")?.asString
                                    ?: output.get("thinking_content")?.asString
                            }
                            // Alternatif: {"content": "..."}
                            if (contentSnippet == null) {
                                contentSnippet = chunkJson.get("content")?.asString
                            }
                        }

                        if (contentSnippet != null) accumulatedRawContent += contentSnippet
                        if (reasoningSnippet != null) accumulatedReasoningContent += reasoningSnippet

                        // Sadece içerik varsa chunk gönder (boş chunk gönderme)
                        if (contentSnippet != null || reasoningSnippet != null) {
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
                            outputStream.write("data: ${gson.toJson(openaiChunk)}\n\n".toByteArray(Charsets.UTF_8))
                            outputStream.flush()
                        }
                    } catch (e: Exception) {
                        // Hatalı SSE satırını atla, devam et
                    }
                }
            }
        } finally {
            // KRİTİK: [DONE] gelmeden bağlantı kapandıysa (Qwen TCP close) Cline'ı askıda bırakma
            if (!streamFinishedCleanly) {
                try {
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
                    outputStream.write("data: [DONE]\n\n".toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                } catch (ignored: Exception) { }
            }
            reader.close()
        }

        accumulatedRawContent
    }
}
