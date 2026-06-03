package com.echo.loomi.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echo.loomi.desktop.network.OAuthServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun LoginScreen(
    onLoginSuccess: (idToken: String, uid: String, name: String, email: String, photoUrl: String) -> Unit
) {
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var testUserName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Smooth background gradient colors
    val gradientColors = listOf(
        Color(0xFF7E57C2), Color(0xFFEF5350), Color(0xFFFFEE58),
        Color(0xFF5C6BC0), Color(0xFF66BB6A)
    )

    var colorIndex1 by remember { mutableStateOf(0) }
    var colorIndex2 by remember { mutableStateOf(1) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            colorIndex1 = (colorIndex1 + 1) % gradientColors.size
            colorIndex2 = (colorIndex2 + 1) % gradientColors.size
        }
    }

    val animatedColor1 by animateColorAsState(gradientColors[colorIndex1], tween(3000))
    val animatedColor2 by animateColorAsState(gradientColors[colorIndex2], tween(3000))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(animatedColor1, animatedColor2)))
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .width(420.dp)
                .padding(24.dp)
                .border(1.dp, Color.White.copy(0.15f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 36.dp, horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App Logo
                Image(
                    painter = painterResource("drawable/logo.xml"),
                    contentDescription = "Loomi Logo",
                    modifier = Modifier.height(56.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Experience seamless encrypted chats",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(40.dp))

                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Authenticating secure session...",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                } else {
                    // Google Auth Button
                    Button(
                        onClick = {
                            isLoading = true
                            errorMessage = ""
                            scope.launch {
                                OAuthServer.startGoogleSignIn { result ->
                                    isLoading = false
                                    if (result.success) {
                                        onLoginSuccess(
                                            result.idToken!!,
                                            result.uid!!,
                                            result.name!!,
                                            result.email!!,
                                            result.photoUrl ?: ""
                                        )
                                    } else {
                                        errorMessage = result.errorMessage ?: "Google Sign-In failed."
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(27.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource("drawable/google.xml"),
                                contentDescription = "Google",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Continue with Google",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Divider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.15f))
                        Text(
                            text = "OR TEST SESSION",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.15f))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Test sign-in input
                    OutlinedTextField(
                        value = testUserName,
                        onValueChange = { testUserName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter testing display name...", color = Color.White.copy(0.4f)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF66BB6A),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF66BB6A)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (testUserName.trim().isNotEmpty()) {
                                isLoading = true
                                scope.launch {
                                    delay(800)
                                    isLoading = false
                                    // Generate simulated auth result
                                    val name = testUserName.trim()
                                    val safeId = name.lowercase().replace(" ", "_")
                                    val mockUid = "test_uid_$safeId"
                                    val mockToken = "mock_auth_token_for_$safeId"
                                    onLoginSuccess(
                                        mockToken,
                                        mockUid,
                                        name,
                                        "$safeId@loomi.test",
                                        ""
                                    )
                                }
                            } else {
                                errorMessage = "Please enter a test display name."
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1C1C1C),
                            contentColor = Color.White
                        ),
                        border = borderStroke(1.dp, Color.White.copy(0.15f))
                    ) {
                        Text(
                            text = "Quick Sign-In",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFF5252),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Loomi Security Standard v1.0\nEnd-to-End Encrypted Node",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.35f),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// Helper utility for border stroke
fun borderStroke(width: androidx.compose.ui.unit.Dp, color: Color) =
    androidx.compose.foundation.BorderStroke(width, color)
