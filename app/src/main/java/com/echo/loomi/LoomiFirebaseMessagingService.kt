package com.echo.loomi

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class LoomiFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")
        // Update token in database for the current user
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseDatabase.getInstance("https://echo-loomi-app-default-rtdb.firebaseio.com/")
                .reference.child("users").child(uid).child("fcmToken").setValue(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        // Handle data messages even when app is closed
        val data = message.data
        if (data.isNotEmpty()) {
            val senderName = data["senderName"] ?: "New Message"
            val messageText = data["messageText"] ?: ""
            val senderId = data["senderId"] ?: ""
            val senderImage = data["senderImage"] ?: ""
            val chatId = data["chatId"] ?: ""

            NotificationHelper.showMessageNotification(
                applicationContext,
                senderId,
                senderName,
                senderImage,
                messageText,
                chatId
            )
        }
    }
}
