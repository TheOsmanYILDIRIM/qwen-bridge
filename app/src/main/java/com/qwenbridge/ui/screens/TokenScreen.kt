package com.qwenbridge.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.qwenbridge.data.ConfigManager
import com.qwenbridge.proxy.QwenApiClient
import com.qwenbridge.ui.theme.*
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TokenScreen(configManager: ConfigManager) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val config by configManager.config.collectAsState()
    val apiClient = remember { QwenApiClient(context) }

    var tokenInput by remember { mutableStateOf(config.token) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isValidating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showEmbeddedLogin by remember { mutableStateOf(false) }

    LaunchedEffect(config.token) {
        tokenInput = config.token
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Token Management",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Qwen Access Token (JWT Bearer) for upstream authentication.",
            fontSize = 12.sp,
            color = TextSecondary
        )

        // Token Input Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("Qwen Access Token") },
                    placeholder = { Text("eyJhbGciOiJIUzI1NiIsInR5c...") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Visibility",
                                    tint = TextSecondary
                                )
                            }
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    tokenInput = clip.getItemAt(0).text.toString().trim()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = PrimaryIndigo
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryIndigo,
                        unfocusedBorderColor = BorderSubtle,
                        focusedLabelColor = PrimaryIndigo,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            configManager.updateToken(tokenInput)
                            Toast.makeText(context, "Token saved securely", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Token")
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isValidating = true
                                validationResult = apiClient.validateToken(tokenInput)
                                isValidating = false
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        enabled = !isValidating && tokenInput.isNotEmpty()
                    ) {
                        if (isValidating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Validate", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Validate")
                        }
                    }
                }

                // Validation Feedback
                validationResult?.let { (valid, msg) ->
                    Surface(
                        color = if (valid) SecondaryEmerald.copy(alpha = 0.15f) else ErrorRose.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (valid) Icons.Default.Check else Icons.Default.ErrorOutline,
                                contentDescription = "Status",
                                tint = if (valid) SecondaryEmerald else ErrorRose,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = msg,
                                color = if (valid) SecondaryEmerald else ErrorRose,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Web Login Helper Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🌐 Auto-Extract from Web Login",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "Open Qwen Web Login in embedded view. When you log in, the token will be automatically captured from localStorage and saved.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Button(
                    onClick = { showEmbeddedLogin = !showEmbeddedLogin },
                    colors = ButtonDefaults.buttonColors(containerColor = if (showEmbeddedLogin) SurfaceCard else PrimaryIndigoDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (showEmbeddedLogin) Icons.Default.Close else Icons.Default.OpenInBrowser,
                        contentDescription = "Login",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (showEmbeddedLogin) "Hide Web Login" else "Open Qwen Web Login")
                }

                if (showEmbeddedLogin) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(450.dp)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = true
                                    // DanyAPI'nin çalışan UA — Android UA WAF tarafından bloklanıyor
                                    settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"

                                    addJavascriptInterface(object {
                                        @JavascriptInterface
                                        fun onTokenFound(token: String) {
                                            if (token.isNotEmpty()) {
                                                configManager.updateToken(token)
                                                post {
                                                    tokenInput = token
                                                    showEmbeddedLogin = false
                                                    Toast.makeText(ctx, "✅ Token otomatik yakalandı!", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    }, "TokenExtractor")

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)

                                            // Cookie'leri OkHttp CookieJar'a aktar (bot challenge sonrası)
                                            val cookies = android.webkit.CookieManager.getInstance()
                                                .getCookie("https://chat.qwen.ai") ?: ""
                                            if (cookies.isNotEmpty()) {
                                                com.qwenbridge.challenge.CookieSessionManager.getInstance()
                                                    .setCookie("https://chat.qwen.ai", cookies)
                                            }

                                            // Token yakalama: localStorage + cookie ikisinden birinden al
                                            val js = """
                                                (function() {
                                                    // 1. localStorage'dan dene
                                                    let t = localStorage.getItem('token');
                                                    
                                                    // 2. Cookie'den dene
                                                    if (!t) {
                                                        const m = document.cookie.match(/(?:^|;\\s*)token=([^;]+)/);
                                                        if (m) t = m[1];
                                                    }
                                                    
                                                    // 3. Authorization header'dan dene (Redux store)
                                                    if (!t && window.__store__) {
                                                        try {
                                                            const state = window.__store__.getState();
                                                            t = state?.auth?.token || state?.user?.token;
                                                        } catch(e) {}
                                                    }
                                                    
                                                    if (t && t.length > 20) {
                                                        TokenExtractor.onTokenFound(t);
                                                    }
                                                })();
                                            """.trimIndent()
                                            evaluateJavascript(js, null)
                                        }
                                    }
                                    loadUrl("https://chat.qwen.ai")
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

            }
        }
    }
}
