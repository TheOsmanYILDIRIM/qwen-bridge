package com.qwenbridge.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qwenbridge.data.*
import com.qwenbridge.proxy.QwenApiClient
import com.qwenbridge.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun ChatPlaygroundScreen(configManager: ConfigManager) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val config by configManager.config.collectAsState()
    val apiClient = remember { QwenApiClient(context) }

    var promptInput by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var attachedBase64 by remember { mutableStateOf<String?>(null) }
    var attachedFileName by remember { mutableStateOf<String?>(null) }

    var isGenerating by remember { mutableStateOf(false) }
    var aiResponse by remember { mutableStateOf("") }
    var thinkingContent by remember { mutableStateOf("") }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            attachedFileName = "Attached File (${it.lastPathSegment?.takeLast(16)})"
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        val bytes = stream.readBytes()
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val mime = context.contentResolver.getType(it) ?: "image/jpeg"
                        attachedBase64 = "data:$mime;base64,$b64"
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to read attachment", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Multimodal Chat Playground",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Test direct vision, images, documents, and code reasoning on Qwen.",
            fontSize = 12.sp,
            color = TextSecondary
        )

        // Attachment Preview Bar if selected
        if (attachedFileName != null) {
            Surface(
                color = SurfaceDark,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AttachFile, contentDescription = "Attachment", tint = PrimaryIndigo, modifier = Modifier.size(16.dp))
                        Text(text = attachedFileName ?: "", color = TextPrimary, fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = {
                            selectedImageUri = null
                            attachedBase64 = null
                            attachedFileName = null
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Remove", tint = TextSecondary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Prompt Input & Action Dock
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = { Text("Ask a question, analyze image, or paste code...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryIndigo,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = "Attach", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add File / Photo", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (config.token.isEmpty()) {
                                Toast.makeText(context, "Please set Qwen token in Token tab", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (promptInput.isEmpty() && attachedBase64 == null) {
                                Toast.makeText(context, "Please enter a message or attach a file", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            coroutineScope.launch {
                                isGenerating = true
                                aiResponse = ""
                                thinkingContent = ""

                                val contentList = mutableListOf<Map<String, Any>>()
                                if (promptInput.isNotEmpty()) {
                                    contentList.add(mapOf("type" to "text", "text" to promptInput))
                                }
                                attachedBase64?.let { b64 ->
                                    contentList.add(mapOf("type" to "image_url", "image_url" to mapOf("url" to b64)))
                                }

                                val messageContent: Any = if (attachedBase64 != null) contentList else promptInput
                                val request = OpenAIChatRequest(
                                    model = config.defaultModel,
                                    messages = listOf(OpenAIMessage(role = "user", content = messageContent)),
                                    stream = true
                                )

                                val output = ByteArrayOutputStream()
                                try {
                                    apiClient.streamChatCompletion(request, config.token, output)
                                    aiResponse = output.toString(Charsets.UTF_8.name())
                                        .lines()
                                        .filter { it.startsWith("data:") && !it.contains("[DONE]") }
                                        .joinToString("") { line ->
                                            try {
                                                val json = com.google.gson.Gson().fromJson(line.removePrefix("data:").trim(), com.google.gson.JsonObject::class.java)
                                                val delta = json.getAsJsonArray("choices")?.get(0)?.asJsonObject?.getAsJsonObject("delta")
                                                delta?.get("content")?.asString ?: ""
                                            } catch (e: Exception) { "" }
                                        }
                                } catch (e: Exception) {
                                    aiResponse = "Error: ${e.message}"
                                } finally {
                                    isGenerating = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isGenerating
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send")
                        }
                    }
                }
            }
        }

        // Response Bubble
        if (aiResponse.isNotEmpty() || isGenerating) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🤖 Qwen Response",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = PrimaryIndigo
                        )

                        if (aiResponse.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Qwen Response", aiResponse))
                                    Toast.makeText(context, "Response copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Surface(
                        color = BgDark,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (aiResponse.isEmpty() && isGenerating) "Thinking & generating response..." else aiResponse,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}
