package com.echo.loomi.desktop.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.echo.loomi.desktop.network.FirebaseClient
import com.echo.loomi.desktop.utils.DesktopEncryptionUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.UUID

// Data models
data class SnapUser(
    val uid: String,
    val name: String,
    val status: String = "Offline",
    val lastSeen: Long = 0,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0,
    val imageName: String = ""
)

data class Story(
    val id: String = "",
    val uid: String = "",
    val image: String = "", // Base64
    val songId: Long = 0,
    val songName: String = "",
    val timestamp: Long = 0,
    var userName: String = "",
    var userProfileImage: String = ""
)

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val message: String = "",
    val timestamp: Long = 0
)

data class CallData(
    val callerId: String = "",
    val receiverId: String = "",
    val callerName: String = "",
    val callerImage: String = "",
    val status: String = "ringing", // ringing, accepted, declined, ended
    val timestamp: Long = System.currentTimeMillis()
)

enum class CallState {
    IDLE, INCOMING, OUTGOING, ONGOING
}

@Composable
fun MainScreen(
    currentUserName: String,
    currentUserImage: String,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    // Database states
    val usersList = remember { mutableStateListOf<SnapUser>() }
    val storiesList = remember { mutableStateListOf<Story>() }
    val messagesList = remember { mutableStateListOf<ChatMessage>() }

    // Selection states
    var selectedUser by remember { mutableStateOf<SnapUser?>(null) }
    var selectedStory by remember { mutableStateOf<Story?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Calling states
    var callState by remember { mutableStateOf(CallState.IDLE) }
    var activeCallData by remember { mutableStateOf<CallData?>(null) }
    
    // SOS states
    var showSOSOverlay by remember { mutableStateOf(false) }

    // Sync current user status to Online
    LaunchedEffect(Unit) {
        val uid = FirebaseClient.currentUid ?: return@LaunchedEffect
        FirebaseClient.write("users/$uid/status", "Online")
        // Set last seen when window closes / app terminates
        Runtime.getRuntime().addShutdownHook(Thread {
            FirebaseClient.write("users/$uid/status", "Offline")
            FirebaseClient.write("users/$uid/lastSeen", System.currentTimeMillis())
        })
    }

    // SSE Database Sync Loop
    LaunchedEffect(Unit) {
        val uid = FirebaseClient.currentUid ?: return@LaunchedEffect
        val gson = Gson()

        // 1. Listen to all users
        FirebaseClient.startListener("users") { event, path, json ->
            scope.launch(Dispatchers.Main) {
                if (json == "null") return@launch
                
                if (path == "" || path == "/") {
                    // Full list replace
                    try {
                        val type = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
                        val data: Map<String, Map<String, Any>> = gson.fromJson(json, type)
                        usersList.clear()
                        data.forEach { (key, value) ->
                            if (key != uid) {
                                usersList.add(parseUserMap(key, value))
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    // Single user update
                    val key = path.replace("/", "")
                    if (key != uid) {
                        try {
                            val type = object : TypeToken<Map<String, Any>>() {}.type
                            val value: Map<String, Any> = gson.fromJson(json, type)
                            val index = usersList.indexOfFirst { it.uid == key }
                            val updatedUser = parseUserMap(key, value)
                            if (index != -1) {
                                usersList[index] = updatedUser
                            } else {
                                usersList.add(updatedUser)
                            }
                        } catch (e: Exception) {
                            // Property update (e.g. status changed)
                            FirebaseClient.read("users/$key") { userJson ->
                                if (userJson != null) {
                                    scope.launch(Dispatchers.Main) {
                                        val type = object : TypeToken<Map<String, Any>>() {}.type
                                        val value: Map<String, Any> = gson.fromJson(userJson, type)
                                        val index = usersList.indexOfFirst { it.uid == key }
                                        if (index != -1) {
                                            usersList[index] = parseUserMap(key, value)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Listen to stories
        FirebaseClient.startListener("stories") { event, path, json ->
            scope.launch(Dispatchers.Main) {
                if (json == "null") {
                    storiesList.clear()
                    return@launch
                }
                
                FirebaseClient.read("stories") { storiesJson ->
                    if (storiesJson != null && storiesJson != "null") {
                        scope.launch(Dispatchers.Main) {
                            try {
                                val type = object : TypeToken<Map<String, Story>>() {}.type
                                val data: Map<String, Story> = gson.fromJson(storiesJson, type)
                                val currentStories = data.values.filter { 
                                    it.timestamp > (System.currentTimeMillis() - 24 * 60 * 60 * 1000)
                                }
                                storiesList.clear()
                                currentStories.forEach { story ->
                                    // Fetch user details for each story
                                    FirebaseClient.read("users/${story.uid}") { userJson ->
                                        if (userJson != null) {
                                            scope.launch(Dispatchers.Main) {
                                                val userMap: Map<String, Any> = gson.fromJson(userJson, object : TypeToken<Map<String, Any>>() {}.type)
                                                story.userName = userMap["name"] as? String ?: "Unknown"
                                                story.userProfileImage = userMap["imageName"] as? String ?: ""
                                                
                                                if (storiesList.none { it.id == story.id }) {
                                                    storiesList.add(story)
                                                    storiesList.sortByDescending { it.timestamp }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }

        // 3. Listen to incoming calls
        FirebaseClient.startListener("calls/$uid") { event, path, json ->
            scope.launch(Dispatchers.Main) {
                if (json == "null") {
                    callState = CallState.IDLE
                    activeCallData = null
                } else {
                    try {
                        val data = gson.fromJson(json, CallData::class.java)
                        activeCallData = data
                        if (data.status == "ringing") {
                            callState = CallState.INCOMING
                        } else if (data.status == "accepted") {
                            callState = CallState.ONGOING
                        } else if (data.status == "declined" || data.status == "ended") {
                            callState = CallState.IDLE
                            activeCallData = null
                        }
                    } catch (e: Exception) {
                        // Might be single property change
                        FirebaseClient.read("calls/$uid") { callJson ->
                            if (callJson != null && callJson != "null") {
                                scope.launch(Dispatchers.Main) {
                                    val data = gson.fromJson(callJson, CallData::class.java)
                                    activeCallData = data
                                    callState = when (data.status) {
                                        "ringing" -> CallState.INCOMING
                                        "accepted" -> CallState.ONGOING
                                        else -> CallState.IDLE
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Sync conversations and active chat messages
    LaunchedEffect(selectedUser) {
        val uid = FirebaseClient.currentUid ?: return@LaunchedEffect
        val receiver = selectedUser
        messagesList.clear()
        
        if (receiver != null) {
            val chatId = if (uid < receiver.uid) "${uid}_${receiver.uid}" else "${receiver.uid}_$uid"
            
            FirebaseClient.startListener("chats/$chatId") { event, path, json ->
                scope.launch(Dispatchers.Main) {
                    if (json == "null") return@launch
                    
                    FirebaseClient.read("chats/$chatId") { chatJson ->
                        if (chatJson != null && chatJson != "null") {
                            scope.launch(Dispatchers.Main) {
                                try {
                                    val type = object : TypeToken<Map<String, ChatMessage>>() {}.type
                                    val data: Map<String, ChatMessage> = Gson().fromJson(chatJson, type)
                                    messagesList.clear()
                                    messagesList.addAll(data.values.sortedBy { it.timestamp })
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }
            }

            // Sync outgoing call listener
            FirebaseClient.startListener("calls/${receiver.uid}") { event, path, json ->
                scope.launch(Dispatchers.Main) {
                    if (json == "null" && callState == CallState.OUTGOING) {
                        callState = CallState.IDLE
                    } else if (json != "null" && callState == CallState.OUTGOING) {
                        try {
                            val data = Gson().fromJson(json, CallData::class.java)
                            if (data.status == "accepted") {
                                callState = CallState.ONGOING
                            } else if (data.status == "declined" || data.status == "ended") {
                                callState = CallState.IDLE
                            }
                        } catch (e: Exception) {}
                    }
                }
            }
        } else {
            FirebaseClient.stopListener("chats")
        }
    }

    // Filter users list by search query
    val filteredUsers = usersList.filter {
        it.name.contains(searchQuery, ignoreCase = true)
    }.sortedWith(
        compareByDescending<SnapUser> { it.status == "Online" }
            .thenByDescending { it.lastMessageTime }
    )

    val blurValue by animateDpAsState(
        targetValue = if (callState != CallState.IDLE || selectedStory != null || showSOSOverlay) 20.dp else 0.dp
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurValue)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // LEFT SIDEBAR (Width: 360dp)
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .drawBehind {
                        val strokeWidth = 1.dp.toPx()
                        val x = size.width - strokeWidth / 2
                        drawLine(
                            color = if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.05f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = strokeWidth
                        )
                    }
            ) {
                // Profile & Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Current User Avatar
                    ProfileImage(
                        imagePath = currentUserImage,
                        size = 44.dp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentUserName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Loomi Desktop",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    // SOS Trigger Button
                    IconButton(
                        onClick = { showSOSOverlay = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFFFF5252).copy(0.15f))
                    ) {
                        Text("🚨", fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Settings / Logout Button
                    var showDropdown by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showDropdown = true }) {
                            Image(
                                painter = painterResource("drawable/setting_4.xml"),
                                contentDescription = "Settings",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Log Out Session") },
                                onClick = {
                                    showDropdown = false
                                    val uid = FirebaseClient.currentUid
                                    if (uid != null) {
                                        FirebaseClient.write("users/$uid/status", "Offline")
                                        FirebaseClient.write("users/$uid/lastSeen", System.currentTimeMillis())
                                    }
                                    FirebaseClient.stopAll()
                                    onLogout()
                                }
                            )
                        }
                    }
                }

                // Stories Row
                if (storiesList.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(storiesList) { story ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { selectedStory = story }
                                    .width(60.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .border(2.5.dp, Color(0xFF66BB6A), CircleShape)
                                        .padding(3.dp)
                                        .clip(CircleShape)
                                ) {
                                    ProfileImage(imagePath = story.userProfileImage, size = 48.dp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = story.userName,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                                )
                            }
                        }
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search friends...", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                )

                // Users/Conversations List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredUsers) { user ->
                        val isSelected = selectedUser?.uid == user.uid
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                                    else Color.Transparent
                                )
                                .clickable { selectedUser = user }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                ProfileImage(imagePath = user.imageName, size = 48.dp)
                                // Status indicator dot
                                if (user.status == "Online") {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4CAF50))
                                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                            .align(Alignment.BottomEnd)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val decryptedLastMsg = DesktopEncryptionUtils.decrypt(user.lastMessage)
                                Text(
                                    text = if (decryptedLastMsg.startsWith("img:")) "📷 Photo message" else decryptedLastMsg,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Timestamp or Last seen indicator
                            if (user.status != "Online" && user.lastSeen > 0) {
                                Text(
                                    text = formatLastSeen(user.lastSeen),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }

            // RIGHT CONVERSATION PANE
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (isDark) MaterialTheme.colorScheme.background else Color(0xFFFFFBF6))
            ) {
                val activeReceiver = selectedUser
                if (activeReceiver == null) {
                    // Empty placeholder state
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource("drawable/grop_chart.xml"),
                            contentDescription = "No active chat",
                            modifier = Modifier.size(240.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Secure Loomi Node Active",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                        )
                        Text(
                            text = "Select a friend from the sidebar to begin end-to-end encrypted chats.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                } else {
                    // Active Chat conversation Screen
                    ChatPane(
                        receiver = activeReceiver,
                        messagesList = messagesList,
                        onStartCall = {
                            val uid = FirebaseClient.currentUid ?: return@ChatPane
                            val callData = CallData(
                                callerId = uid,
                                receiverId = activeReceiver.uid,
                                callerName = DesktopEncryptionUtils.encrypt(currentUserName),
                                callerImage = DesktopEncryptionUtils.encrypt(currentUserImage),
                                status = "ringing"
                            )
                            FirebaseClient.write("calls/${activeReceiver.uid}", callData)
                            
                            // Log trigger
                            val callTrigger = mapOf(
                                "type" to "call",
                                "callerId" to uid,
                                "callerName" to currentUserName,
                                "receiverId" to activeReceiver.uid,
                                "timestamp" to System.currentTimeMillis()
                            )
                            FirebaseClient.push("notification_triggers", callTrigger) {}
                            
                            activeCallData = callData
                            callState = CallState.OUTGOING
                        }
                    )
                }
            }
        }

        // OVERLAYS

        // 1. Call Screen Overlay (Ringing / Incoming / Ongoing)
        if (callState != CallState.IDLE && activeCallData != null) {
            val isIncoming = callState == CallState.INCOMING
            val activeCall = activeCallData!!
            
            // Decrypt caller name & image if incoming
            val callName = if (isIncoming) {
                DesktopEncryptionUtils.decrypt(activeCall.callerName)
            } else {
                selectedUser?.name ?: "Unknown"
            }
            
            val callImage = if (isIncoming) {
                DesktopEncryptionUtils.decrypt(activeCall.callerImage)
            } else {
                selectedUser?.imageName ?: ""
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.width(360.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ProfileImage(imagePath = callImage, size = 110.dp)

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = callName,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = when (callState) {
                                CallState.INCOMING -> "Incoming Loomi Call..."
                                CallState.OUTGOING -> "Ringing Loomi Node..."
                                CallState.ONGOING -> "Connected (00:00)"
                                else -> ""
                            },
                            fontSize = 15.sp,
                            color = Color.White.copy(0.6f)
                        )

                        Spacer(modifier = Modifier.height(36.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            if (isIncoming) {
                                // Decline Button
                                Button(
                                    onClick = {
                                        val uid = FirebaseClient.currentUid ?: return@Button
                                        FirebaseClient.delete("calls/$uid")
                                        callState = CallState.IDLE
                                        activeCallData = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                                    modifier = Modifier.size(54.dp),
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("📞", color = Color.White, fontSize = 20.sp)
                                }

                                // Accept Button
                                Button(
                                    onClick = {
                                        val uid = FirebaseClient.currentUid ?: return@Button
                                        FirebaseClient.write("calls/$uid/status", "accepted")
                                        callState = CallState.ONGOING
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                    modifier = Modifier.size(54.dp),
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("📞", color = Color.White, fontSize = 20.sp)
                                }
                            } else {
                                // End Outgoing/Ongoing Call
                                Button(
                                    onClick = {
                                        val receiverUid = activeCall.receiverId
                                        val callerUid = activeCall.callerId
                                        FirebaseClient.delete("calls/$receiverUid")
                                        FirebaseClient.delete("calls/$callerUid")
                                        callState = CallState.IDLE
                                        activeCallData = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                                    modifier = Modifier.size(54.dp),
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("🛑", color = Color.White, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Stories Viewer Overlay
        if (selectedStory != null) {
            val story = selectedStory!!
            var storyBitmap by remember(story.id) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(story.id) {
                if (story.image.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        storyBitmap = try {
                            val clean = story.image.substringAfter("base64,")
                            val bytes = Base64.getDecoder().decode(clean.replace("\\s".toRegex(), ""))
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
                    .background(Color.Black.copy(0.9f))
                    .clickable { selectedStory = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(420.dp).clickable(enabled = false) {}
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileImage(imagePath = story.userProfileImage, size = 38.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(story.userName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(formatLastSeen(story.timestamp), color = Color.LightGray, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { selectedStory = null }) {
                            Text("✕", color = Color.White, fontSize = 20.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(480.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.DarkGray)
                    ) {
                        if (storyBitmap != null) {
                            Image(
                                bitmap = storyBitmap!!,
                                contentDescription = "Story Image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }

                    if (story.songName.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "🎵 ${story.songName}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        }

        // 3. SOS Countdown Overlay
        if (showSOSOverlay) {
            SOSOverlay(
                onTimeout = {
                    val uid = FirebaseClient.currentUid
                    if (uid != null) {
                        val sosData = mapOf(
                            "uid" to uid,
                            "timestamp" to System.currentTimeMillis(),
                            "latitude" to 12.9716, // Simulated desktop location
                            "longitude" to 77.5946,
                            "message" to "Loomi Desktop emergency SOS broadcast triggered."
                        )
                        FirebaseClient.write("sos/$uid", sosData) {}
                    }
                    showSOSOverlay = false
                },
                onCancel = {
                    showSOSOverlay = false
                }
            )
        }
    }
}

// Private chat view pane helper
@Composable
fun ChatPane(
    receiver: SnapUser,
    messagesList: List<ChatMessage>,
    onStartCall: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(messagesList.size) {
        if (messagesList.isNotEmpty()) {
            listState.animateScrollToItem(messagesList.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Chat Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .drawBehind {
                    val strokeWidth = 1.dp.toPx()
                    val y = size.height - strokeWidth / 2
                    drawLine(
                        color = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.04f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokeWidth
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileImage(imagePath = receiver.imageName, size = 42.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = receiver.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (receiver.status == "Online") "Online" else "Offline",
                    fontSize = 12.sp,
                    color = if (receiver.status == "Online") Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface.copy(0.4f)
                )
            }

            // Call buttons
            IconButton(onClick = onStartCall) {
                Image(
                    painter = painterResource("drawable/call.xml"),
                    contentDescription = "Voice Call",
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = {}) {
                Image(
                    painter = painterResource("drawable/video.xml"),
                    contentDescription = "Video Call",
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Messages Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (messagesList.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource("drawable/welcom_to__loomi.xml"),
                        contentDescription = "No chats yet",
                        modifier = Modifier.size(180.dp)
                    )
                    Text(
                        text = "Say Hello to ${receiver.name}!",
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messagesList) { msg ->
                        val isMe = msg.senderId == FirebaseClient.currentUid
                        ChatBubble(msg = msg, isMe = isMe)
                    }
                }
            }
        }

        // Morphing Floating Message Input Box
        FloatingBottomBar(
            text = input,
            onTextChange = { input = it },
            isExpanded = isExpanded,
            onExpandedChange = { isExpanded = it },
            onSend = {
                if (input.trim().isNotEmpty()) {
                    val uid = FirebaseClient.currentUid ?: return@FloatingBottomBar
                    val chatId = if (uid < receiver.uid) "${uid}_${receiver.uid}" else "${receiver.uid}_$uid"
                    
                    val encrypted = DesktopEncryptionUtils.encrypt(input.trim())
                    val msg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        senderId = uid,
                        receiverId = receiver.uid,
                        message = encrypted,
                        timestamp = System.currentTimeMillis()
                    )
                    
                    FirebaseClient.push("chats/$chatId", msg) { key ->
                        if (key != null) {
                            // Send trigger
                            val trigger = mapOf(
                                "type" to "message",
                                "senderId" to uid,
                                "senderName" to receiver.name, // Display receiver's name
                                "messageText" to input.trim(),
                                "receiverId" to receiver.uid,
                                "chatId" to chatId,
                                "timestamp" to System.currentTimeMillis()
                            )
                            FirebaseClient.push("notification_triggers", trigger) {}
                        }
                    }
                    input = ""
                }
            },
            onSelectLocalPhoto = {
                scope.launch(Dispatchers.IO) {
                    val fileDialog = java.awt.FileDialog(
                        null as java.awt.Frame?,
                        "Select Image to Send",
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
                            
                            val uid = FirebaseClient.currentUid ?: return@launch
                            val chatId = if (uid < receiver.uid) "${uid}_${receiver.uid}" else "${receiver.uid}_$uid"
                            
                            val msg = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                senderId = uid,
                                receiverId = receiver.uid,
                                message = "img:$base64",
                                timestamp = System.currentTimeMillis()
                            )
                            FirebaseClient.push("chats/$chatId", msg) {}
                        }
                    }
                }
            }
        )
    }
}

// Chat message bubble renderer
@Composable
fun ChatBubble(msg: ChatMessage, isMe: Boolean) {
    val isDark = isSystemInDarkTheme()
    val decryptedMessage = DesktopEncryptionUtils.decrypt(msg.message)
    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isMe) 20.dp else 6.dp,
        bottomEnd = if (isMe) 4.dp else 20.dp
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .clip(bubbleShape)
                .background(
                    if (isMe) (if (isDark) Color.White.copy(0.2f) else Color(0xFFC8E6C9))
                    else (if (isDark) Color.White.copy(0.1f) else Color(0xFFFFECB3).copy(alpha = 0.4f))
                )
                .border(1.dp, Color.White.copy(alpha = if (isDark) 0.1f else 0.8f), bubbleShape)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (decryptedMessage.startsWith("img:")) {
                var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(decryptedMessage) {
                    withContext(Dispatchers.IO) {
                        try {
                            val base64Data = decryptedMessage.substring(4)
                            val bytes = Base64.getDecoder().decode(base64Data.replace("\\s".toRegex(), ""))
                            imageBitmap = loadImageBitmap(bytes.inputStream())
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap!!,
                        contentDescription = "Image message",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else {
                Text(
                    text = parseMarkdown(decryptedMessage),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    color = if (isDark) Color.White else Color(0xFF2C2C2C)
                )
            }
        }
    }
}

// Morphing bottom floating navigation bar input
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloatingBottomBar(
    text: String,
    onTextChange: (String) -> Unit,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSend: () -> Unit,
    onSelectLocalPhoto: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val focusRequester = remember { FocusRequester() }

    val animProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    val horizontalPadding = (180 * (1f - animProgress)).dp + 16.dp
    val barHeight = (50 + (10 * animProgress)).dp

    val bgColor = if (isDark) {
        androidx.compose.ui.graphics.lerp(Color(0xFF1A1A1A), Color(0xFF121212).copy(0.7f), animProgress)
    } else {
        androidx.compose.ui.graphics.lerp(Color(0xFFFFF2D9), Color.White.copy(0.75f), animProgress)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 12.dp)
            .height(barHeight)
            .clip(RoundedCornerShape(30.dp))
            .background(bgColor)
            .border(
                width = 2.dp,
                color = Color(0xFFFFF2D9).copy(alpha = if (isExpanded) 0.8f else 0.2f),
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
            val iconColor = if (isDark) Color.White else Color.Black
            if (animProgress < 0.5f) {
                // Short Buttons Icons mode
                IconButton(
                    onClick = onSelectLocalPhoto,
                    modifier = Modifier.size(36.dp)
                ) {
                    Image(
                        painter = painterResource("drawable/camera.xml"),
                        contentDescription = "Camera",
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(32.dp))

                IconButton(
                    onClick = { onExpandedChange(true) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Image(
                        painter = painterResource("drawable/keyboard_keys_25dp_1f1f1f_fill0_wght400_grad0_opsz24.xml"),
                        contentDescription = "Expand keyboard",
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                // Input text field Mode
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back/Collapse button
                    IconButton(
                        onClick = { onExpandedChange(false) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color.White.copy(0.1f) else Color.White.copy(0.35f))
                    ) {
                        Image(
                            painter = painterResource("drawable/arrow___down_2.xml"),
                            contentDescription = "Collapse",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        placeholder = { Text("Encrypted message...", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true
                    )

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }

                    // Send Button
                    val sendBtnColor = if (text.isNotBlank()) Color.Black else Color.Black.copy(0.15f)
                    IconButton(
                        onClick = onSend,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(sendBtnColor)
                    ) {
                        Image(
                            painter = painterResource("drawable/send.xml"),
                            contentDescription = "Send",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// SOS Countdown Screen
@Composable
fun SOSOverlay(
    onTimeout: () -> Unit,
    onCancel: () -> Unit
) {
    var countdown by remember { mutableStateOf(5) }
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFF5252).copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "EMERGENCY SOS ACTIVE",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Loomi is broadcasting emergency telemetry to nodes...",
                color = Color.White.copy(0.7f),
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = countdown.toString(),
                fontSize = 120.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFFF5252)),
                modifier = Modifier.height(54.dp).width(200.dp),
                shape = RoundedCornerShape(27.dp)
            ) {
                Text(
                    text = "CANCEL ALERT",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

// Helper: Profile Image Loader
@Composable
fun ProfileImage(imagePath: String, size: androidx.compose.ui.unit.Dp) {
    var bitmap by remember(imagePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(imagePath) {
        if (imagePath.startsWith("data:image")) {
            withContext(Dispatchers.IO) {
                bitmap = try {
                    val clean = imagePath.substringAfter("base64,")
                    val bytes = Base64.getDecoder().decode(clean.replace("\\s".toRegex(), ""))
                    loadImageBitmap(bytes.inputStream())
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        } else if (imagePath.startsWith("http")) {
            withContext(Dispatchers.IO) {
                bitmap = try {
                    val bytes = URL(imagePath).openStream().readBytes()
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
            .size(size)
            .clip(CircleShape)
            .background(Color.Gray.copy(0.15f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (imagePath.isNotEmpty() && !imagePath.startsWith("data:") && !imagePath.startsWith("http")) {
            // Load selected memoji from local resources
            Image(
                painter = painterResource(imagePath),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(2.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Text("👤", fontSize = (size.value * 0.45).sp)
        }
    }
}

// Parsing utilities
fun parseUserMap(uid: String, map: Map<String, Any>): SnapUser {
    val name = map["name"] as? String ?: "Unknown"
    val status = map["status"] as? String ?: "Offline"
    val lastSeen = (map["lastSeen"] as? Double)?.toLong() ?: (map["lastSeen"] as? Long) ?: 0L
    val imageName = map["imageName"] as? String ?: ""
    return SnapUser(
        uid = uid,
        name = name,
        status = status,
        lastSeen = lastSeen,
        imageName = imageName
    )
}

fun formatLastSeen(lastSeen: Long): String {
    if (lastSeen <= 0) return "Never"
    val diff = System.currentTimeMillis() - lastSeen
    val minutes = diff / (60 * 1000)
    val hours = diff / (60 * 60 * 1000)
    val days = diff / (24 * 60 * 60 * 1000)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}

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
