package com.echo.loomi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream

object NotificationHelper {
    private const val CHANNEL_ID = "loomi_messages"
    private const val CHANNEL_NAME = "Loomi Messages"
    private const val CALL_CHANNEL_ID = "loomi_calls"
    private const val CALL_CHANNEL_NAME = "Loomi Calls"
    const val KEY_TEXT_REPLY = "key_text_reply"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Channel for messages (High Importance)
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new messages in Loomi"
            }
            manager.createNotificationChannel(channel)

            // Channel for calls (Max Importance)
            val callChannel = NotificationChannel(
                CALL_CHANNEL_ID,
                CALL_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming calls"
                setSound(null, null) // Handled by activity or custom sound
                enableVibration(true)
            }
            manager.createNotificationChannel(callChannel)
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

        CoroutineScope(Dispatchers.IO).launch {
            val largeIcon = getLargeIcon(context, senderImage)

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

            // Custom Notification Layout
            val customLayout = RemoteViews(context.packageName, R.layout.notification_custom).apply {
                setTextViewText(R.id.notification_title, senderName)
                setTextViewText(R.id.notification_message, messageText)
                
                if (largeIcon != null) {
                    setImageViewBitmap(R.id.notification_profile_image, largeIcon)
                }
                setImageViewResource(R.id.notification_app_icon, R.drawable.logo)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(senderName)
                .setContentText(messageText)
                .setCustomContentView(customLayout)
                .setCustomBigContentView(customLayout)
                .setCustomHeadsUpContentView(customLayout)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX) // Max priority for force push
                .setDefaults(NotificationCompat.DEFAULT_ALL) // Vibration and sound
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, false) // High-priority heads-up
                .setContentIntent(pendingIntent)
                .addAction(replyAction)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(notificationId, notification)
        }
    }

    fun showCallNotification(
        context: Context,
        callerId: String,
        callerName: String,
        callerImage: String
    ) {
        val notificationId = 1001 // Fixed ID for calls

        // Intent to open CallActivity
        val intent = Intent(context, CallActivity::class.java).apply {
            putExtra("receiverUid", callerId)
            putExtra("receiverName", callerName)
            putExtra("receiverImage", callerImage)
            putExtra("isIncoming", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.call)
            .setContentTitle("Incoming Call")
            .setContentText("$callerName is calling you...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    private suspend fun getLargeIcon(context: Context, senderImage: String): Bitmap? {
        return try {
            if (senderImage.startsWith("http")) {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(senderImage)
                    .allowHardware(false) // Required for bitmaps
                    .build()
                val result = (loader.execute(request) as? SuccessResult)?.drawable
                (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
            } else {
                // Load from assets
                val inputStream: InputStream = context.assets.open(senderImage)
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }
}
