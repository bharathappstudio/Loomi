package com.echo.loomi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.echo.loomi.ui.theme.LoomiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LoomiTheme {
                SnapStyleScreen()
            }
        }
    }
}

@Immutable
data class SnapUser(
    val name: String,
    val status: String,
    val time: String,
    val isPinned: Boolean = false,
    val imageName: String
)

@Composable
fun SnapStyleScreen() {
    val users = remember {
        listOf(
            SnapUser("Hema Kutty", "Delivered", "8m", true, "Ellipse 7.png"),
            SnapUser("Cute _girl🥰", "Delivered", "8m", true, "Ellipse 112.png"),
            SnapUser("My Lover 😘", "Received", "1d", true, "Ellipse 47.png"),
            SnapUser("Priya Chlo 😚", "Delivered", "5m", false, "Ellipse 81.png"),
            SnapUser("Sneha", "Received", "10m", false, "Ellipse 1.png"),
            SnapUser("Anjali ✨", "Delivered", "15m", false, "Ellipse 2.png"),
            SnapUser("Rahul", "Received", "1h", false, "Ellipse 3.png"),
            SnapUser("Pooja 🌸", "Delivered", "2h", false, "Ellipse 4.png"),
            SnapUser("Vikram", "Opened", "3h", false, "Ellipse 5.png"),
            SnapUser("Kavya", "Received", "5h", false, "Ellipse 6.png"),
            SnapUser("Arjun", "Delivered", "6h", false, "Ellipse 9.png"),
            SnapUser("Deepa 💎", "Opened", "7h", false, "Ellipse 10.png"),
            SnapUser("Suresh", "Received", "12h", false, "Ellipse 11.png"),
            SnapUser("Meera", "Delivered", "1d", false, "Ellipse 12.png"),
            SnapUser("Amit", "Opened", "2d", false, "Ellipse 13.png"),
            SnapUser("Swati 🦋", "Received", "3d", false, "Ellipse 14.png"),
            SnapUser("Karan", "Delivered", "4d", false, "Ellipse 15.png"),
            SnapUser("Neha 🌈", "Opened", "5d", false, "Ellipse 16.png"),
            SnapUser("Vijay", "Received", "1w", false, "Ellipse 17.png"),
            SnapUser("Divya", "Delivered", "1w", false, "Ellipse 18.png")
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color(0xFFFFFFFF).copy(alpha = 0.5f),
            topBar = {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .background(Color.Transparent)
                ) {
                    // Top Action Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Left: Profile Circle (Snap Style)
                        Surface(
                            modifier = Modifier
                                .size(45.dp)
                                .align(Alignment.CenterStart),
                            shape = CircleShape,
                            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFFFFF).copy(alpha = 0.5f)),
                            color = Color.Transparent
                        ) {
                            val context = LocalContext.current
                            val profileRequest = remember {
                                ImageRequest.Builder(context)
                                    .data("file:///android_asset/user/Ellipse 139.png")
                                    .size(120, 120)
                                    .build()
                            }
                            AsyncImage(
                                model = profileRequest,
                                contentDescription = "Profile",
                                modifier = Modifier.clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // Center: Logo
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "Logo",
                            modifier = Modifier.height(30.dp),
                            contentScale = ContentScale.Fit
                        )

                        // Right Icons
                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(contentAlignment = Alignment.TopEnd) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Yellow,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(
                        items = users,
                        key = { it.name + it.imageName },
                        contentType = { "chat_item" }
                    ) { user ->
                        SnapChatItem(user)
                    }
                }

                // Smooth Bottom Fade Overlay (Optimized: No BlendMode/Offscreen)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xFFFFFFFF).copy(alpha = 5000f)),
                            )
                        )
                )
            }
        }

        // --- Floating Bottom Navigation Bar ---
        FloatingBottomNavBar(
            onSearchClick = { /* Handle search click */ },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 30.dp)
        )
    }
}

@Composable
fun SnapTab(text: String, count: String? = null, isSelected: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Color.Black else Color(0xFFF7F7F7),
        modifier = Modifier.height(34.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = if (isSelected) Color.White else Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            count?.let {
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(if (isSelected) Color.White else Color.Black), contentAlignment = Alignment.Center) {
                    Text(it, color = if (isSelected) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SnapChatItem(user: SnapUser) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // Drawing the divider directly to improve performance (reduces layout nodes)
                val strokeWidth = 0.5.dp.toPx()
                val y = size.height - strokeWidth / 2
                drawLine(
                    color = Color(0xFFEEEEEE),
                    start = Offset(72.dp.toPx(), y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeWidth
                )
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // User Icon (Outline style like Snap) - Optimized with Box instead of Surface
        Box(
            modifier = Modifier
                .size(54.dp)
                .border(2.dp, Color(0xFFFFA500).copy(alpha = 0.5f), CircleShape)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val context = LocalContext.current
            val imageRequest = remember(user.imageName) {
                ImageRequest.Builder(context)
                    .data("file:///android_asset/user/${user.imageName}")
                    .crossfade(true)
                    .size(150, 150) // Hardware-accelerated downsizing for assets
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier
                    .padding(4.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = user.name, fontSize = 17.sp, fontWeight = FontWeight.Normal)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The status icon style
                val statusIcon = when (user.status) {
                    "Delivered" -> "➤"
                    "Received" -> "☐"
                    "Opened" -> "▻"
                    else -> "➤"
                }
                val statusColor = when (user.status) {
                    "Delivered" -> Color(0xFF00B0FF) // Blue
                    "Received" -> Color.Red
                    "Opened" -> Color.Red
                    else -> Color.Gray
                }
                Text(
                    text = statusIcon,
                    color = statusColor,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(text = "${user.status} • ${user.time}", color = Color.Gray, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun FloatingBottomNavBar(
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .zIndex(1f) // THIS FORCES IT TO THE VERY FRONT
            .clip(RoundedCornerShape(30.dp))
            // The "Liquid Glass" translucent background (50% solid so it's clearly visible)
            .background(Color(0xFFFFF2D9).copy(alpha = 200f))
            // The subtle shiny glass edge
            .border(
                width = 2.dp,
                color = Color(0xFFFFFFFF).copy(alpha = 3000f),
                shape = RoundedCornerShape(30.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { /* Handle camera click */ },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = "Camera",
                    tint = Color.Black, // Icons are pure black and sharp now
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = { /* Handle check click */ },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "Done",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}