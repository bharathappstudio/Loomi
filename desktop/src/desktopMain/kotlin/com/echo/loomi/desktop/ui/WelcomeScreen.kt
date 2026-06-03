package com.echo.loomi.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echo.loomi.desktop.network.FirebaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.Base64

@Composable
fun WelcomeScreen(
    googlePhotoUrl: String,
    onProfileComplete: () -> Unit
) {
    val imageNames = (1..14).map { if (it < 10) "0$it.png" else "$it.png" }
    var selectedGender by remember { mutableStateOf("Male") }
    var selectedImage by remember { mutableStateOf(imageNames[0]) }
    var customImageBase64 by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isProfileLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Loading indicator animation logic (colors matching Google Auth)
    val loadingColors = listOf(
        Color(0xFF8AB4F8), Color(0xFFF28B82), Color(0xFFFDD663),
        Color(0xFF81C995), Color(0xFF669DF6)
    )
    var colorIndex1 by remember { mutableStateOf(0) }
    var colorIndex2 by remember { mutableStateOf(1) }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            while (true) {
                delay(700)
                colorIndex1 = (colorIndex1 + 1) % loadingColors.size
                colorIndex2 = (colorIndex2 + 1) % loadingColors.size
            }
        }
    }

    val c1 by animateColorAsState(loadingColors[colorIndex1], tween(600))
    val c2 by animateColorAsState(loadingColors[colorIndex2], tween(600))

    // URL / Base64 image cache
    var customBitmap by remember(customImageBase64) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(customImageBase64) {
        if (customImageBase64 != null) {
            withContext(Dispatchers.IO) {
                customBitmap = try {
                    val cleanStr = if (customImageBase64!!.startsWith("data:image")) {
                        customImageBase64!!.substringAfter("base64,")
                    } else {
                        customImageBase64!!
                    }
                    val bytes = Base64.getDecoder().decode(cleanStr.replace("\\s".toRegex(), ""))
                    loadImageBitmap(bytes.inputStream())
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }
    }

    var googleBitmap by remember(googlePhotoUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(googlePhotoUrl) {
        if (googlePhotoUrl.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                googleBitmap = try {
                    val bytes = URL(googlePhotoUrl).openStream().readBytes()
                    loadImageBitmap(bytes.inputStream())
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFC8E6C9)) // Loomi Light Mode background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Section: Selection Preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.4f))
                            .border(4.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isProfileLoading) {
                            CircularProgressIndicator(color = Color.White)
                        } else {
                            if (customBitmap != null) {
                                Image(
                                    bitmap = customBitmap!!,
                                    contentDescription = "Custom Profile",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else if (selectedImage.isEmpty() && googleBitmap != null) {
                                Image(
                                    bitmap = googleBitmap!!,
                                    contentDescription = "Google Profile",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                // Load selected memoji from local resources
                                val resourcePath = "Memoji/$selectedGender/Circle/$selectedImage"
                                Image(
                                    painter = painterResource(resourcePath),
                                    contentDescription = "Selected Memoji",
                                    modifier = Modifier.fillMaxSize().padding(10.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Section: Selection List
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 24.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top 2 Round Buttons: Gallery (File Chooser) and Google Photo
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Gallery File Selector Button
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF5F5F5))
                            .border(1.dp, Color.LightGray.copy(0.3f), CircleShape)
                            .clickable {
                                scope.launch(Dispatchers.IO) {
                                    val fileDialog = java.awt.FileDialog(
                                        null as java.awt.Frame?,
                                        "Select Profile Image",
                                        java.awt.FileDialog.LOAD
                                    )
                                    fileDialog.file = "*.png;*.jpg;*.jpeg"
                                    fileDialog.isVisible = true
                                    val dir = fileDialog.directory
                                    val file = fileDialog.file
                                    if (dir != null && file != null) {
                                        val selectedFile = File(dir, file)
                                        if (selectedFile.exists()) {
                                            val bytes = selectedFile.readBytes()
                                            val base64 = Base64.getEncoder().encodeToString(bytes)
                                            withContext(Dispatchers.Main) {
                                                customImageBase64 = "data:image/jpeg;base64,$base64"
                                                selectedImage = "" // Clear memoji selection
                                            }
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource("drawable/image.xml"),
                            contentDescription = "Gallery",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    if (googlePhotoUrl.isNotEmpty() && googleBitmap != null) {
                        Spacer(modifier = Modifier.width(24.dp))

                        // 2. Google Profile Photo Button
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF5F5F5))
                                .border(
                                    width = if (selectedImage.isEmpty() && customImageBase64 == null) 2.dp else 1.dp,
                                    color = if (selectedImage.isEmpty() && customImageBase64 == null) Color.Black else Color.LightGray.copy(0.3f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    customImageBase64 = null
                                    selectedImage = ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = googleBitmap!!,
                                contentDescription = "Google Photo",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                // Gender Selection Filters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("Male", "Female").forEach { gender ->
                        val isSelected = selectedGender == gender
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(if (isSelected) Color.Black else Color(0xFFF5F5F5))
                                .clickable {
                                    if (selectedGender != gender) {
                                        scope.launch {
                                            isProfileLoading = true
                                            selectedGender = gender
                                            customImageBase64 = null
                                            if (selectedImage.isEmpty()) selectedImage = imageNames[0]
                                            delay(200)
                                            isProfileLoading = false
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = gender,
                                color = if (isSelected) Color.White else Color.Black,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Lazy Memoji Row
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(imageNames) { imageName ->
                        val isSelected = selectedImage == imageName
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.Black else Color.LightGray.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    if (selectedImage != imageName) {
                                        scope.launch {
                                            isProfileLoading = true
                                            selectedImage = imageName
                                            customImageBase64 = null
                                            delay(200)
                                            isProfileLoading = false
                                        }
                                    }
                                }
                        ) {
                            val resPath = "Memoji/$selectedGender/Circle/$imageName"
                            Image(
                                painter = painterResource(resPath),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            isLoading = true
                            val uid = FirebaseClient.currentUid
                            if (uid != null) {
                                scope.launch(Dispatchers.IO) {
                                    val imagePath = when {
                                        customImageBase64 != null -> customImageBase64!!
                                        selectedImage.isEmpty() && googlePhotoUrl.isNotEmpty() -> googlePhotoUrl
                                        else -> "Memoji/$selectedGender/Circle/$selectedImage"
                                    }

                                    FirebaseClient.write("users/$uid/imageName", imagePath) { success ->
                                        scope.launch(Dispatchers.Main) {
                                            isLoading = false
                                            if (success) {
                                                onProfileComplete()
                                            }
                                        }
                                    }
                                }
                            } else {
                                onProfileComplete()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1C), contentColor = Color.White)
                    ) {
                        Text(
                            text = "Let's Go!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
