package id.eclipsegate.transcribe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = OnyxBlack)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SteelBorder, RoundedCornerShape(10.dp))
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "INFORMASI SISTEM",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Arsitektur Transcribe Core v$currentVersionName",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Server Status Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBackground, RoundedCornerShape(6.dp))
                        .border(1.dp, SteelBorder, RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "INFRASTRUKTUR UTAMA",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(EmeraldSuccess, RoundedCornerShape(3.dp))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ONLINE",
                                    color = EmeraldSuccess,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        InfoRow(label = "Endpoint Domain", value = "gate.eclipsegate.my.id")
                        InfoRow(label = "Protokol", value = "HTTPS / WSS (TLS 1.3 Let's Encrypt)")
                        InfoRow(label = "Audio Streaming", value = "PCM 16kHz Mono -> In-Memory WAV")
                        InfoRow(label = "VAD Chunker", value = "RMS Silence Split (5s - 25s)")
                        InfoRow(label = "Basis Data", value = "PostgreSQL 16 (GIN + B-Tree)")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Security & Privacy Specs
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBackground, RoundedCornerShape(6.dp))
                        .border(1.dp, SteelBorder, RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "KEAMANAN & PRIVASI",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "• Kunci pribadi API disimpan di Android Keystore (AES-256-GCM).\n• Zero-knowledge server: API Key pribadi tidak pernah disimpan ke SSD server.\n• Token sesi menggunakan JWT dual-token dengan Argon2id hash.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2632))
                ) {
                    Text(text = "TUTUP", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun UserGuideDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = OnyxBlack)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SteelBorder, RoundedCornerShape(10.dp))
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PANDUAN & BANTUAN",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Tips Optimal Transcribe Core",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Guide 1: How to get Groq Key
                GuideCard(
                    title = "1. Cara Mendapatkan API Key Groq (Gratis)",
                    description = "1. Buka situs console.groq.com di browser.\n2. Login dengan akun Google atau GitHub Anda.\n3. Masuk ke menu 'API Keys' lalu klik 'Create API Key'.\n4. Salin kunci (diawali 'gsk_') dan masukkan ke menu Konfigurasi Mesin AI di aplikasi.\n5. Anda mendapatkan kuota gratis 8 jam audio per hari dengan kecepatan transkripsi 0,3 detik!"
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Guide 2: How to get Gemini Key
                GuideCard(
                    title = "2. Cara Mendapatkan API Key Google Gemini",
                    description = "1. Buka aistudio.google.com/apikey di browser.\n2. Klik 'Create API key' dan pilih proyek Google Cloud Anda.\n3. Salin kunci (diawali 'AIza') dan masukkan ke aplikasi.\n4. Nikmati transkripsi audio dengan context window raksasa 1 juta token bebas kuota server."
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Guide 3: Background & AOD
                GuideCard(
                    title = "3. Perekaman di Latar Belakang (Background)",
                    description = "• Aplikasi tetap merekam saat layar dimatikan atau saat membuka aplikasi lain berkat CPU WakeLock dan Foreground Service.\n• Jika ingin layar tetap menyala di atas meja saat kuliah/rapat, aktifkan tombol [ AOD ON ] di pojok kanan atas layar transkripsi."
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Guide 4: Question suggestions
                GuideCard(
                    title = "4. Fitur Ide Tanya Instan",
                    description = "Saat sesi berlangsung, ketuk tombol [ IDE TANYA ] untuk mendapatkan 3 rekomendasi pertanyaan kritis berbasis topik yang baru saja dibahas dosen/pembicara tanpa menunggu sesi selesai."
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2632))
                ) {
                    Text(text = "MENGERTI", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(text = label, color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(text = value, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GuideCard(title: String, description: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(6.dp))
            .border(1.dp, SteelBorder, RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(text = title, color = AccentPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}
