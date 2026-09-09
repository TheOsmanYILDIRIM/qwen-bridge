package com.qwenbridge.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qwenbridge.data.ConfigManager
import com.qwenbridge.service.OEMHelper
import com.qwenbridge.service.QwenBridgeService
import com.qwenbridge.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    configManager: ConfigManager,
    onNavigateToSetup: () -> Unit,
    onNavigateToToken: () -> Unit
) {
    val context = LocalContext.current
    val config by configManager.config.collectAsState()
    val isRunning by configManager.isServiceRunning.collectAsState()
    val isChallengeActive by configManager.isChallengeActive.collectAsState()

    val hasOverlay = Settings.canDrawOverlays(context)
    val hasBattery = OEMHelper.isIgnoringBatteryOptimizations(context)
    val hasToken = config.token.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Qwen Bridge",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Local OpenAI API Proxy & Challenge Solver",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            IconButton(onClick = onNavigateToToken) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = "Token",
                    tint = if (hasToken) SecondaryEmerald else ErrorRose
                )
            }
        }

        // Active Challenge Banner
        if (isChallengeActive) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AccentAmber.copy(alpha = 0.15f)),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(AccentAmber, PrimaryIndigo))),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = AccentAmber,
                        strokeWidth = 3.dp
                    )
                    Column {
                        Text(
                            text = "Security Verification in Progress",
                            fontWeight = FontWeight.SemiBold,
                            color = AccentAmber,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Check your screen overlay to complete verification.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Setup Alert Banner if permissions missing
        if (!hasOverlay || !hasBattery) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(PrimaryIndigo, SecondaryEmerald))),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToSetup
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "Alert",
                        tint = AccentAmber,
                        modifier = Modifier.size(32.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Background Permissions Needed",
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Tap to grant Overlay & Battery whitelist to stay alive.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open Setup",
                        tint = TextSecondary
                    )
                }
            }
        }

        // Server Status Hero Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) SecondaryEmerald else ErrorRose)
                        )
                        Text(
                            text = if (isRunning) "PROXY ACTIVE" else "PROXY STOPPED",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isRunning) SecondaryEmerald else ErrorRose,
                            letterSpacing = 1.sp
                        )
                    }

                    Switch(
                        checked = isRunning,
                        onCheckedChange = { start ->
                            if (start) {
                                QwenBridgeService.start(context)
                            } else {
                                QwenBridgeService.stop(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryIndigo,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = SurfaceCard
                        )
                    )
                }

                // Endpoint Copy Card
                val endpointUrl = "http://127.0.0.1:${config.port}/v1"
                Surface(
                    color = BgDark,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Base URL",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                            Text(
                                text = endpointUrl,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = PrimaryIndigo,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("QwenBridge URL", endpointUrl))
                                Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Stats Matrix
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Total Requests",
                value = "${config.totalRequests}",
                icon = Icons.Default.Http,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Active Model",
                value = config.defaultModel.take(12),
                icon = Icons.Default.SmartToy,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val lastSolvedStr = if (config.lastChallengeSolvedTime > 0) {
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(config.lastChallengeSolvedTime))
            } else {
                "None"
            }
            StatCard(
                title = "Last Solved",
                value = lastSolvedStr,
                icon = Icons.Default.Shield,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Keep-Alive",
                value = if (config.keepAliveMediaSession) "Armed" else "Standard",
                icon = Icons.Default.ElectricBolt,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = PrimaryIndigo,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}
