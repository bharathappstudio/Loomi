package com.echo.loomi

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Setting : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        prefs = getSharedPreferences("echo_prefs", MODE_PRIVATE)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            MaterialTheme {
                SettingUI(
                    onLogout = {
                        FirebaseAuth.getInstance().signOut()
                        googleSignInClient.signOut().addOnCompleteListener {
                            prefs.edit().clear().apply()
                            startActivity(
                                Intent(this, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                            )
                            finish()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun SettingUI(onLogout: () -> Unit) {

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val user = FirebaseAuth.getInstance().currentUser
    val name = user?.displayName ?: "Unknown User"
    val email = user?.email ?: ""

    val photoUrl = user?.photoUrl
        ?.toString()
        ?.replace("s96-c", "s4096-c")
        ?.replace("s400-c", "s4096-c")

    // --- 3-COLOR GRADIENT LOADING LOGIC ---
    var isProfileLoading by remember { mutableStateOf(true) }

    val googleColors = listOf(
        Color(0xFF8AB4F8), // Medium Blue
        Color(0xFFF28B82), // Medium Red
        Color(0xFFFDD663), // Medium Yellow
        Color(0xFF81C995), // Medium Green
        Color(0xFF669DF6)  // Medium Secondary Blue
    )

    var colorIndex1 by remember { mutableIntStateOf(0) }
    var colorIndex2 by remember { mutableIntStateOf(1) }
    var colorIndex3 by remember { mutableIntStateOf(2) }

    LaunchedEffect(isProfileLoading) {
        if (isProfileLoading) {
            launch {
                while (true) {
                    delay(700)
                    colorIndex1 = (colorIndex1 + 1) % googleColors.size
                    colorIndex2 = (colorIndex2 + 1) % googleColors.size
                    colorIndex3 = (colorIndex3 + 1) % googleColors.size
                }
            }
            delay(4000)
            isProfileLoading = false
        }
    }

    // Animated colors assigned to c1, c2, c3
    val c1 by animateColorAsState(googleColors[colorIndex1], animationSpec = tween(600), label = "c1")
    val c2 by animateColorAsState(googleColors[colorIndex2], animationSpec = tween(600), label = "c2")
    val c3 by animateColorAsState(googleColors[colorIndex3], animationSpec = tween(600), label = "c3")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFFFF))
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(scrollState)
            .navigationBarsPadding()
            .padding(16.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = null,
                modifier = Modifier.clickable { (context as? ComponentActivity)?.finish() }
            )
            Spacer(Modifier.width(16.dp))
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(28.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {

            Box(
                modifier = Modifier.size(55.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isProfileLoading,
                    transitionSpec = {
                        (fadeIn(tween(600)) + scaleIn(initialScale = 0.8f))
                            .togetherWith(fadeOut(tween(600)))
                    },
                    label = ""
                ) { loading ->
                    if (loading) {
                        // ✅ FIX: Using c1, c2, c3 which match the defined variables above
                        LoadingIndicator(
                            modifier = Modifier
                                .size(56.dp)
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.linearGradient(
                                            colors = listOf(c1, c2, c3)
                                        ),
                                        blendMode = BlendMode.SrcAtop
                                    )
                                },
                            color = Color.White
                        )
                    } else {
                        if (photoUrl != null) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFFF9FBE7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(name.first().toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            Column {
                Text(name, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(email, fontSize = 14.sp, color = Color(0xFF6B6B6B))
            }
        }

        Spacer(Modifier.height(20.dp))

        // Bubble Animation Logic
        val transition = rememberInfiniteTransition(label = "bubbles")
        val up1 by transition.animateFloat(-120f, 120f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "")
        val up2 by transition.animateFloat(120f, -120f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "")
        val side1 by transition.animateFloat(-50f, 50f, infiniteRepeatable(tween(8000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "")
        val side2 by transition.animateFloat(50f, -50f, infiniteRepeatable(tween(10000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(2.dp, Color.White, RoundedCornerShape(20.dp))
                .background(Color(0xFFE8F5E9))
                // .clickable { context.startActivity(Intent(context, EchoWeb::class.java)) }
        ) {
            val bubbleColor = Color(0xFFFFFFFF).copy(alpha = 0.75f)
            Box(Modifier.size(22.dp).offset(30.dp + side1.dp, up1.dp).background(bubbleColor, CircleShape))
            Box(Modifier.size(18.dp).offset(70.dp, up2.dp).background(bubbleColor, CircleShape))
            Box(Modifier.size(14.dp).offset(120.dp + side2.dp, up1.dp + 30.dp).background(bubbleColor, CircleShape))
            Box(Modifier.size(26.dp).offset(160.dp, up2.dp + 50.dp).background(bubbleColor, CircleShape))

            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Get the best of Echo 🫐", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text("Higher limits, cloud storage, in Realtime Database Echo built in Ai", fontSize = 13.sp, color = Color(0xCC4E4E4E))
            }
        }

        Spacer(Modifier.height(24.dp))

        SettingRow("Echo App Realtime Database", true) { /* context.startActivity(Intent(context, DataBackupScreen::class.java)) */ }
        SettingRow("Permissions") { /* context.startActivity(Intent(context, PermissionsActivity::class.java)) */ }
        SettingRow("APP-Release") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://gitlab.com/jarvisvbharath11/Loomi/-/blob/release-apk/app/release/app-release.apk?ref_type=heads"))) }
        SettingRow("Give feedback") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cal.com/ui-studio13"))) }
        SettingRow("Call to Developer") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("tel:+917094589909"))) }
        SettingRow("About") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/bharathappstudio"))) }
        SettingRow("Updating Echo") { /* context.startActivity(Intent(context, EchoActivity::class.java)) */ }

        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onLogout) { Text("Sign out") }
        }
    }
}

@Composable
fun SettingRow(title: String, isNew: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 16.sp)
        if (isNew) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCCA5D6A7))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("NEW", fontSize = 11.sp)
            }
            Spacer(Modifier.width(8.dp))
        }
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color(0x80171616))
    }
}
