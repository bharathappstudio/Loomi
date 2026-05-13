package com.echo.loomi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput

object NotificationHelper {
    private const val CHANNEL_ID = "loomi_messages"
    private const val CHANNEL_NAME = "Loomi Messages"
    const val KEY_TEXT_REPLY = "key_text_reply"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new messages in Loomi"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showMessageNotification(
        context: Context,
        senderId: String,
        senderName: String,
        senderImage: String,
        messageText: String,
        chatId: String
    ) {
        val notificationId = senderId.hashCode()

        // Intent to open MessageActivity
        val intent = Intent(context, MessageActivity::class.java).apply {
            putExtra("receiverUid", senderId)
            putExtra("receiverName", senderName)
            putExtra("receiverImage", senderImage)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // RemoteInput for Direct Reply
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).run {
            setLabel("Type your message...")
            build()
        }

        // Action for Reply
        val replyIntent = Intent(context, DirectReplyReceiver::class.java).apply {
            putExtra("receiverUid", senderId)
            putExtra("chatId", chatId)
            putExtra("notificationId", notificationId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context, notificationId, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.send, "Reply", replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo) // Make sure this exists
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .addAction(replyAction)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }
}
