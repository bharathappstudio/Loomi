package com.echo.loomi

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MessageListenerService : Service() {

    private val database = FirebaseDatabase.getInstance("https://echo-loomi-app-default-rtdb.firebaseio.com/").reference
    private val listeners = mutableMapOf<String, ValueEventListener>()
    private var usersListener: ValueEventListener? = null
    private var callsListener: ValueEventListener? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationHelper.createNotificationChannel(this)
        
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startListening()
        listenForCalls()
        return START_STICKY
    }

    private fun listenForCalls() {
        val auth = FirebaseAuth.getInstance()
        val myUid = auth.currentUser?.uid ?: return

        if (callsListener != null) return

        callsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val callData = snapshot.getValue(CallData::class.java)
                if (callData != null && callData.status == "ringing") {
                    val decryptedName = EncryptionUtils.decrypt(callData.callerName)
                    val decryptedImage = EncryptionUtils.decrypt(callData.callerImage)
                    NotificationHelper.showCallNotification(
                        this@MessageListenerService,
                        callData.callerId,
                        decryptedName,
                        decryptedImage
                    )
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        database.child("calls").child(myUid).addValueEventListener(callsListener!!)
    }

    private fun startListening() {
        val auth = FirebaseAuth.getInstance()
        val myUid = auth.currentUser?.uid ?: return

        if (usersListener != null) return

        usersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (userSnapshot in snapshot.children) {
                    val otherUid = userSnapshot.child("uid").getValue(String::class.java) ?: continue
                    if (otherUid == myUid) continue
                    
                    val otherName = userSnapshot.child("name").getValue(String::class.java) ?: "Unknown"
                    val otherImage = userSnapshot.child("imageName").getValue(String::class.java) ?: ""
                    val chatId = if (myUid < otherUid) "${myUid}_$otherUid" else "${otherUid}_$myUid"
                    
                    if (!listeners.containsKey(chatId)) {
                        val chatRef = database.child("chats").child(chatId)
                        chatRef.keepSynced(true)

                        val listener = object : ValueEventListener {
                            private var firstLoad = true
                            override fun onDataChange(chatSnapshot: DataSnapshot) {
                                if (chatSnapshot.exists()) {
                                    val lastMsgObj = chatSnapshot.children.lastOrNull()?.getValue(ChatMessage::class.java)
                                    if (lastMsgObj != null && lastMsgObj.senderId != myUid) {
                                        val now = System.currentTimeMillis()
                                        val isRecent = (now - lastMsgObj.timestamp) < 30000
                                        if (!firstLoad || isRecent) {
                                            NotificationHelper.showMessageNotification(
                                                this@MessageListenerService,
                                                otherUid,
                                                otherName,
                                                otherImage,
                                                lastMsgObj.message,
                                                chatId
                                            )
                                        }
                                    }
                                }
                                firstLoad = false
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        }
                        chatRef.limitToLast(1).addValueEventListener(listener)
                        listeners[chatId] = listener
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        database.child("users").addValueEventListener(usersListener!!)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Attempt to restart the service if the app is swiped away
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        startService(restartServiceIntent)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        usersListener?.let { database.child("users").removeEventListener(it) }
        callsListener?.let { 
            val myUid = FirebaseAuth.getInstance().currentUser?.uid
            if (myUid != null) database.child("calls").child(myUid).removeEventListener(it)
        }
        listeners.forEach { (chatId, listener) ->
            database.child("chats").child(chatId).removeEventListener(listener)
        }
        super.onDestroy()
    }
}
