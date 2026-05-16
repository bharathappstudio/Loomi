package com.echo.loomi

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Immutable
data class MusicTrack(
    val trackId: Long,
    val trackName: String,
    val artistName: String,
    val previewUrl: String,
    val artworkUrl: String
)

@Composable
fun CameraScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    var currentUserImage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val database = FirebaseDatabase.getInstance("https://echo-loomi-app-default-rtdb.firebaseio.com/").reference
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid ?: return@LaunchedEffect

        // Fetch current user's asset image name
        database.child("users").child(uid).child("imageName").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentUserImage = snapshot.getValue(String::class.java) ?: ""
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFEFB600))) {
        if (hasCameraPermission) {
            CameraView(
                onBack = onBack,
                onImageCaptured = { uri ->
                    Toast.makeText(context, "Image saved to gallery", Toast.LENGTH_SHORT).show()
                },
                currentUserImageAsset = currentUserImage
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Camera permission is required", color = Color.Black)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraView(onBack: () -> Unit, onImageCaptured: (Uri) -> Unit, currentUserImageAsset: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var selectedPreviewUri by remember { mutableStateOf<Uri?>(null) }
    
    var showMusicSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val resolutionSelector = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .build()
    }

    val preview = remember(resolutionSelector) {
        Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
    }

    val imageCapture: ImageCapture = remember(flashMode, resolutionSelector) {
        ImageCapture.Builder()
            .setFlashMode(flashMode)
            .setResolutionSelector(resolutionSelector)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(lensFacing, flashMode) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            imageCapture.flashMode = flashMode

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                preview.surfaceProvider = previewView.surfaceProvider
            } catch (exc: Exception) {
                Log.e("CameraView", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFFFBF6))) {

        // --- CENTERED OVERLAY GROUP ---
        Box(modifier = Modifier.align(Alignment.Center)) {
            // 1. Centered Camera Circle
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
            ) {
                if (selectedPreviewUri != null) {
                    coil.compose.AsyncImage(
                        model = selectedPreviewUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                } else {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 2. Top-Right Pill (Icons)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 20.dp, y = (-10).dp)
                    .clip(RoundedCornerShape(25.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(25.dp))
                    .background(Color(0xFFFFE0B2))
                    .padding(horizontal = 30.dp, vertical = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(R.drawable.call), contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Icon(painterResource(R.drawable.video), contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
            }

            // 3. Bottom-Left Rounded Square (Music)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-15).dp, y = 15.dp)
                    .size(65.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFAB91))
                    .clickable { showMusicSheet = true }
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.musicnote),
                    contentDescription = null,
                    tint = Color(0xFFFFFFFF),
                    modifier = Modifier.size(25.dp)
                )
            }
        }
        // --- END CENTERED OVERLAY GROUP ---

        // Top Controls (Close and Flash)
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopStart)
                .background(Color.Gray.copy(alpha = 0.1f), CircleShape)
        ) {
            Icon(painterResource(R.drawable.arrow_left), contentDescription = "Close", tint = Color.Black, modifier = Modifier.size(20.dp))
        }

        IconButton(
            onClick = {
                flashMode = when (flashMode) {
                    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                    else -> ImageCapture.FLASH_MODE_OFF
                }
            },
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopEnd)
                .background(Color.Gray.copy(alpha = 0.1f), CircleShape)
        ) {
            Icon(
                painterResource(R.drawable.flass),
                contentDescription = "Flash",
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
        }

        // Bottom Controls (Gallery, Capture, Flip)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 3 Small Circles Above Capture Button
            Row(
                modifier = Modifier.padding(bottom = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. User selected profile image
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Gray.copy(alpha = 0.1f))
                        .clickable {
                            if (currentUserImageAsset.isNotEmpty()) {
                                selectedPreviewUri = Uri.parse("file:///android_asset/$currentUserImageAsset")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    coil.compose.AsyncImage(
                        model = "file:///android_asset/$currentUserImageAsset",
                        contentDescription = "Profile",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }

                // 2. Camera icon
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .clickable { selectedPreviewUri = null },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.camera),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = Color.White
                    )
                }

                // 3. Google user profile
                val currentUser = FirebaseAuth.getInstance().currentUser
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Gray.copy(alpha = 0.1f))
                        .clickable {
                            currentUser?.photoUrl?.let { 
                                val highResUrl = it.toString().replace("s96-c", "s4000")
                                selectedPreviewUri = Uri.parse(highResUrl) 
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    coil.compose.AsyncImage(
                        model = currentUser?.photoUrl?.toString()?.replace("s96-c", "s384"),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { Toast.makeText(context, "Gallery feature coming soon", Toast.LENGTH_SHORT).show() },
                    modifier = Modifier.size(50.dp).background(Color.Gray.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(painterResource(R.drawable.image), contentDescription = "Gallery", tint = Color.Black, modifier = Modifier.size(20.dp))
                }

                // Capture button
                Box(
                    modifier = Modifier
                        .size(85.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(2.dp, Color(0xFFFFE082), CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFA5D6A7))
                        .clickable {
                            if (selectedPreviewUri != null) {
                                onImageCaptured(selectedPreviewUri!!)
                            } else {
                                takePhoto(context, imageCapture, cameraExecutor, onImageCaptured)
                            }
                        }
                )

                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    modifier = Modifier.size(50.dp).background(Color.Gray.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(painterResource(R.drawable.camera), contentDescription = "Flip", tint = Color.Black, modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    if (showMusicSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMusicSheet = false },
            sheetState = sheetState,
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            var musicSearchQuery by remember { mutableStateOf("") }
            var musicResults by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
            var isSearching by remember { mutableStateOf(false) }
            val mediaPlayer = remember { MediaPlayer() }
            var currentPlayingUrl by remember { mutableStateOf<String?>(null) }

            DisposableEffect(Unit) {
                onDispose {
                    mediaPlayer.release()
                }
            }

            LaunchedEffect(musicSearchQuery) {
                if (musicSearchQuery.length > 2) {
                    isSearching = true
                    musicResults = searchMusic(musicSearchQuery)
                    isSearching = false
                } else if (musicSearchQuery.isEmpty()) {
                    musicResults = emptyList()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Add Music",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 15.dp)
                )
                
                TextField(
                    value = musicSearchQuery,
                    onValueChange = { musicSearchQuery = it },
                    placeholder = { Text("Search songs or artists...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp)),
                    leadingIcon = { 
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Black)
                        } else {
                            Icon(painterResource(R.drawable.search), null, modifier = Modifier.size(20.dp), tint = Color.Black)
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF5F5F5),
                        unfocusedContainerColor = Color(0xFFF5F5F5),
                        disabledContainerColor = Color(0xFFF5F5F5),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                if (musicResults.isEmpty() && !isSearching) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Search for your favorite tracks", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        items(musicResults, key = { it.trackId }) { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (currentPlayingUrl == track.previewUrl) {
                                            mediaPlayer.stop()
                                            mediaPlayer.reset()
                                            currentPlayingUrl = null
                                        } else {
                                            mediaPlayer.stop()
                                            mediaPlayer.reset()
                                            mediaPlayer.setAudioAttributes(
                                                AudioAttributes.Builder()
                                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                                    .build()
                                            )
                                            mediaPlayer.setDataSource(track.previewUrl)
                                            mediaPlayer.prepareAsync()
                                            mediaPlayer.setOnPreparedListener { it.start() }
                                            currentPlayingUrl = track.previewUrl
                                        }
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).background(Color.LightGray)) {
                                    coil.compose.AsyncImage(
                                        model = track.artworkUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (currentPlayingUrl == track.previewUrl) {
                                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.4f)), contentAlignment = Alignment.Center) {
                                            Icon(painterResource(R.drawable.videocam), null, tint = Color.White, modifier = Modifier.size(24.dp))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track.trackName, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(track.artistName, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1)
                                }
                                if (currentPlayingUrl == track.previewUrl) {
                                    Icon(painterResource(R.drawable.call), null, modifier = Modifier.size(20.dp), tint = Color.Green)
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

private suspend fun searchMusic(query: String): List<MusicTrack> = withContext(Dispatchers.IO) {
    val results = mutableListOf<MusicTrack>()
    try {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = URL("https://itunes.apple.com/search?term=$encodedQuery&media=music&limit=20")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        val resultArray = json.getJSONArray("results")
        
        for (i in 0 until resultArray.length()) {
            val item = resultArray.getJSONObject(i)
            results.add(
                MusicTrack(
                    trackId = item.getLong("trackId"),
                    trackName = item.optString("trackName", "Unknown"),
                    artistName = item.optString("artistName", "Unknown"),
                    previewUrl = item.optString("previewUrl", ""),
                    artworkUrl = item.optString("artworkUrl100", "")
                )
            )
        }
    } catch (e: Exception) {
        Log.e("CameraView", "Music search failed", e)
    }
    results
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: ExecutorService,
    onImageCaptured: (Uri) -> Unit
) {
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
        .format(System.currentTimeMillis())
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Loomi")
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions
        .Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
        .build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraView", "Photo capture failed: ${exc.message}", exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                output.savedUri?.let { onImageCaptured(it) }
            }
        }
    )
}
