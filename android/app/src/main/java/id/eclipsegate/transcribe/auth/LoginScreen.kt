package id.eclipsegate.transcribe.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Design Tokens (Monochrome Utility Palette)
private val OnyxBackground = Color(0xFF0A0D12)
private val DarkSlateCard = Color(0xFF121720)
private val SteelBorder = Color(0xFF283342)
private val TextWhite = Color(0xFFF0F4F8)
private val TextMuted = Color(0xFF8C9BAE)
private val ErrorContainer = Color(0xFF2A0F12)
private val ErrorBorder = Color(0xFF7F1D1D)
private val ErrorText = Color(0xFFFCA5A5)
private val AccentPrimary = Color(0xFF38BDF8)

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onGoogleSignInClicked: () -> Unit,
    onAuthSuccess: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) {
            onAuthSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnyxBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SteelBorder, RoundedCornerShape(10.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSlateCard),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Application Title
                Text(
                    text = "TRANSCRIBE CORE",
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Sistem transkripsi dan intelijen rapat berbasis AI.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Error Banner
                if (state.errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ErrorContainer, RoundedCornerShape(6.dp))
                            .border(1.dp, ErrorBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = state.errorMessage ?: "",
                            color = ErrorText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Google Sign In Button (Minimalist, High Contrast)
                Button(
                    onClick = onGoogleSignInClicked,
                    enabled = !state.isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPrimary,
                        disabledContainerColor = AccentPrimary.copy(alpha = 0.5f),
                        contentColor = OnyxBackground
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = OnyxBackground,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Lanjutkan dengan Google",
                            color = OnyxBackground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }
        }
    }
}
