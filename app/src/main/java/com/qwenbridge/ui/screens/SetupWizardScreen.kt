package com.qwenbridge.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import com.qwenbridge.service.OEMHelper
import com.qwenbridge.ui.theme.*

@Composable
fun SetupWizardScreen() {
    val context = LocalContext.current
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasBattery by remember { mutableStateOf(OEMHelper.isIgnoringBatteryOptimizations(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Keep-Alive & Security Setup",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Configure system permissions to ensure the background API proxy remains active and can solve security challenges seamlessly.",
            fontSize = 12.sp,
            color = TextSecondary
        )

        // Step 1: Display Over Other Apps (Overlay)
        PermissionStepCard(
            stepNumber = 1,
            title = "Display Over Other Apps (Overlay)",
            desc = "Allows the app to display a floating verification window when Cloudflare or Alibaba WAF triggers a bot challenge.",
            isGranted = hasOverlay,
            actionLabel = "Grant Overlay Permission",
            onAction = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
            }
        )

        // Step 2: Battery Optimization Whitelist
        PermissionStepCard(
            stepNumber = 2,
            title = "Ignore Battery Optimizations (Doze)",
            desc = "Prevents Android from putting the background proxy server to sleep when the screen is locked.",
            isGranted = hasBattery,
            actionLabel = "Whitelist from Battery Saver",
            onAction = {
                OEMHelper.requestIgnoreBatteryOptimizations(context)
            }
        )

        // Step 3: OEM-Specific AutoStart (Xiaomi, Samsung, Huawei)
        PermissionStepCard(
            stepNumber = 3,
            title = "OEM AutoStart & Background Execution",
            desc = "Opens manufacturer security settings (${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }}) to enable AutoStart and allow unlimited background work.",
            isGranted = false,
            actionLabel = "Open ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} Settings",
            onAction = {
                OEMHelper.openAutoStartSettings(context)
            }
        )

        // Step 4: Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionStepCard(
                stepNumber = 4,
                title = "Notification Permission",
                desc = "Required to show persistent Foreground Service status and keep the process alive in low-memory situations.",
                isGranted = true,
                actionLabel = "Check Notifications",
                onAction = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun PermissionStepCard(
    stepNumber: Int,
    title: String,
    desc: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (isGranted) SecondaryEmerald else PrimaryIndigo),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$stepNumber",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                }

                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Granted",
                        tint = SecondaryEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = desc,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )

            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) SurfaceCard else PrimaryIndigo
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isGranted) "Reconfigure" else actionLabel,
                    fontSize = 13.sp
                )
            }
        }
    }
}
