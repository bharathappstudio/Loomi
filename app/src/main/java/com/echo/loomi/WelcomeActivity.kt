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
    val userImages = listOf(
        "Ellipse 1.png", "Ellipse 2.png", "Ellipse 3.png", "Ellipse 4.png",
        "Ellipse 5.png", "Ellipse 6.png", "Ellipse 7.png", "Ellipse 9.png",
        "Ellipse 10.png", "Ellipse 11.png", "Ellipse 12.png", "Ellipse 13.png",
        "Ellipse 14.png", "Ellipse 15.png", "Ellipse 16.png", "Ellipse 17.png",
        "Ellipse 18.png", "Ellipse 47.png", "Ellipse 81.png", "Ellipse 112.png"
    )

    var selectedImage by remember { mutableStateOf(userImages[0]) }
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
                            .data("file:///android_asset/user/$selectedImage")
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

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(userImages) { imageName ->
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
                                .data("file:///android_asset/user/$imageName")
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
                        database.child("users").child(user.uid).child("imageName").setValue(selectedImage)
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
