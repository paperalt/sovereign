package org.sovereign.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val OnyxBlack = Color(0xFF0A0D12)
private val DarkSlate = Color(0xFF141A22)
private val CardBackground = Color(0xFF0F151E)
private val SteelBorder = Color(0xFF283342)
private val TextPrimary = Color(0xFFF0F4F8)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF64748B)
private val AccentPrimary = Color(0xFF38BDF8)
private val EmeraldSuccess = Color(0xFF10B981)

@Composable
fun SystemInfoDialog(
    currentVersionName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .heightIn(max = 660.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = OnyxBlack),
                border = BorderStroke(1.dp, SteelBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ABOUT SOVEREIGN",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Private On-Device Speech Intelligence",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }

                    HorizontalDivider(color = SteelBorder.copy(alpha = 0.5f))

                    // 1. App Identity Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, SteelBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("RELEASE IDENTITY", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Box(
                                    modifier = Modifier
                                        .background(EmeraldSuccess.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .border(1.dp, EmeraldSuccess, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("OPEN SOURCE", color = EmeraldSuccess, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }

                            HorizontalDivider(color = SteelBorder.copy(alpha = 0.4f))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("APP NAME", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Text("Sovereign", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("VERSION", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Text("v$currentVersionName", color = AccentPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("PACKAGE", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Text("org.sovereign.app", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("AUTHOR", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Text("@paperalt", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkSlate, RoundedCornerShape(6.dp))
                                    .clickable {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/paperalt/sovereign"))
                                        context.startActivity(intent)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("REPOSITORY", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Text("github.com/paperalt/sovereign", color = AccentPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    // 2. Product Capabilities
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, SteelBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("CAPABILITIES", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            HorizontalDivider(color = SteelBorder.copy(alpha = 0.4f))

                            ArchitectureSpecItem(
                                label = "Speech-to-Text",
                                value = "Live streaming voice recognition with pause & resume"
                            )
                            ArchitectureSpecItem(
                                label = "Reasoning & Synthesis",
                                value = "Executive summaries, key takeaways, and action items"
                            )
                            ArchitectureSpecItem(
                                label = "In-Meeting Inquiry",
                                value = "Context-grounded analytical questions during live discussions"
                            )
                            ArchitectureSpecItem(
                                label = "Model Flexibility",
                                value = "Bring-your-own-key or local inference (Ollama, Groq, Google, OpenAI)"
                            )
                        }
                    }

                    // 3. Privacy & Security
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, SteelBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("PRIVACY & SECURITY", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            HorizontalDivider(color = SteelBorder.copy(alpha = 0.4f))

                            SecurityBulletItem(
                                title = "Hardware Keystore Protection",
                                desc = "API keys are encrypted using hardware-backed AES-256-GCM."
                            )
                            SecurityBulletItem(
                                title = "Direct Provider Connectivity",
                                desc = "Audio and requests go directly to your configured AI endpoints with no third-party proxies."
                            )
                            SecurityBulletItem(
                                title = "Complete Ownership",
                                desc = "All session recordings, notes, and transcripts remain exclusively on your device."
                            )
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                    ) {
                        Text("CLOSE", color = OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
fun GuideDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .heightIn(max = 660.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = OnyxBlack),
                border = BorderStroke(1.dp, SteelBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "USER GUIDE & MANUAL",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Endpoints, API Keys, and Operations",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }

                    HorizontalDivider(color = SteelBorder.copy(alpha = 0.5f))

                    GuideItem(
                        title = "1. Obtaining Free Tier API Keys",
                        description = "• Groq Cloud (Recommended Default STT & LLM):\n  1. Go to console.groq.com and sign up for free (no credit card required).\n  2. Create an API Key in the 'API Keys' tab.\n  3. Generous free tier: 30 RPM, 14,400 requests/day with ultra-fast Whisper Large V3 Turbo and Llama 3.3 70B.\n\n• Google AI Studio (Gemini 2.0 Flash):\n  1. Visit aistudio.google.com/apikey.\n  2. Generates free API key with 1,000,000 token context window.\n\n• OpenRouter (Free Tier):\n  1. Visit openrouter.ai/keys to generate an API key.\n  2. Select models with ':free' suffix to use free subsidized models."
                    )

                    GuideItem(
                        title = "2. Setting Up STT & LLM via Hamburger Menu",
                        description = "• Voice (STT) Endpoints:\n  Tap Menu -> 'Voice (STT) Endpoints' to add, configure, or test speech-to-text endpoints. Set default model, endpoint URL, and API key.\n\n• Reasoning (LLM) Endpoints:\n  Tap Menu -> 'Reasoning (LLM) Endpoints' to configure intelligence models. Tap '[Detect /models]' to automatically fetch all live models from the provider."
                    )

                    GuideItem(
                        title = "3. AI Engine Pipeline: Combine & Presets",
                        description = "• Tap Menu -> 'AI Engine Pipeline' to combine any STT with any LLM.\n• Tap '[Test Pipeline Latency]' to run a live diagnostic ping on both endpoints.\n• Save your favorite combination as a Preset for 1-tap switching."
                    )

                    GuideItem(
                        title = "4. Offline & Local Network Inference (Ollama)",
                        description = "• Run Ollama on your PC/Mac or home server:\n  ollama run whisper\n  ollama run llama3.2\n• Set endpoint to your machine's LAN IP (or http://10.0.2.2:11434 in Android emulator).\n• API key is completely optional for local endpoints."
                    )

                    GuideItem(
                        title = "5. Live Recording & In-Meeting Inquiry",
                        description = "• Background Service: Audio recording runs smoothly in the background with CPU WakeLock protection.\n• Always On Display: Toggle [ AOD ON ] on the recording screen to keep the screen active on your desk during meetings.\n• In-Meeting Inquiry: Tap [ INQUIRY ] during a live discussion to formulate 3 targeted critical questions based on recent speaker statements without stopping the recording."
                    )

                    GuideItem(
                        title = "6. Data Privacy & On-Device Storage",
                        description = "• All audio recordings, transcripts, summaries, and meeting groups remain securely stored on your device.\n• No intermediate server or third party ever receives your data."
                    )

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                    ) {
                        Text("CLOSE", color = OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchitectureSpecItem(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSlate, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            text = label.uppercase(),
            color = AccentPrimary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun SecurityBulletItem(title: String, desc: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSlate, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = desc,
            color = TextSecondary,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun GuideItem(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, SteelBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                color = AccentPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}
