package com.echo.loomi

import android.app.Activity
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.text.format.DateUtils
import android.util.Base64
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StoryScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    SideEffect {
        val window = (context as? Activity)?.window
        window?.statusBarColor = android.graphics.Color.TRANSPARENT
        window?.navigationBarColor = android.graphics.Color.TRANSPARENT
    }
    BackHandler(onBack = onBack)
    val stories = remember { mutableStateListOf<Story>() }
    var selectedStoryForSheet by remember { mutableStateOf<Story?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }

    val filteredStories = remember(searchQuery, stories.toList()) {
        if (searchQuery.isEmpty()) {
            stories
        } else {
            stories.filter {
                it.userName.contains(searchQuery, ignoreCase = true) ||
                        it.songName.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    val database = FirebaseDatabase.getInstance("https://echo-loomi-app-default-rtdb.firebaseio.com/").reference
    val scope = rememberCoroutineScope()
    val mediaPlayer = remember { MediaPlayer() }
    var currentPlayingId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    LaunchedEffect(Unit) {
        database.child("stories").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Get current IDs from DB to remove deleted stories from UI
                val dbStoryIds = snapshot.children.mapNotNull { it.key }
                stories.removeAll { it.id !in dbStoryIds }

                val newStoriesList = mutableListOf<Story>()
                for (child in snapshot.children) {
                    val story = child.getValue(Story::class.java)
                    if (story != null) {
                        newStoriesList.add(story)
                    }
                }

                // Ensure only one story per user in the list (most recent one)
                val uniqueStories = newStoriesList.groupBy { it.uid }
                    .map { it.value.maxBy { s -> s.timestamp } }
                    .sortedByDescending { it.timestamp }

                uniqueStories.forEach { story ->
                    database.child("users").child(story.uid).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(userSnapshot: DataSnapshot) {
                            val userName = userSnapshot.child("name").getValue(String::class.java) ?: "Unknown"
                            val userProfileImage = userSnapshot.child("imageName").getValue(String::class.java) ?: ""

                            val index = stories.indexOfFirst { it.uid == story.uid }
                            if (index != -1) {
                                stories[index] = story.copy(userName = userName, userProfileImage = userProfileImage)
                            } else {
                                stories.add(story.copy(userName = userName, userProfileImage = userProfileImage))
                                stories.sortByDescending { it.timestamp }
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    if (dragAmount > 30) { // More sensitive right swipe
                        onBack()
                        change.consume()
                    }
                }
            },
        containerColor = Color(0xFFFFFFFF),
        topBar = {
            Column(modifier = Modifier.statusBarsPadding().fillMaxWidth().background(Color.Transparent)) {
                // Logo at the top
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 50.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.welcom_to__loomi),
                        contentDescription = "Loomi Logo",
                        modifier = Modifier.height(80.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(60.dp))
                        .background(Color.White.copy(alpha = 0.7f))
                        .border(1.5.dp, Color(0xFFC8E6C9), RoundedCornerShape(60.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.search),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.Gray
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "Search stories or songs...",
                                color = Color.Gray,
                                fontSize = 15.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Color.Black
                        ),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp)
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(30.dp)) {
                            Icon(
                                painter = painterResource(id = R.drawable.arrow___down_2), // Reusing existing icon for "clear" feel or just something to tap
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp).graphicsLayer(rotationZ = 45f),
                                tint = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Vertical 3-column grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, start = 12.dp, end = 40.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                items(filteredStories, key = { it.id }) { story ->
                    StoryItem(
                        story = story,
                        isPlaying = currentPlayingId == story.id,
                        onPlayToggle = {
                            if (currentPlayingId == story.id) {
                                mediaPlayer.stop()
                                mediaPlayer.reset()
                                currentPlayingId = null
                            } else {
                                currentPlayingId = story.id
                                scope.launch {
                                    playStoryMusic(story, mediaPlayer) {
                                        currentPlayingId = null
                                    }
                                }
                            }
                        },
                        onStoryClick = {
                            selectedStoryForSheet = story
                        }
                    )
                }
            }
            // Bottom gradient to match MainActivity
            Box(modifier = Modifier.fillMaxWidth().height(250.dp).align(Alignment.BottomCenter).background(brush = Brush.verticalGradient(colors = listOf(Color.Transparent, Color(0xFFFFFBF6).copy(alpha = 0.9f)))))
        }

        if (selectedStoryForSheet != null) {
            StoryBottomSheet(
                story = selectedStoryForSheet!!,
                onDismiss = { selectedStoryForSheet = null }
            )
        }
    }
}

@Composable
fun StoryItem(
    story: Story,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onStoryClick: () -> Unit
) {
    val bitmap = remember(story.image) {
        try {
            val imageBytes = Base64.decode(story.image, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // --- CENTERED OVERLAY GROUP (Scaled Down) ---
        Box(modifier = Modifier.size(115.dp)) {
            // 1. Centered Camera Circle (Now Story Image)
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.Center)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(2.dp, if (isPlaying) Color(0xFF81C995) else Color.White, CircleShape)
                    .clickable { onStoryClick() }
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Gray))
                }
            }


            // 3. Bottom-Left Rounded Square (Music)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = -4.dp, y = 4.dp)
                    .size(35.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.5.dp, Color.White, RoundedCornerShape(8.dp))
                    .background(if (isPlaying) Color(0xFF81C995) else Color(0xFFFFAB91))
                    .clickable { onPlayToggle() }
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(if (isPlaying) R.drawable.videocam else R.drawable.musicnote),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private suspend fun playStoryMusic(story: Story, mediaPlayer: MediaPlayer, onComplete: () -> Unit) {
    if (story.songId == 0L) return

    withContext(Dispatchers.IO) {
        try {
            val url = URL("https://itunes.apple.com/lookup?id=${story.songId}")
            val connection = url.openConnection() as HttpURLConnection
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val results = json.getJSONArray("results")
            if (results.length() > 0) {
                val previewUrl = results.getJSONObject(0).getString("previewUrl")

                withContext(Dispatchers.Main) {
                    mediaPlayer.stop()
                    mediaPlayer.reset()
                    mediaPlayer.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    mediaPlayer.setDataSource(previewUrl)
                    mediaPlayer.prepareAsync()
                    mediaPlayer.setOnPreparedListener {
                        it.isLooping = false
                        it.start()
                    }
                    mediaPlayer.setOnCompletionListener {
                        onComplete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("StoryScreen", "Failed to play music", e)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryBottomSheet(
    story: Story,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var songArtworkUrl by remember { mutableStateOf<String?>(null) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    val sheetMediaPlayer = remember { MediaPlayer() }

    val bitmap = remember(story.image) {
        try {
            val imageBytes = Base64.decode(story.image, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    val timeAgo = remember(story.timestamp) {
        if (story.timestamp == 0L) ""
        else DateUtils.getRelativeTimeSpanString(story.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    }

    LaunchedEffect(story.songId) {
        if (story.songId == 0L) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://itunes.apple.com/lookup?id=${story.songId}")
                val connection = url.openConnection() as HttpURLConnection
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val results = json.getJSONArray("results")
                if (results.length() > 0) {
                    val item = results.getJSONObject(0)
                    previewUrl = item.optString("previewUrl")
                    songArtworkUrl = item.optString("artworkUrl100").replace("100x100bb.jpg", "600x600bb.jpg")
                }
            } catch (e: Exception) {
                Log.e("StoryScreen", "Failed to fetch song details", e)
            }
        }
    }

    LaunchedEffect(previewUrl) {
        previewUrl?.let { url ->
            try {
                sheetMediaPlayer.apply {
                    stop()
                    reset()
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(url)
                    isLooping = true
                    prepareAsync()
                    setOnPreparedListener { start() }
                }
            } catch (e: Exception) {
                Log.e("StoryScreen", "Failed to play music in sheet", e)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            sheetMediaPlayer.release()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = Color(0xE6FFF6DE)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 60.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- CENTERED STORY UI (Larger Version) ---
            Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .border(3.dp, Color.White, CircleShape)
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // Top-Right Pill
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = 10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.5.dp, Color.White, RoundedCornerShape(20.dp))
                        .background(Color(0xFFFFE0B2))
                        .padding(horizontal = 30.dp, vertical = 15.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painterResource(R.drawable.call), null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Icon(painterResource(R.drawable.video), null, tint = Color.Black, modifier = Modifier.size(16.dp))
                }

                // Bottom-Left Music Box
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = (-0.dp), y = (-10.dp))
                        .size(60.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .border(2.dp, Color.White, RoundedCornerShape(15.dp))
                        .background(Color(0xFFFFAB91))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(R.drawable.musicnote), null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // --- SONG INFO ROW: Name (Left) | Image (Right) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- UPLOAD TIME (Bottom) ---
            Text(
                text = "Uploaded $timeAgo",
                fontSize = 13.sp,
                color = Color.Black.copy(alpha = 0.7f)
            )
        }
    }
}