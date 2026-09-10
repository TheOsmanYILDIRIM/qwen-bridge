package com.qwenbridge.data

import com.google.gson.annotations.SerializedName

// --- OpenAI API Models ---

data class OpenAIMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: Any? = null, // String or List<OpenAIContentPart>
    @SerializedName("name") val name: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<OpenAIToolCall>? = null,
    @SerializedName("tool_call_id") val toolCallId: String? = null
) {
    fun getTextContent(): String {
        return when (content) {
            is String -> content
            is List<*> -> {
                content.filterIsInstance<Map<*, *>>().mapNotNull { part ->
                    val type = part["type"] as? String
                    if (type == "text") part["text"] as? String else null
                }.joinToString("\n")
            }
            else -> content?.toString() ?: ""
        }
    }

    fun getImageUrls(): List<String> {
        val result = mutableListOf<String>()
        if (content is List<*>) {
            content.filterIsInstance<Map<*, *>>().forEach { part ->
                val type = part["type"] as? String
                if (type == "image_url") {
                    val imgObj = part["image_url"] as? Map<*, *>
                    val url = imgObj?.get("url") as? String
                    if (url != null) result.add(url)
                }
            }
        }
        return result
    }
}

data class OpenAIContentPart(
    @SerializedName("type") val type: String, // "text" or "image_url"
    @SerializedName("text") val text: String? = null,
    @SerializedName("image_url") val imageUrl: OpenAIImageUrl? = null
)

data class OpenAIImageUrl(
    @SerializedName("url") val url: String,
    @SerializedName("detail") val detail: String? = "auto"
)

data class OpenAIChatRequest(
    @SerializedName("model") val model: String = "qwen3.8-max",
    @SerializedName("messages") val messages: List<OpenAIMessage>,
    @SerializedName("stream") val stream: Boolean = false,
    @SerializedName("temperature") val temperature: Double? = null,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    @SerializedName("enable_thinking") val enableThinking: Boolean? = null,
    @SerializedName("thinking_budget") val thinkingBudget: Int? = null,
    @SerializedName("tools") val tools: List<OpenAITool>? = null,
    @SerializedName("tool_choice") val toolChoice: Any? = null
)

data class OpenAITool(
    @SerializedName("type") val type: String = "function",
    @SerializedName("function") val function: OpenAIFunction
)

data class OpenAIFunction(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("parameters") val parameters: Any? = null
)

data class OpenAIToolCall(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String = "function",
    @SerializedName("function") val function: OpenAIFunctionCall
)

data class OpenAIFunctionCall(
    @SerializedName("name") val name: String,
    @SerializedName("arguments") val arguments: String
)

data class OpenAIChatResponse(
    @SerializedName("id") val id: String,
    @SerializedName("object") val objectType: String = "chat.completion",
    @SerializedName("created") val created: Long = System.currentTimeMillis() / 1000,
    @SerializedName("model") val model: String,
    @SerializedName("choices") val choices: List<OpenAIChoice>,
    @SerializedName("usage") val usage: OpenAIUsage? = null
)

data class OpenAIChoice(
    @SerializedName("index") val index: Int = 0,
    @SerializedName("message") val message: OpenAIMessage,
    @SerializedName("finish_reason") val finishReason: String? = "stop"
)

data class OpenAIChatChunkResponse(
    @SerializedName("id") val id: String,
    @SerializedName("object") val objectType: String = "chat.completion.chunk",
    @SerializedName("created") val created: Long = System.currentTimeMillis() / 1000,
    @SerializedName("model") val model: String,
    @SerializedName("choices") val choices: List<OpenAIChunkChoice>
)

data class OpenAIChunkChoice(
    @SerializedName("index") val index: Int = 0,
    @SerializedName("delta") val delta: OpenAIChatDelta,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class OpenAIChatDelta(
    @SerializedName("role") val role: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("reasoning_content") val reasoningContent: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<OpenAIToolCall>? = null
)

data class OpenAIUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int = 0,
    @SerializedName("completion_tokens") val completionTokens: Int = 0,
    @SerializedName("total_tokens") val totalTokens: Int = 0
)

data class OpenAIModel(
    @SerializedName("id") val id: String,
    @SerializedName("object") val objectType: String = "model",
    @SerializedName("created") val created: Long = 1700000000,
    @SerializedName("owned_by") val ownedBy: String = "qwen-bridge"
)

data class OpenAIModelListResponse(
    @SerializedName("object") val objectType: String = "list",
    @SerializedName("data") val data: List<OpenAIModel>
)

// --- Image & Video Generation Models ---

data class OpenAIImageGenerationRequest(
    @SerializedName("prompt") val prompt: String,
    @SerializedName("model") val model: String = "qwen-image",
    @SerializedName("size") val size: String = "1024x1024",
    @SerializedName("n") val n: Int = 1,
    @SerializedName("response_format") val responseFormat: String = "url"
)

data class OpenAIImageGenerationResponse(
    @SerializedName("created") val created: Long = System.currentTimeMillis() / 1000,
    @SerializedName("data") val data: List<OpenAIImageData>
)

data class OpenAIImageData(
    @SerializedName("url") val url: String? = null,
    @SerializedName("b64_json") val b64Json: String? = null,
    @SerializedName("revised_prompt") val revisedPrompt: String? = null
)

data class OpenAIVideoGenerationRequest(
    @SerializedName("prompt") val prompt: String,
    @SerializedName("model") val model: String = "qwen-video",
    @SerializedName("image") val image: String? = null,
    @SerializedName("duration") val duration: Int = 5
)

data class OpenAIVideoGenerationResponse(
    @SerializedName("created") val created: Long = System.currentTimeMillis() / 1000,
    @SerializedName("data") val data: List<OpenAIVideoData>
)

data class OpenAIVideoData(
    @SerializedName("url") val url: String? = null,
    @SerializedName("status") val status: String = "completed"
)

// --- Qwen Web Specific Models ---

data class QwenNewChatRequest(
    @SerializedName("chatId") val chatId: String = "",
    @SerializedName("models") val models: List<String>,
    @SerializedName("chat_type") val chatType: String = "t2t",
    @SerializedName("chat_mode") val chatMode: String = "normal",
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
)

data class QwenNewChatResponse(
    @SerializedName("id") val id: String?,
    @SerializedName("success") val success: Boolean? = true,
    @SerializedName("error") val error: String? = null
)

data class QwenChatRequest(
    @SerializedName("stream") val stream: Boolean = true,
    @SerializedName("version") val version: String = "2.1",
    @SerializedName("incremental_output") val incrementalOutput: Boolean = true,
    @SerializedName("chatId") val chatId: String,
    @SerializedName("chat_id") val chat_id: String,
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<QwenMessage>
)

data class QwenMessage(
    @SerializedName("fid") val fid: String,
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String,
    @SerializedName("feature_config") val featureConfig: QwenFeatureConfig? = null,
    @SerializedName("files") val files: List<QwenFileAttachment>? = null
)

data class QwenFileAttachment(
    @SerializedName("type") val type: String, // "image" or "file"
    @SerializedName("url") val url: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("size") val size: Long? = null
)

data class QwenFeatureConfig(
    @SerializedName("thinking_enabled") val thinkingEnabled: Boolean = true,
    @SerializedName("output_schema") val outputSchema: String = "phase",
    @SerializedName("auto_thinking") val autoThinking: Boolean = true,
    @SerializedName("thinking_mode") val thinkingMode: String = "Auto",
    @SerializedName("thinking_format") val thinkingFormat: String = "summary",
    @SerializedName("auto_search") val autoSearch: Boolean = false
)

// --- App Config & Telemetry ---

data class AppConfig(
    val port: Int = 8787,
    val token: String = "",
    val upstreamEndpoint: String = "https://chat.qwen.ai",
    val defaultModel: String = "qwen3.8-max",
    val enableThinking: Boolean = true,
    val autoStartOnBoot: Boolean = true,
    val keepAliveMediaSession: Boolean = true,
    val keepAliveWakeLock: Boolean = true,
    val floatingOverlay: Boolean = true,
    val totalRequests: Long = 0,
    val lastChallengeSolvedTime: Long = 0
)

data class LogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val method: String,
    val path: String,
    val statusCode: Int,
    val durationMs: Long,
    val isChallengeTriggered: Boolean = false,
    val details: String = "",
    val requestHeaders: Map<String, String>? = null,
    val requestBody: String? = null,
    val responseHeaders: Map<String, String>? = null,
    val responseBody: String? = null
)
