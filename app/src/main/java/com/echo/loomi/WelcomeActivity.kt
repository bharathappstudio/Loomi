package com.echo.loomi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.echo.loomi.ui.theme.LoomiTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class WelcomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LoomiTheme {
                WelcomeScreen(onFinish = {
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                })
            }
        }
    }
}

@Composable
fun WelcomeScreen(onFinish: () -> Unit) {
    val imageNames = (1..14).map { if (it < 10) "0$it.png" else "$it.png" }
    var selectedGender by remember { mutableStateOf("Male") }
    var selectedImage by remember { mutableStateOf(imageNames[0]) }
    val auth = FirebaseAuth.getInstance()
    val database = FirebaseDatabase.getInstance("https://echo-loomi-app-default-rtdb.firebaseio.com/").reference
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding(),
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
                Text(
                    text = "Pick Your Profile",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                Surface(
                    modifier = Modifier.size(200.dp),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(4.dp, Color.Black.copy(alpha = 0.1f)),
                    color = Color.Transparent
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("file:///android_asset/Memoji/$selectedGender/Circle/$selectedImage")
                            .build(),
                        contentDescription = "Selected Profile",
                        modifier = Modifier.clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // Bottom Section: Selection List
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Color(0xFFF7F7F7))
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select an Image",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Gender Filter Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf("Male", "Female").forEach { gender ->
                    val isSelected = selectedGender == gender
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (isSelected) Color.Black else Color.White)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color.Black else Color.LightGray.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .clickable { selectedGender = gender },
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

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(imageNames) { imageName ->
                    val isSelected = selectedImage == imageName
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) Color.Black else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { selectedImage = imageName }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/Memoji/$selectedGender/Circle/$imageName")
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val user = auth.currentUser
                    if (user != null) {
                        val fullPath = "Memoji/$selectedGender/Circle/$selectedImage"
                        database.child("users").child(user.uid).child("imageName").setValue(fullPath)
                            .addOnCompleteListener {
                                // Save locally that profile is complete
                                val prefs = context.getSharedPreferences("echo_prefs", android.content.Context.MODE_PRIVATE)
                                prefs.edit().putBoolean("profile_done", true).apply()
                                onFinish()
                            }
                    } else {
                        onFinish()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = "Let's Go!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
