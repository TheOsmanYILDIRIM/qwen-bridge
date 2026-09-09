package com.qwenbridge.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qwenbridge.data.ConfigManager
import com.qwenbridge.data.OpenAIImageGenerationRequest
import com.qwenbridge.data.OpenAIVideoGenerationRequest
import com.qwenbridge.proxy.QwenApiClient
import com.qwenbridge.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MediaStudioScreen(configManager: ConfigManager) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val config by configManager.config.collectAsState()
    val apiClient = remember { QwenApiClient(context) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Image, 1: Video
    var promptInput by remember { mutableStateOf("") }
    var selectedSize by remember { mutableStateOf("1024x1024") }
    var isGenerating by remember { mutableStateOf(false) }
    var generatedImageUrl by remember { mutableStateOf<String?>(null) }
    var generatedVideoUrl by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "AI Media Studio",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Generate high-quality images (Qwen-Image) and videos (Qwen-Video) directly.",
            fontSize = 12.sp,
            color = TextSecondary
        )

        // Tabs (Image / Video)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SurfaceDark,
            contentColor = PrimaryIndigo
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("🎨 Image Generator") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("🎬 Video Generator") }
            )
        }

        // Input Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    label = { Text(if (selectedTab == 0) "Image Prompt" else "Video Prompt") },
                    placeholder = {
                        Text(if (selectedTab == 0) "A futuristic cyberpunk city with neon lights 4k..." else "A serene drone shot over mountains at sunset...")
                    },
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

                if (selectedTab == 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("1024x1024", "768x1024", "1024x768").forEach { size ->
                            FilterChip(
                                selected = selectedSize == size,
                                onClick = { selectedSize = size },
                                label = { Text(size, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryIndigo,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        if (config.token.isEmpty()) {
                            Toast.makeText(context, "Please configure Qwen Token first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (promptInput.isEmpty()) {
                            Toast.makeText(context, "Please enter a prompt", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        coroutineScope.launch {
                            isGenerating = true
                            try {
                                if (selectedTab == 0) {
                                    val resp = apiClient.generateImage(
                                        OpenAIImageGenerationRequest(prompt = promptInput, size = selectedSize),
                                        config.token
                                    )
                                    val url = resp.data.firstOrNull()?.url
                                    generatedImageUrl = url
                                    Toast.makeText(context, "Image generated and saved to Documents/Qwen!", Toast.LENGTH_LONG).show()
                                } else {
                                    val resp = apiClient.generateVideo(
                                        OpenAIVideoGenerationRequest(prompt = promptInput),
                                        config.token
                                    )
                                    generatedVideoUrl = resp.data.firstOrNull()?.url
                                    Toast.makeText(context, "Video generated successfully!", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isGenerating = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isGenerating
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Generating with Qwen AI...")
                    } else {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Default.AutoFixHigh else Icons.Default.Videocam,
                            contentDescription = "Generate",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (selectedTab == 0) "Generate Image" else "Generate Video")
                    }
                }
            }
        }

        // Result Card
        if (generatedImageUrl != null || generatedVideoUrl != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "✨ Generation Result",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )

                    val activeUrl = generatedImageUrl ?: generatedVideoUrl ?: ""
                    Surface(
                        color = BgDark,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "URL: $activeUrl\n📁 Auto-saved to: /storage/emulated/0/Documents/Qwen/",
                            fontSize = 11.sp,
                            color = SecondaryEmerald,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}
