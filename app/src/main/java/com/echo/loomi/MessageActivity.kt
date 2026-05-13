package com.echo.loomi

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.unit.lerp
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.unit.fontscaling.MathUtils.lerp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.echo.loomi.ui.theme.LoomiTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.delay

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val message: String = "",
    val timestamp: Long = 0
)

class MessageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        enableEdgeToEdge()

        val receiverUid = intent.getStringExtra("receiverUid") ?: ""
        val receiverName = intent.getStringExtra("receiverName") ?: ""
        val receiverImage = intent.getStringExtra("receiverImage") ?: ""

        if (receiverUid.isEmpty()) {
            finish()
            return
        }

        setContent {
            LoomiTheme {
                MessageScreen(
                    receiverUid = receiverUid,
                    receiverName = receiverName,
                    receiverImage = receiverImage,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        val parts = text.split("**")
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
            } else {
                append(part)
            }
        }
    }
}

@Composable
fun MessageScreen(
    receiverUid: String,
    receiverName: String,
    receiverImage: String,
    onBack: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val currentUid = auth.currentUser?.uid ?: return
    val database = FirebaseDatabase.getInstance("https://echo-loomi-app-default-rtdb.firebaseio.com/").reference
    
    val chatId = if (currentUid < receiverUid) "${currentUid}_$receiverUid" else "${receiverUid}_$currentUid"
    val messagesList = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Transform state
    var isReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(3000)
        isReady = true
    }

    LaunchedEffect(chatId) {
        database.child("chats").child(chatId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messagesList.clear()
                for (msgSnapshot in snapshot.children) {
                    val msg = msgSnapshot.getValue(ChatMessage::class.java)
                    if (msg != null) {
                        messagesList.add(msg)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    LaunchedEffect(messagesList.size) {
        if (messagesList.isNotEmpty()) {
            listState.animateScrollToItem(messagesList.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFFF3E0)))

        Column(modifier = Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
            MessageTopBar(receiverName, receiverImage, onBack)
            
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messagesList, key = { it.id }) { msg ->
                        val isMe = msg.senderId == currentUid
                        ChatBubble(msg, isMe)
                    }
                }
            }

            FloatingBottomNavBar(
                text = input,
                onTextChange = { input = it },
                onSend = {
                    if (input.trim().isNotEmpty()) {
                        val msgId = database.child("chats").child(chatId).push().key ?: ""
                        val message = ChatMessage(
                            id = msgId,
                            senderId = currentUid,
                            receiverId = receiverUid,
                            message = input,
                            timestamp = System.currentTimeMillis()
                        )
                        database.child("chats").child(chatId).child(msgId).setValue(message)
                        input = ""
                    }
                },
                isExpanded = isReady,
                onExpandedChange = { isReady = it },
                modifier = Modifier.padding(bottom = 20.dp)//floting nave bar hight
            )
        }
    }
}

@Composable
fun MessageTopBar(receiverName: String, receiverImage: String, onBack: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.9f),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.Black
                )
            }
            
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("file:///android_asset/user/$receiverImage")
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = receiverName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "Online",
                    fontSize = 12.sp,
                    color = Color(0xFF66BB6A)
                )
            }
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
fun FloatingBottomNavBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val animProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "nav_morph"
    )

    val horizontalPadding = androidx.compose.ui.unit.lerp(80.dp, 10.dp, animProgress)
    val barHeight = androidx.compose.ui.unit.lerp(50.dp, 60.dp, animProgress)
    val bgColor = androidx.compose.ui.graphics.lerp(Color(0xFFFFF2D9), Color.White.copy(0.55f), animProgress)
    val borderAlpha = androidx.compose.ui.util.lerp(0.8f, 0.3f, animProgress)

    Box(
        modifier = modifier
            .padding(horizontal = horizontalPadding)
            .height(barHeight)
            .clip(RoundedCornerShape(30.dp))
            .background(bgColor)
            .border(
                width = 2.dp,
                color = Color(0xFFFFFFFF).copy(alpha = if (isExpanded) 0.3f else 0.8f),
                shape = RoundedCornerShape(30.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (animProgress < 0.5f) {
                // Icons Mode
                IconButton(
                    onClick = { /* Handle camera */ },
                    modifier = Modifier.size(36.dp).graphicsLayer(alpha = 1f - animProgress * 2)
                ) {
                    Icon(Icons.Outlined.PhotoCamera, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                }
                
                Spacer(modifier = Modifier.width(20.dp))

                IconButton(
                    onClick = { onExpandedChange(true) },
                    modifier = Modifier.size(36.dp).graphicsLayer(alpha = 1f - animProgress * 2)
                ) {
                    Icon(Icons.Outlined.Keyboard, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                }

                Spacer(modifier = Modifier.width(20.dp))

                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.size(36.dp).graphicsLayer(alpha = 1f - animProgress * 2)
                ) {
                    Icon(Icons.Outlined.Search, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                }
            } else {
                // Input Mode
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Logo Button - Modern Glassmorphism
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.35f)) // Slightly stronger glass look
                            .clickable { onExpandedChange(false) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.logo),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp), // Slightly smaller for premium feel
                            tint = Color.Unspecified
                        )
                    }

                    // 2. Smoothly animated TextField
                    // We use AnimatedVisibility for a "gentle" entrance
                    AnimatedVisibility(
                        visible = animProgress > 0.5f,
                        enter = fadeIn(animationSpec = tween(400)) + expandHorizontally(),
                        exit = fadeOut(animationSpec = tween(300)) + shrinkHorizontally(),
                        modifier = Modifier.weight(1f)
                    ) {
                        TextField(
                            value = text,
                            onValueChange = onTextChange,
                            placeholder = {
                                Text(
                                    "Ask...",
                                    color = Color.Black.copy(0.4f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Color.Black
                            ),
                            singleLine = true
                        )
                    }

                    // 3. Send Button - Color Morphing
                    val sendButtonColor by animateColorAsState(
                        targetValue = if (text.isNotBlank()) Color.Black else Color.Black.copy(0.15f),
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "color"
                    )

                    IconButton(
                        onClick = onSend,
                        modifier = Modifier
                            .size(46.dp)
                            .graphicsLayer {
                                // Gentle scale up as the bar expands
                                val scale = lerp(0.8f, 1f, (animProgress - 0.5f).coerceAtLeast(0f) * 2)
                                scaleX = scale
                                scaleY = scale
                                alpha = (animProgress - 0.5f).coerceAtLeast(0f) * 2
                            }
                            .clip(CircleShape)
                            .background(sendButtonColor)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage, isMe: Boolean) {
    val bubbleShape = RoundedCornerShape(
        topStart = 22.dp,
        topEnd = 22.dp,
        bottomStart = if (isMe) 22.dp else 8.dp,
        bottomEnd = if (isMe) 5.dp else 22.dp
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(bubbleShape)
                .background(if (isMe) Color(0x66C8E6C9) else Color(0x80FFECB3).copy(alpha = 0.45f))
                .border(1.dp, Color.White.copy(alpha = 0.80f), bubbleShape)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = parseMarkdown(msg.message),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                color = Color(0xB3000000)
            )
        }
    }
}


