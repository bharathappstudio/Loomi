package com.echo.loomi

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
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
import androidx.compose.ui.graphics.graphicsLayer
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
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun CameraScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    val usersList = remember { mutableStateListOf<SnapUser>() }
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

        database.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                usersList.clear()
                for (userSnapshot in snapshot.children) {
                    val otherUid = userSnapshot.child("uid").getValue(String::class.java) ?: ""
                    if (otherUid != uid) {
                        val name = userSnapshot.child("name").getValue(String::class.java) ?: "Unknown"
                        val imageName = userSnapshot.child("imageName").getValue(String::class.java) ?: ""
                        usersList.add(SnapUser(otherUid, name, imageName = imageName))
                    }
                }
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
            if (capturedImageUri != null) {
                ImagePreviewScreen(
                    uri = capturedImageUri!!,
                    users = usersList,
                    onBack = { capturedImageUri = null },
                    onSend = { user ->
                        sendImageToUser(context, capturedImageUri!!, user)
                        capturedImageUri = null
                        onBack()
                    }
                )
            } else {
                CameraView(
                    onBack = onBack,
                    onImageCaptured = { capturedImageUri = it },
                    currentUserImageAsset = currentUserImage
                )
            }
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

@Composable
fun CameraView(onBack: () -> Unit, onImageCaptured: (Uri) -> Unit, currentUserImageAsset: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var selectedPreviewUri by remember { mutableStateOf<Uri?>(null) }

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
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                scaleX = -1f
                            }
                        }
                    )
                }
            }

            // 2. Top-Right Pill (Like/Dislike)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 20.dp, y = (-10).dp)
                    .clip(RoundedCornerShape(25.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(25.dp)) // Adds the white border
                    .background(Color(0xFFFFE0B2)) // Peach/Light Orange
                    .padding(horizontal = 30.dp, vertical = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(R.drawable.call), contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Icon(painterResource(R.drawable.camera), contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
            }

            // 3. Bottom-Left Rounded Square (Pause)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-15).dp, y = 15.dp)
                    .size(65.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(16.dp)) // Adds the white border
                    .background(Color(0xFFFFAB91)) // Pink
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.camera),
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
                painterResource(R.drawable.setting_4),
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
                                // Request 4K/Highest resolution from Google
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
                    Icon(painterResource(R.drawable.search), contentDescription = "Gallery", tint = Color.Black, modifier = Modifier.size(20.dp))
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePreviewScreen(
    uri: Uri,
    users: List<SnapUser>,
    onBack: () -> Unit,
    onSend: (SnapUser) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFD0F8CE))) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.statusBarsPadding().height(20.dp))
            
            // Back button
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.Gray.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(painterResource(R.drawable.arrow_left), contentDescription = "Back", tint = Color.Black, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Round Photo Preview
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .border(3.dp, Color.Black, CircleShape)
            ) {
                coil.compose.AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
            
            Text("Send to friends", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            
            Spacer(modifier = Modifier.height(10.dp))

            // User List for sending
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)
            ) {
                items(users) { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSend(user) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(45.dp).clip(CircleShape).background(Color(0xFFF5F5F5))) {
                            coil.compose.AsyncImage(
                                model = "file:///android_asset/${user.imageName}",
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(user.name, style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(painterResource(R.drawable.send), contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                    }
                }
            }
        }
    }
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

private fun sendImageToUser(context: Context, uri: Uri, user: SnapUser) {
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val database = FirebaseDatabase.getInstance("https://echo-loomi-app-default-rtdb.firebaseio.com/").reference
    val receiverUid = user.uid
    val chatId = if (currentUid < receiverUid) "${currentUid}_$receiverUid" else "${receiverUid}_$currentUid"

    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
        
        val msgId = database.child("chats").child(chatId).push().key ?: ""
        val message = ChatMessage(
            id = msgId,
            senderId = currentUid,
            receiverId = receiverUid,
            message = "img:$base64Image",
            timestamp = System.currentTimeMillis()
        )
        database.child("chats").child(chatId).child(msgId).setValue(message)
            .addOnSuccessListener {
                Toast.makeText(context, "Image sent to ${user.name}", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to send image", Toast.LENGTH_SHORT).show()
            }
    } catch (e: Exception) {
        Log.e("CameraView", "Error sending image", e)
        Toast.makeText(context, "Error processing image", Toast.LENGTH_SHORT).show()
    }
}
