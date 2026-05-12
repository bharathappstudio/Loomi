package com.echo.loomi

import android.content.Intent
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
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
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val auth = FirebaseAuth.getInstance()
        val prefs = getSharedPreferences("echo_prefs", MODE_PRIVATE)
        
        // 1. Check if user is logged in
        if (auth.currentUser == null) {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
        // 2. Check if profile setup is done locally
        if (!prefs.getBoolean("profile_done", false)) {
            // Verify with Firebase in case prefs were cleared
            val db = FirebaseDatabase.getInstance("https://echo-loomi-app-default-rtdb.firebaseio.com/").reference
            db.child("users").child(auth.currentUser!!.uid).child("imageName").get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        prefs.edit().putBoolean("profile_done", true).apply()
                        // Continue to UI
                        startApp()
                    } else {
                        val intent = Intent(this, WelcomeActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                }
                .addOnFailureListener {
                    val intent = Intent(this, WelcomeActivity::class.java)
                    startActivity(intent)
                    finish()
                }
        } else {
            startApp()
        }
    }

    private fun startApp() {
        enableEdgeToEdge()
        setContent {
            LoomiTheme {
                SnapStyleScreen(
                    onLogout = {
                        val auth = FirebaseAuth.getInstance()
                        val uid = auth.currentUser?.uid
                        if (uid != null) {
                            val database = FirebaseDatabase.getInstance("https://echo-loomi-app-default-rtdb.firebaseio.com/").reference
                            database.child("users").child(uid).child("status").setValue("Offline")
                            database.child("users").child(uid).child("lastSeen").setValue(ServerValue.TIMESTAMP)
                        }
                        auth.signOut()
                        getSharedPreferences("echo_prefs", MODE_PRIVATE).edit().clear().apply()
                        
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

@Immutable
data class SnapUser(
    val uid: String,
    val name: String,
    val status: String = "Offline",
    val lastSeen: Long = 0,
    val isPinned: Boolean = false,
    val imageName: String
)

fun formatLastSeen(lastSeen: Long): String {
    if (lastSeen <= 0) return "Never"
    val now = System.currentTimeMillis()
    val diff = now - lastSeen
    
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}

@Composable
fun SnapStyleScreen(onLogout: () -> Unit) {
    val usersList = remember { mutableStateListOf<SnapUser>() }
    val currentUser = FirebaseAuth.getInstance().currentUser
    var currentUserImage by remember { mutableStateOf("Ellipse 1.png") }

    LaunchedEffect(Unit) {
        val database = FirebaseDatabase.getInstance("https://echo-loomi-app-default-rtdb.firebaseio.com/").reference
        val uid = currentUser?.uid ?: return@LaunchedEffect

        // Presence System
        val userStatusRef = database.child("users").child(uid).child("status")
        val lastSeenRef = database.child("users").child(uid).child("lastSeen")
        val connectedRef = database.child(".info/connected")

        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    userStatusRef.setValue("Online")
                    userStatusRef.onDisconnect().setValue("Offline")
                    lastSeenRef.onDisconnect().setValue(ServerValue.TIMESTAMP)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Fetch current user image
        database.child("users").child(uid).child("imageName")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    currentUserImage = snapshot.getValue(String::class.java) ?: "Ellipse 1.png"
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        // Fetch other users
        database.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                usersList.clear()
                for (userSnapshot in snapshot.children) {
                    val otherUid = userSnapshot.child("uid").getValue(String::class.java) ?: ""
                    if (otherUid != uid) {
                        val name = userSnapshot.child("name").getValue(String::class.java) ?: "Unknown"
                        val imageName = userSnapshot.child("imageName").getValue(String::class.java) ?: "Ellipse 1.png"
                        val status = userSnapshot.child("status").getValue(String::class.java) ?: "Offline"
                        val lastSeen = userSnapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                        
                        usersList.add(SnapUser(
                            uid = otherUid,
                            name = name, 
                            imageName = imageName,
                            status = status,
                            lastSeen = lastSeen
                        ))
                    }
                }
                // Sort online users to top
                usersList.sortByDescending { it.status == "Online" }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
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
                            val profileRequest = remember(currentUserImage) {
                                ImageRequest.Builder(context)
                                    .data("file:///android_asset/user/$currentUserImage")
                                    .placeholder(android.R.drawable.ic_menu_report_image)
                                    .error(android.R.drawable.ic_menu_report_image)
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
                            IconButton(onClick = onLogout) {
                                Icon(Icons.Default.PushPin, contentDescription = "Logout", tint = Color.Black)
                            }
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
                        items = usersList,
                        key = { it.uid },
                        contentType = { "chat_item" }
                    ) { user ->
                        SnapChatItem(user)
                    }
                }

                // Smooth Bottom Fade Overlay
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
fun SnapChatItem(user: SnapUser) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
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
        // User Icon
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
                    .placeholder(android.R.drawable.ic_menu_report_image)
                    .error(android.R.drawable.ic_menu_report_image)
                    .size(150, 150)
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
            
            // Online status dot
            if (user.status == "Online") {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.BottomEnd)
                        .background(Color.Green, CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = user.name, fontSize = 17.sp, fontWeight = FontWeight.Normal)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusText = if (user.status == "Online") "Online" else formatLastSeen(user.lastSeen)
                val statusColor = if (user.status == "Online") Color(0xFF66BB6A) else Color.Gray
                
                Text(
                    text = if (user.status == "Online") "● " else "➤ ",
                    color = statusColor,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(text = statusText, color = Color.Gray, fontSize = 13.sp)
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
            .zIndex(1f)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFFFFF2D9).copy(alpha = 200f))
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
                    tint = Color.Black,
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
