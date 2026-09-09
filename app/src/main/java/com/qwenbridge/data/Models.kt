package com.qwenbridge.data

import com.google.gson.annotations.SerializedName

// --- OpenAI API Models ---

data class OpenAIMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<OpenAIToolCall>? = null,
    @SerializedName("tool_call_id") val toolCallId: String? = null
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
    @SerializedName("feature_config") val featureConfig: QwenFeatureConfig? = null
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
    val details: String = ""
)
