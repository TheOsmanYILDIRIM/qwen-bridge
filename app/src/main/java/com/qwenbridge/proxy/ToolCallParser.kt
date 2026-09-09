package com.qwenbridge.proxy

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.qwenbridge.data.OpenAIFunctionCall
import com.qwenbridge.data.OpenAITool
import com.qwenbridge.data.OpenAIToolCall
import java.util.UUID
import java.util.regex.Pattern

object ToolCallParser {

    private val gson = Gson()
    private val TOOL_CALL_PATTERN = Pattern.compile("<tool_call>([\\s\\S]*?)</tool_call>", Pattern.CASE_INSENSITIVE)
    private val CODE_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*\\{\\s*\"(?:tool_call|function|name)\"[\\s\\S]*?\\}\\s*```", Pattern.CASE_INSENSITIVE)

    fun buildToolSystemPrompt(tools: List<OpenAITool>): String {
        val toolsJson = gson.toJson(tools)
        return """
            # Tools Available
            You have access to the following tools:
            $toolsJson

            # Tool Calling Instructions
            When you need to call a tool, you MUST format your call using the following XML tag:
            <tool_call>
            {"name": "tool_name", "arguments": {"param1": "value1"}}
            </tool_call>

            Do not add conversational fluff before the tool call if an action is required immediately.
        """.trimIndent()
    }

    fun extractToolCalls(rawContent: String): Pair<String, List<OpenAIToolCall>?> {
        val toolCalls = mutableListOf<OpenAIToolCall>()
        var cleanedContent = rawContent

        val matcher = TOOL_CALL_PATTERN.matcher(rawContent)
        while (matcher.find()) {
            val jsonContent = matcher.group(1)?.trim() ?: ""
            try {
                val parsed = gson.fromJson(jsonContent, JsonObject::class.java)
                val name = parsed.get("name")?.asString ?: ""
                val argsElement = parsed.get("arguments")
                val argsString = if (argsElement != null && argsElement.isJsonObject) {
                    gson.toJson(argsElement)
                } else if (argsElement != null && argsElement.isJsonPrimitive) {
                    argsElement.asString
                } else {
                    "{}"
                }

                if (name.isNotEmpty()) {
                    toolCalls.add(
                        OpenAIToolCall(
                            id = "call_" + UUID.randomUUID().toString().replace("-", "").take(16),
                            type = "function",
                            function = OpenAIFunctionCall(name = name, arguments = argsString)
                        )
                    )
                }
            } catch (e: Exception) {
                // Ignore malformed tool JSON
            }
        }

        if (toolCalls.isNotEmpty()) {
            cleanedContent = matcher.replaceAll("").trim()
            return Pair(cleanedContent, toolCalls)
        }

        return Pair(rawContent, null)
    }
}
