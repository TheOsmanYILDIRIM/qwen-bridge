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
import com.qwenbridge.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(configManager: ConfigManager) {
    val context = LocalContext.current
    val config by configManager.config.collectAsState()

    var portInput by remember { mutableStateOf(config.port.toString()) }
    var selectedModel by remember { mutableStateOf(config.defaultModel) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    val modelsList = listOf(
        "qwen3.8-max",
        "qwen3.8-max-preview",
        "qwen3.7-max",
        "qwen3.7-plus",
        "qwen3.6-plus",
        "qwen3.6-27b",
        "qwen3.5-plus",
        "qwen3.5-flash",
        "qwen3-coder-plus",
        "qwen-image",
        "qwen-video",
        "qwen-deep-research"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings & Tuning",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        // Network Settings Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "🌐 Network & Server",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it },
                        label = { Text("Local Proxy Port") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            val p = portInput.toIntOrNull()
                            if (p != null && p in 1024..65535) {
                                configManager.updatePort(p)
                                Toast.makeText(context, "Port saved: $p", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Invalid port (1024-65535)", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save")
                    }
                }

                // Default Model Dropdown
                ExposedDropdownMenuBox(
                    expanded = modelDropdownExpanded,
                    onExpandedChange = { modelDropdownExpanded = !modelDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Default Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = modelDropdownExpanded,
                        onDismissRequest = { modelDropdownExpanded = false },
                        modifier = Modifier.background(SurfaceDark)
                    ) {
                        modelsList.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model, color = TextPrimary) },
                                onClick = {
                                    selectedModel = model
                                    configManager.updateDefaultModel(model)
                                    modelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Keep-Alive & Thermal Protection Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "🛡️ Background Keep-Alive Tricks",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )

                SettingToggleRow(
                    title = "MediaSession Anti-Kill Trick",
                    desc = "Signals Android media system to prevent task cleaners from killing background proxy.",
                    checked = config.keepAliveMediaSession,
                    onCheckedChange = {
                        configManager.updateKeepAlive(it, config.keepAliveWakeLock)
                    }
                )

                HorizontalDivider(color = BorderSubtle)

                SettingToggleRow(
                    title = "Partial WakeLock",
                    desc = "Keeps CPU active during long thinking queries when phone screen is locked.",
                    checked = config.keepAliveWakeLock,
                    onCheckedChange = {
                        configManager.updateKeepAlive(config.keepAliveMediaSession, it)
                    }
                )

                HorizontalDivider(color = BorderSubtle)

                SettingToggleRow(
                    title = "Auto-Start on Boot",
                    desc = "Starts the foreground proxy service automatically when phone restarts.",
                    checked = config.autoStartOnBoot,
                    onCheckedChange = {
                        configManager.updateAutoStart(it)
                    }
                )

                HorizontalDivider(color = BorderSubtle)

                SettingToggleRow(
                    title = "Reasoning / Thinking Mode",
                    desc = "Enables step-by-step thinking for complex coding and mathematical prompts.",
                    checked = config.enableThinking,
                    onCheckedChange = {
                        configManager.updateThinking(it)
                    }
                )
            }
        }
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = TextPrimary
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryIndigo,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = SurfaceCard
            )
        )
    }
}
