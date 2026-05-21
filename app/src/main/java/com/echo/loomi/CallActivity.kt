package com.echo.loomi

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.echo.loomi.ui.theme.LoomiTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class CallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        enableEdgeToEdge()

        val receiverUid = intent.getStringExtra("receiverUid") ?: ""
        val receiverName = intent.getStringExtra("receiverName") ?: ""
        val receiverImage = intent.getStringExtra("receiverImage") ?: ""
        val isIncoming = intent.getBooleanExtra("isIncoming", false)

        setContent {
            LoomiTheme {
                CallScreenContent(
                    receiverUid = receiverUid,
                    receiverName = receiverName,
                    receiverImage = receiverImage,
                    isIncoming = isIncoming,
                    onFinish = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreenContent(
    receiverUid: String,
    receiverName: String,
    receiverImage: String,
    isIncoming: Boolean,
    onFinish: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val currentUid = auth.currentUser?.uid ?: return
    val database = FirebaseDatabase.getInstance("https://echo-loomi-app-default-rtdb.firebaseio.com/").reference
    
    var callState by remember { mutableStateOf(if (isIncoming) CallState.INCOMING else CallState.OUTGOING) }
    
    // Listen for call changes
    LaunchedEffect(Unit) {
        val targetUid = if (isIncoming) currentUid else receiverUid
        database.child("calls").child(targetUid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    callState = CallState.ENDED
                    onFinish()
                } else {
                    val status = snapshot.child("status").getValue(String::class.java)
                    if (status == "accepted") {
                        callState = CallState.ONGOING
                    } else if (status == "ended" || status == "declined") {
                        onFinish()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val decryptedName = if (isIncoming) EncryptionUtils.decrypt(receiverName) else receiverName
        val decryptedImage = if (isIncoming) EncryptionUtils.decrypt(receiverImage) else receiverImage

        CallBottomSheet(
            receiverName = decryptedName,
            receiverImage = decryptedImage,
            callState = callState,
            onAccept = {
                database.child("calls").child(currentUid).child("status").setValue("accepted")
                callState = CallState.ONGOING
            },
            onDecline = {
                endCall(currentUid)
                onFinish()
            },
            onEnd = {
                if (isIncoming) endCall(currentUid) else endCall(receiverUid)
                onFinish()
            },
            onDismiss = {
                // If it's an activity, dismiss might mean ending the call
                if (callState != CallState.ONGOING) {
                    if (isIncoming) endCall(currentUid) else endCall(receiverUid)
                    onFinish()
                }
            }
        )
    }
}
